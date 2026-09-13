package re.pinok.data

import android.os.Build
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Волна 45-д #SETTINGS-CRYPTO: шифрование файла экспорта настроек
 * (запрос пользователя: тумблер в настройках включает шифрование экспорта,
 * а импорт ВСЕГДА умеет оба формата — зашифрованный файл по заголовку
 * сам открывает окно ввода кода, тумблер роли не играет).
 *
 * ЗАЧЕМ (угроза): экспорт-файл уходит через SAF в пользовательскую папку
 * (Downloads и т.п.) и содержит ПОЛНЫЙ контейнер настроек + расшифрованную
 * сессию (access_token). Без шифрования любой, кто получит файл (чужой ПК,
 * USB/MTP, синхронизация папки с облаком), получает вход в аккаунт.
 *
 * ПОЧЕМУ КОД НЕ ХРАНИТСЯ НИГДЕ: цель шифрования — защитить файл ПОСЛЕ того,
 * как он покинул контейнер. Ключ Android Keystore умирает вместе с
 * приложением (uninstall/clear data — весь сценарий отката), поэтому
 * шифровать ключом контейнера бессмысленно: расшифровать после переустановки
 * было бы нечем. Единственный ключ, который переживает переустановку —
 * код, который помнит человек. Код вводится в диалоге при КАЖДОМ экспорте
 * (тумблер включён) и при импорте зашифрованного файла; в контейнере его нет.
 *
 * ФОРМАТ КОНВЕРТА (format = 2 — УРОВЕНЬ КОНВЕРТА; payload внутри всегда
 * format = 1, см. SovaPrefsBackup.FORMAT_VERSION):
 * {
 *   "format": 2,                     // версия конверта (не payload!)
 *   "cipher": "AES-GCM",             // AES-256-GCM (тег включён в data)
 *   "kdf": "PBKDF2WithHmacSHA256",   // алгоритм выведения ключа из кода
 *   "iter": 200000,                  // итерации PBKDF2
 *   "salt": "<base64 16Б>",          // соль PBKDF2 (случайная на экспорт)
 *   "iv": "<base64 12Б>",            // IV GCM (случайный на экспорт)
 *   "data": "<base64 ct+tag>"        // AES-GCM(UTF-8(payload format=1))
 * }
 * Алгоритм KDF читается из ФАЙЛА, а не выбирается по версии устройства —
 * файл, зашифрованный на Android 8+, расшифровывается на любой установке,
 * где алгоритм доступен, и наоборот.
 *
 * ВЫБОР KDF (minSdk 24, а PBKDF2WithHmacSHA256 в платформе с API 26):
 *  - API 26+ → PBKDF2WithHmacSHA256, 200000 итераций (OWASP-класс для SHA-256);
 *  - API 24/25 → PBKDF2WithHmacSHA1, 300000 итераций (PBKDF2-HMAC-SHA1
 *    остаётся криптостойким; итераций больше, т.к. раунд SHA-1 дешевле).
 * АЛГОРИТМ AES-GCM доступен с API 19 — гейт не нужен.
 *
 * НАДЁЖНОСТЬ ОТ НЕВЕРНОГО КОДА: GCM-тег — аутентифицированное шифрование;
 * неверный код (или битый файл) даёт AEADBadTagException на doFinal →
 * честный отказ «Неверный код или файл повреждён», без частичных данных.
 *
 * ГРАНИЦЫ ЧЕСТНО: код живёт в JVM как String/CharArray (как и access_token
 * в других частях приложения) — от дампинга памяти дебаггером защиты нет;
 * логирование кода запрещено. Страховочная копия (SAFETY_FILE_NAME) НЕ
 * шифруется сознательно: она не покидает приватный контейнер и должна
 * восстанавливаться без трения (точка возврата на живой установке).
 */
object SovaPrefsCrypto {

    /** Версия формата КОНВЕРТА. Payload внутри конверта всегда format = 1. */
    const val ENVELOPE_FORMAT = 2

    private const val CIPHER_NAME = "AES-GCM"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF_SHA256 = "PBKDF2WithHmacSHA256"
    private const val KDF_SHA1 = "PBKDF2WithHmacSHA1"
    // Итерации: SHA-256 — 200k (OWASP-класс); SHA-1 (фолбэк API 24/25) — 300k
    // (раунд дешевле → компенсируем числом; ~1 сек на средних телефонах).
    private const val ITER_SHA256 = 200_000
    private const val ITER_SHA1 = 300_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val SALT_BYTES = 16

    /** Честная ошибка расшифровки (неверный код / битый файл / неизвестный KDF). */
    class DecryptionException(message: String) : Exception(message)

    /**
     * Быстрый тест «это зашифрованный конверт формата 2?». Лёгкий Gson-парс
     * (файлы экспорта < 1 МБ — дёшево). Битый/не-JSON/формат 1 → false:
     * вызывающий идёт обычным путём parse(). Используется UI при импорте,
     * чтобы РЕШИТЬ, показывать ли окно ввода кода.
     */
    fun isEncrypted(text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            // JsonElement + as? — тот же детерминизм, что в SovaPrefsBackup.parse
            // (урок 45-г): встроенный адаптер Gson, никаких маскарадингов типов.
            val root = Gson().fromJson(text, JsonElement::class.java) as? JsonObject
                ?: return false
            val format = root.get("format")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            format == ENVELOPE_FORMAT && root.get("cipher")?.isJsonPrimitive == true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Шифрование payload (JSON формата 1 из SovaPrefsBackup.export) → текст
     * конверта формата 2. Соль и IV генерируются заново на каждый вызов
     * (SecureRandom) — два экспорта с одним кодом дают разные файлы.
     * KDF выбирается по версии Android УСТРОЙСТВА (см. KDoc класса).
     *
     * @throws IllegalArgumentException пустой код (UI обязан проверять раньше).
     * @throws IllegalStateException   KDF недоступен на этой версии Android.
     */
    fun encrypt(plainJson: String, code: CharArray): String {
        require(code.isNotEmpty()) { "Код шифрования пуст" }
        val kdf: String
        val iter: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            kdf = KDF_SHA256
            iter = ITER_SHA256
        } else {
            kdf = KDF_SHA1
            iter = ITER_SHA1
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(code, salt, iter, kdf), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        val root = JsonObject()
        root.addProperty("format", ENVELOPE_FORMAT)
        root.addProperty("cipher", CIPHER_NAME)
        root.addProperty("kdf", kdf)
        root.addProperty("iter", iter)
        root.addProperty("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        root.addProperty("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        root.addProperty("data", Base64.encodeToString(ct, Base64.NO_WRAP))
        return Gson().toJson(root)
    }

    /**
     * Расшифровка конверта формата 2 → payload (JSON формата 1) для parse().
     * KDF и итерации читаются из ФАЙЛА (не по версии устройства): файл с
     * нового телефона расшифровывается на старом, если алгоритм доступен.
     *
     * @throws DecryptionException неверный код (AEADBadTag), битый конверт,
     *                             неизвестный cipher/KDF, KDF недоступен
     *                             на этой версии Android.
     */
    fun decrypt(envelopeText: String, code: CharArray): String {
        require(code.isNotEmpty()) { "Код расшифровки пуст" }
        val root = try {
            Gson().fromJson(envelopeText, JsonElement::class.java) as? JsonObject
        } catch (t: Throwable) {
            throw DecryptionException("Конверт шифрования повреждён: " + t.javaClass.simpleName)
        } ?: throw DecryptionException("Конверт шифрования повреждён")
        if (root.get("format")?.takeIf { it.isJsonPrimitive }?.asInt != ENVELOPE_FORMAT ||
            root.get("cipher")?.isJsonPrimitive != true
        ) {
            throw DecryptionException("Это не зашифрованный файл настроек (неизвестный конверт)")
        }
        val cipherName = root.get("cipher")?.asString.orEmpty()
        if (cipherName != CIPHER_NAME) {
            throw DecryptionException("Неизвестный алгоритм шифрования: " + cipherName + " (файл из более новой версии)")
        }
        val kdf = root.get("kdf")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (kdf != KDF_SHA256 && kdf != KDF_SHA1) {
            throw DecryptionException("Неизвестный алгоритм ключа: " + kdf + " (файл из более новой версии)")
        }
        val iter = root.get("iter")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (iter < 1) throw DecryptionException("Конверт шифрования повреждён (итерации)")
        val salt = decodeField(root, "salt")
        val iv = decodeField(root, "iv")
        val ct = decodeField(root, "data")
        if (salt == null || iv == null || ct == null) {
            throw DecryptionException("Конверт шифрования повреждён (поля salt/iv/data)")
        }
        val key = try {
            deriveKey(code, salt, iter, kdf)
        } catch (t: java.security.NoSuchAlgorithmException) {
            // Файл зашифрован KDF, которого нет на этом устройстве (например
            // SHA-256 конверт с нового телефона попал на Android 7).
            throw DecryptionException("Алгоритм ключа " + kdf + " недоступен на этой версии Android")
        }
        val cipher = try {
            Cipher.getInstance(CIPHER_TRANSFORMATION)
        } catch (t: Throwable) {
            throw DecryptionException("AES-GCM недоступен на этом устройстве")
        }
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = try {
            cipher.doFinal(ct)
        } catch (t: Exception) {
            // AEADBadTagException и родственные: неверный код или битый файл.
            throw DecryptionException("Неверный код или файл повреждён")
        }
        return String(plain, Charsets.UTF_8)
    }

    /** PBKDF2 → AES-ключ 256 бит. Ключевые байты обнуляются после использования. */
    private fun deriveKey(code: CharArray, salt: ByteArray, iter: Int, kdf: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(kdf)
        val spec = PBEKeySpec(code, salt, iter, KEY_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        try {
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            java.util.Arrays.fill(keyBytes, 0)
        }
    }

    /** Чтение base64-поля конверта (null = поле отсутствует/не base64). */
    private fun decodeField(root: JsonObject, name: String): ByteArray? {
        val raw = root.get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        return try {
            Base64.decode(raw, Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
    }
}
