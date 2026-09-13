package re.pinok.updater

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Волна 46 #UPDATER-SIGNING (P0 внешнего ревью): корень доверия апдейтера.
 *
 * ЗАЧЕМ: манифест обновлений (version.json) содержит apkUrl и sha256 в ОДНОМ
 * доверяемом файле. До этой волны любой, кто контролирует канал доставки
 * (компрометация репозитория, кастомный UPDATER-SOURCE, MITM поверх https),
 * мог подменить манифест → updater установил бы произвольный APK с правами
 * приложения. Подпись разрывает эту связку: манифест действует ТОЛЬКО если
 * подписан приватным ключом релиз-менеджера, чья ПУБЛИЧНАЯ половина зашита
 * в APK. Ключ в APK нельзя подменить без пересборки — это и есть якорь.
 *
 * СХЕМА:
 *  - приватный ключ (32 байта seed, hex) — только у релиз-менеджера, ВНЕ git;
 *  - публичный ключ (base64, 32 байта) — константа RELEASE_PUBLIC_KEY_B64 ниже;
 *  - version.json.sig — base64 detached-подписи ed25519 ровно над БАЙТАМИ
 *    version.json (без канонизации JSON: подпись и проверка оперируют одним
 *    и тем же массивом байтов — нечему разъезжаться);
 *  - инструмент подписи: tools/updater-sign/sign_manifest.py (stdlib-only,
 *    RFC 8032 reference с selftest по тест-векторам).
 *
 * ПРАВИЛО FAIL-CLOSED: verify() возвращает false при ЛЮБОЙ проблеме (ключ не
 * задан, подпись не распарсилась, длина != 64, не совпала) — UpdaterManager
 * трактует это как SignatureRejected и НЕ доводит манифест до UI/установки.
 * Исключение согласовано с ревьюером: ПУСТОЙ version.json подписи не требует
 * (устанавливать нечего — подделывать нечего), это #UPDATER-EMPTY-CONFIG.
 *
 * РОТАЦИЯ КЛЮЧА: новый ключ = новая сборка APK с новым RELEASE_PUBLIC_KEY_B64.
 * Старые установки после ротации честно отклоняют манифесты старым ключом —
 * критичное обновление при ротации доставляется ВРУЧНУЮ (сторонняя ссылка).
 *
 * NULL-ЯВНО: запреты файла UpdaterManager (без !!, ?., ?:) соблюдаются и здесь.
 */
object UpdaterSigning {

    /**
     * Публичный ключ ed25519 релиз-менеджера манифеста (base64, 32 байта).
     * Сгенерирован tools/updater-sign/sign_manifest.py keygen (волна 46);
     * отпечаток ключа (sha256 от 32 байт pubkey, hex):
     * 22520fde561cf545efc7a2d0700b18db1b5cd1b37f520063e603471596489748.
     * Замена значения = ротация ключа (требует пересборки и доставки APK вручную).
     */
    const val RELEASE_PUBLIC_KEY_B64 = "XvaSZqNnskbup2Obuj+XwN7q2pqHJKpYUsrtbde8wEI="

    /** Ключ встроен в сборку? (пустая константа = updater всегда fail-closed). */
    fun isConfigured(): Boolean {
        return RELEASE_PUBLIC_KEY_B64.trim().isNotEmpty()
    }

    /**
     * Проверка detached-подписи над точными байтами манифеста.
     *
     * @param manifestBytes ровно те байты, что отдал сервер (response.body.bytes())
     * @param signatureB64 содержимое version.json.sig (base64 от 64 байтов подписи)
     * @return true ТОЛЬКО при валидной подписи вшитым ключом; иначе false
     */
    fun verify(manifestBytes: ByteArray, signatureB64: String): Boolean {
        val keyB64 = RELEASE_PUBLIC_KEY_B64.trim()
        if (keyB64.isEmpty()) return false
        if (manifestBytes.isEmpty()) return false
        val signature: ByteArray = try {
            Base64.decode(signatureB64.trim(), Base64.DEFAULT)
        } catch (t: Throwable) {
            return false
        }
        if (signature.size != 64) return false
        return try {
            val keyBytes = Base64.decode(keyB64, Base64.DEFAULT)
            if (keyBytes.size != 32) {
                false
            } else {
                // Легковесный API BouncyCastle БЕЗ регистрации провайдера:
                // bcprov-jdk15to18 нужен, т.к. java.security знает Ed25519
                // только с API 33, а minSdk проекта = 24.
                val verifier = Ed25519Signer()
                verifier.init(false, Ed25519PublicKeyParameters(keyBytes, 0))
                verifier.update(manifestBytes, 0, manifestBytes.size)
                verifier.verifySignature(signature)
            }
        } catch (t: Throwable) {
            false
        }
    }
}
