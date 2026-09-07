package re.pinok.ui.screens.profile

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════════
// П-3 (#PROFILE-SNAP): редактор профиля — этап П-3 плана
// «профиль.снапшоты.инвентарь.md» §5 п.4.
//
// Что умеет (всё реально работает, no-stub):
//  1. Префилл: account.getProfileInfo (accountGetProfileInfo) — терпеливый
//     парсинг JsonObject (?.get + takeIf !isJsonNull).
//  2. account.saveProfileInfo (accountSaveProfileInfo): имена, пол (чипы 1/2),
//     дата рождения (текст ДД.ММ.ГГГГ), родной город (текст — VK принимает
//     home_town строкой), семейное положение (relation 0..8, диалог), статус
//     и «короткая информация» (activities/interests/music/movies/tv/books/
//     games). Отправляются ТОЛЬКО изменённые относительно префилла поля
//     (dirty-diff) — неизменённое не трогаем.
//  3. Аватар: photos.getProfileUploadServer → multipart POST (транспорт
//     photosUploadWallPhoto — поле "photo", ответ {server, photo, hash})
//     → photos.save(album_id = -6).
//  4. Обложка: photos.getOwnerCoverPhotoUploadServer(upload_v2=1) → multipart
//     POST полем "file" (локальный OkHttp-транспорт — VKA-транспорт шлёт поле
//     "photo", а cover-сервер принимает "file", как в бандле pageProfile;
//     VKApiClient по правилам этапа не правим) → сырой JSON ответа →
//     photos.saveOwnerCoverPhoto(response_json=..., upload_v2=1) — v2-форма
//     saveCover/uploadCoverToServer. Удаление: photos.removeOwnerCoverPhoto.
//
// Честные отклонения (проверено rg 2026-09-07; «Город» обновлён П-7-C):
//  - «Город» (city): П-7-C — редактируемый: тап открывает диалог поиска
//    EditCitySearchDialog (debounce 350мс → VKA databaseGetCities =
//    database.getCities), выбор даёт cityId+title → dirty-diff шлёт cityId
//    в account.saveProfileInfo ТОЛЬКО при реальной смене города. Отклонения:
//    (а) prefill-ID: account.getProfileInfo отдаёт city как {id,title} — ID
//    читается editProfileNestedId; если сервер id не отдал (гвард) —
//    dirty-база null, смена через поиск всё равно уходит корректным ID;
//    (б) country_id поиска — country.id из префилла, иначе 1 (Россия,
//    серверный дефолт VK); (в) «очистить город» не предлагается — поведение
//    city=0/пустого city в account.saveProfileInfo VK не документировано
//    (no-stub: не имитируем).
//  - «О себе» (about) и «Сайт» (site) показываются read-only: сигнатура
//    accountSaveProfileInfo этих параметров не содержит (расширение метода
//    правилами этапа запрещено, overload нет). Подсказка: веб-версия VK.
//  - crop-параметры обложки не задаются (опциональны) — VK кадрирует сам.
//  - «Родной город» (home_town) — ОТДЕЛЬНОЕ строковое поле, не путать с
//    city (ID справочника): механика homeTown П-3 не изменена П-7-C.
//  - bdate — текстовый ввод без календаря (без внешних библиотек, как задано).
//
// Обновление профиля после сохранения: onBack() → popBackStack → ProfileScreen
// (маршрут profile) проходит свежую загрузку (LaunchedEffect(Unit) →
// usersGetFullExtended) — существующий механизм перезагрузки П-1, отдельный
// reload-канал не нужен.
// ═══════════════════════════════════════════════════════════════════════════

private const val EDIT_TAG = "EditProfileScreen"

/** Снимок префилла — база dirty-diff (отправляем только изменённые поля). */
private data class EditProfileSnapshot(
    val firstName: String,
    val lastName: String,
    val bdate: String,
    val homeTown: String,
    val status: String,
    val activities: String,
    val interests: String,
    val music: String,
    val movies: String,
    val tv: String,
    val books: String,
    val games: String,
    val sex: Int,
    val relation: Int,
    // П-7-C: город как ID (city.id из account.getProfileInfo); null = не
    // задан или сервер не отдал id. Сравнение с selectedCityId даёт
    // dirty-diff по городу (cityId уходит в save только при реальной смене).
    val cityId: Int?,
)

/** Значения relation (семейное положение) по account.saveProfileInfo. */
private val RELATION_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "Не указано",
    1 to "Не женат / не замужем",
    2 to "Есть друг / есть подруга",
    3 to "Помолвлен(а)",
    4 to "Женат / замужем",
    5 to "Всё сложно",
    6 to "В активном поиске",
    7 to "Влюблён(а)",
    // 8 = «в гражданском браке» — реальное значение VK (вне списка задания):
    // добавлено, чтобы префилл relation=8 не отображался как «Не указано».
    8 to "В гражданском браке",
)

/** Терпеливое чтение строки из JsonObject (null для отсутствия/JSON-null/не-строки). */
private fun editProfileStr(obj: JsonObject, key: String): String? {
    val el = obj.get(key) ?: return null
    if (el.isJsonNull || !el.isJsonPrimitive) return null
    return el.asString
}

/** Терпеливое чтение Int из JsonObject (null для отсутствия/JSON-null/не-числа). */
private fun editProfileInt(obj: JsonObject, key: String): Int? {
    val el = obj.get(key) ?: return null
    if (el.isJsonNull || !el.isJsonPrimitive) return null
    return try {
        el.asInt
    } catch (_: Exception) {
        null
    }
}

/** title вложенного объекта {id, title} (city/country из getProfileInfo). */
private fun editProfileNestedTitle(obj: JsonObject, key: String): String? {
    val el = obj.get(key) ?: return null
    if (!el.isJsonObject) return null
    return editProfileStr(el.asJsonObject, "title")
}

/** id вложенного объекта {id, title} (П-7-C: city/country из getProfileInfo). */
private fun editProfileNestedId(obj: JsonObject, key: String): Int? {
    val el = obj.get(key) ?: return null
    if (!el.isJsonObject) return null
    return editProfileInt(el.asJsonObject, "id")
}

/**
 * Multipart-загрузка файла обложки на upload_url. Cover-сервер принимает файл
 * в поле "file" (fieldName бандла pageProfile, KDoc photosSaveOwnerCoverPhoto),
 * поэтому транспорт отдельный от photosUploadWallPhoto (поле "photo") — VKA
 * не правим. Возвращает СЫРОЙ JSON-ответ сервера (идёт в response_json
 * v2-формы photos.saveOwnerCoverPhoto) или null.
 */
private suspend fun uploadCoverMultipart(
    context: Context,
    uploadUrl: String,
    uri: Uri,
): String? {
    return withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val requestBody = bytes.toRequestBody(mime.toMediaType())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "cover.jpg", requestBody)
                .build()
            val request = Request.Builder().url(uploadUrl).post(multipart).build()
            // Свежий OkHttpClient: VKA-клиент приватный, upload-серверу (pu.vk.com)
            // заголовки авторизации не нужны (как и photosUploadWallPhoto).
            OkHttpClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLog.e(EDIT_TAG, "cover upload HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    AppLog.e(EDIT_TAG, "cover upload: empty body")
                    return@use null
                }
                body
            }
        } catch (e: Exception) {
            AppLog.e(EDIT_TAG, "cover upload failed", e)
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Префилл ──
    var loading by remember { mutableStateOf(true) }
    var prefillError by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }
    var snapshot by remember { mutableStateOf<EditProfileSnapshot?>(null) }
    // bdate_visibility из ответа (передаём обратно при смене bdate).
    var bdateVisibility by remember { mutableStateOf<Int?>(null) }
    var cityTitle by remember { mutableStateOf<String?>(null) }
    var countryTitle by remember { mutableStateOf<String?>(null) }
    // П-7-C: город как ID (account.saveProfileInfo city=<id>). selected* —
    // текущий выбор юзера (стартует с префилла), searchCountryId — страна
    // для database.getCities (country.id префилла, иначе 1 = Россия —
    // серверный дефолт VK; отклонение задокументировано в KDoc диалога).
    var selectedCityId by remember { mutableStateOf<Int?>(null) }
    var selectedCityTitle by remember { mutableStateOf<String?>(null) }
    var searchCountryId by remember { mutableStateOf(1) }
    // Обложка уже установлена (users.get field=cover → usersGetFullExtended).
    var coverExists by remember { mutableStateOf(false) }

    // ── Поля формы ──
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var bdate by remember { mutableStateOf("") }
    var homeTown by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var activities by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var music by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf("") }
    var tv by remember { mutableStateOf("") }
    var books by remember { mutableStateOf("") }
    var games by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(0) }
    var relation by remember { mutableStateOf(0) }

    // ── Сохранение / загрузки ──
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var avatarBusy by remember { mutableStateOf(false) }
    var coverBusy by remember { mutableStateOf(false) }
    var showRelationDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }

    fun toastMsg(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Аватар: getProfileUploadServer → photosUploadWallPhoto (поле "photo",
    // ответ {server, photo, hash}) → photos.save(album_id=-6). Успех →
    // Toast + возврат на профиль (перезагрузит usersGetFullExtended).
    fun changeAvatar(uri: Uri) {
        if (avatarBusy) return
        avatarBusy = true
        scope.launch {
            try {
                val uploadUrl = app.apiClient.photosGetProfileUploadServer()
                if (uploadUrl.isNullOrBlank()) {
                    toastMsg("Не удалось получить адрес загрузки фото")
                    return@launch
                }
                val uploaded = app.apiClient.photosUploadWallPhoto(uploadUrl, uri)
                if (uploaded == null) {
                    toastMsg(app.apiClient.lastApiError ?: "Не удалось загрузить фото")
                    return@launch
                }
                val saved = app.apiClient.photosSave(
                    albumId = -6,
                    server = uploaded.server,
                    photos = uploaded.photo,
                    hash = uploaded.hash,
                )
                if (saved.isEmpty()) {
                    toastMsg(app.apiClient.lastApiError ?: "Не удалось сохранить фото профиля")
                } else {
                    AppLog.i(EDIT_TAG, "avatar updated (${saved.size} photo object(s))")
                    toastMsg("Фото профиля обновлено")
                    onBack()
                }
            } catch (e: Exception) {
                AppLog.e(EDIT_TAG, "changeAvatar failed", e)
                toastMsg("Ошибка: ${e.message}")
            } finally {
                avatarBusy = false
            }
        }
    }

    // Обложка: getOwnerCoverPhotoUploadServer(upload_v2) → multipart "file" →
    // saveOwnerCoverPhoto(response_json=raw, upload_v2) — v2-форма веба.
    fun changeCover(uri: Uri) {
        if (coverBusy) return
        coverBusy = true
        scope.launch {
            try {
                val uploadUrl = app.apiClient.photosGetOwnerCoverPhotoUploadServer(uploadV2 = true)
                if (uploadUrl.isNullOrBlank()) {
                    toastMsg("Не удалось получить адрес загрузки обложки")
                    return@launch
                }
                val rawResponse = uploadCoverMultipart(context, uploadUrl, uri)
                if (rawResponse == null) {
                    toastMsg("Не удалось загрузить обложку")
                    return@launch
                }
                val ok = app.apiClient.photosSaveOwnerCoverPhoto(
                    responseJson = rawResponse,
                    uploadV2 = true,
                )
                if (ok) {
                    AppLog.i(EDIT_TAG, "cover updated (v2 form)")
                    toastMsg("Обложка обновлена")
                    onBack()
                } else {
                    toastMsg(app.apiClient.lastApiError ?: "Не удалось сохранить обложку")
                }
            } catch (e: Exception) {
                AppLog.e(EDIT_TAG, "changeCover failed", e)
                toastMsg("Ошибка: ${e.message}")
            } finally {
                coverBusy = false
            }
        }
    }

    fun removeCover() {
        if (coverBusy) return
        coverBusy = true
        scope.launch {
            try {
                val ok = app.apiClient.photosRemoveOwnerCoverPhoto()
                if (ok) {
                    AppLog.i(EDIT_TAG, "cover removed")
                    toastMsg("Обложка удалена")
                    onBack()
                } else {
                    toastMsg(app.apiClient.lastApiError ?: "Не удалось удалить обложку")
                }
            } catch (e: Exception) {
                AppLog.e(EDIT_TAG, "removeCover failed", e)
                toastMsg("Ошибка: ${e.message}")
            } finally {
                coverBusy = false
            }
        }
    }

    fun save() {
        val snap = snapshot ?: return
        if (saving || avatarBusy || coverBusy) return
        if (firstName.trim().isEmpty() || lastName.trim().isEmpty()) {
            saveError = "Имя и фамилия не могут быть пустыми"
            return
        }
        saving = true
        saveError = null
        scope.launch {
            try {
                val bdateChanged = bdate.trim() != snap.bdate
                val resp = app.apiClient.accountSaveProfileInfo(
                    firstName = firstName.trim().takeIf { it != snap.firstName },
                    lastName = lastName.trim().takeIf { it != snap.lastName },
                    bdate = bdate.trim().takeIf { bdateChanged },
                    bdateVisibility = bdateVisibility.takeIf { bdateChanged },
                    homeTown = homeTown.trim().takeIf { it != snap.homeTown },
                    relation = relation.takeIf { it != snap.relation },
                    status = status.trim().takeIf { it != snap.status },
                    // П-7-C: city уходит ТОЛЬКО при реальной смене города
                    // (выбор через databaseGetCities; см. EditCitySearchDialog).
                    cityId = selectedCityId.takeIf { it != snap.cityId },
                    sex = sex.takeIf { it != snap.sex },
                    activities = activities.trim().takeIf { it != snap.activities },
                    interests = interests.trim().takeIf { it != snap.interests },
                    music = music.trim().takeIf { it != snap.music },
                    movies = movies.trim().takeIf { it != snap.movies },
                    tv = tv.trim().takeIf { it != snap.tv },
                    books = books.trim().takeIf { it != snap.books },
                    games = games.trim().takeIf { it != snap.games },
                )
                val changed = resp
                    ?.get("changed")
                    ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                    ?.asString == "1"
                if (changed) {
                    AppLog.i(EDIT_TAG, "saveProfileInfo: changed=1")
                    toastMsg("Сохранено")
                    onBack()
                } else {
                    // changed отсутствует/0, либо captcha не была решена в call().
                    val msg = app.apiClient.lastApiError ?: "Не удалось сохранить профиль"
                    AppLog.w(EDIT_TAG, "saveProfileInfo: changed!=1 (resp=$resp)")
                    saveError = msg
                    toastMsg(msg)
                }
            } catch (e: Exception) {
                AppLog.e(EDIT_TAG, "saveProfileInfo failed", e)
                val msg = "Ошибка: ${e.message}"
                saveError = msg
                toastMsg(msg)
            } finally {
                saving = false
            }
        }
    }

    // Префилл: account.getProfileInfo (+ users.get(field=cover) через
    // usersGetFullExtended — тот же путь, что рендерит ProfileScreen после П-1;
    // падение usersGet не блокирует форму, лишь прячет «Удалить обложку»).
    LaunchedEffect(retryTick) {
        loading = true
        prefillError = null
        try {
            val info = app.apiClient.accountGetProfileInfo()
            if (info == null) {
                prefillError = app.apiClient.lastApiError ?: "Не удалось загрузить данные профиля"
                return@LaunchedEffect
            }
            firstName = (editProfileStr(info, "first_name") ?: "").take(40)
            lastName = (editProfileStr(info, "last_name") ?: "").take(40)
            bdate = editProfileStr(info, "bdate") ?: ""
            bdateVisibility = editProfileInt(info, "bdate_visibility")
            homeTown = (editProfileStr(info, "home_town") ?: "").take(100)
            status = (editProfileStr(info, "status") ?: "").take(140)
            activities = (editProfileStr(info, "activities") ?: "").take(500)
            interests = (editProfileStr(info, "interests") ?: "").take(500)
            music = (editProfileStr(info, "music") ?: "").take(500)
            movies = (editProfileStr(info, "movies") ?: "").take(500)
            tv = (editProfileStr(info, "tv") ?: "").take(500)
            books = (editProfileStr(info, "books") ?: "").take(500)
            games = (editProfileStr(info, "games") ?: "").take(500)
            sex = editProfileInt(info, "sex") ?: 0
            relation = editProfileInt(info, "relation") ?: 0
            cityTitle = editProfileNestedTitle(info, "city")
            countryTitle = editProfileNestedTitle(info, "country")
            // П-7-C: city/country как ID (база dirty-diff и страна поиска).
            selectedCityId = editProfileNestedId(info, "city")
            selectedCityTitle = null
            searchCountryId = editProfileNestedId(info, "country") ?: 1
            snapshot = EditProfileSnapshot(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                bdate = bdate.trim(),
                homeTown = homeTown.trim(),
                status = status.trim(),
                activities = activities.trim(),
                interests = interests.trim(),
                music = music.trim(),
                movies = movies.trim(),
                tv = tv.trim(),
                books = books.trim(),
                games = games.trim(),
                sex = sex,
                relation = relation,
                cityId = selectedCityId,
            )
            AppLog.i(EDIT_TAG, "prefill loaded")
        } catch (e: Exception) {
            AppLog.e(EDIT_TAG, "prefill failed", e)
            prefillError = "Ошибка: ${e.message}"
        } finally {
            loading = false
        }
        // Отдельный try: обложка — необязательный добор после основного префилла.
        try {
            val prof = app.apiClient.usersGetFullExtended(null)
            coverExists = prof?.cover?.enabled == true
        } catch (e: Exception) {
            AppLog.e(EDIT_TAG, "cover state load failed", e)
        }
    }

    // Пикеры изображений: ActivityResultContracts.GetContent("image/*") —
    // системный выбор документа (в :app PickVisualMedia используется для
    // вложений постов; GetContent выбран по заданию этапа П-3).
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) changeAvatar(uri)
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) changeCover(uri)
    }

    val snapNow = snapshot
    val dirty = snapNow != null && (
        firstName.trim() != snapNow.firstName ||
            lastName.trim() != snapNow.lastName ||
            bdate.trim() != snapNow.bdate ||
            homeTown.trim() != snapNow.homeTown ||
            status.trim() != snapNow.status ||
            activities.trim() != snapNow.activities ||
            interests.trim() != snapNow.interests ||
            music.trim() != snapNow.music ||
            movies.trim() != snapNow.movies ||
            tv.trim() != snapNow.tv ||
            books.trim() != snapNow.books ||
            games.trim() != snapNow.games ||
            sex != snapNow.sex ||
            relation != snapNow.relation ||
            selectedCityId != snapNow.cityId
        )
    val relationLabel = RELATION_OPTIONS
        .firstOrNull { it.first == relation }
        ?.second ?: "Не указано"
    // П-7-C: текст строки «Город» — выбранный через поиск title при смене,
    // иначе префилл (city.title + ", " + country.title), как было в П-3.
    val cityChanged = snapNow != null && selectedCityId != snapNow.cityId
    val cityRowText: String = if (cityChanged) {
        selectedCityTitle ?: ""
    } else {
        buildString {
            cityTitle?.let { append(it) }
            countryTitle?.let {
                if (isNotEmpty()) append(", ")
                append(it)
            }
        }
    }
    val currentSaveError = saveError

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Редактирование профиля") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = dirty && !saving) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            prefillError != null -> {
                // Локальный val: delegated-свойство не смарт-кастится в ветке when.
                val err = prefillError
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = err ?: "Не удалось загрузить данные профиля",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { retryTick++ },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("Повторить")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    item {
                        EditSectionHeader("Главное")
                    }
                    item {
                        EditTextFieldRow(
                            label = "Имя",
                            value = firstName,
                            onValueChange = { firstName = it.take(40) },
                            enabled = !saving,
                        )
                        EditTextFieldRow(
                            label = "Фамилия",
                            value = lastName,
                            onValueChange = { lastName = it.take(40) },
                            enabled = !saving,
                        )
                    }
                    item {
                        EditSexRow(sex = sex, enabled = !saving, onSelect = { sex = it })
                    }
                    item {
                        EditTextFieldRow(
                            label = "Дата рождения",
                            value = bdate,
                            onValueChange = { bdate = it.take(10) },
                            enabled = !saving,
                            supportingText = "Формат: ДД.ММ.ГГГГ или ДД.ММ (без года)",
                        )
                    }
                    item {
                        EditRelationRow(
                            label = relationLabel,
                            enabled = !saving,
                            onOpen = { showRelationDialog = true },
                        )
                    }
                    item {
                        EditTextFieldRow(
                            label = "Родной город",
                            value = homeTown,
                            onValueChange = { homeTown = it.take(100) },
                            enabled = !saving,
                        )
                    }
                    item {
                        // П-7-C: город редактируемый — тап открывает поиск
                        // (database.getCities); выбор пишет selectedCityId/Title.
                        EditCityRow(
                            cityText = cityRowText,
                            enabled = !saving,
                            onOpen = { showCityDialog = true },
                        )
                    }
                    item {
                        EditSectionHeader("Короткая информация")
                        EditTextFieldRow(
                            label = "Статус",
                            value = status,
                            onValueChange = { status = it.take(140) },
                            enabled = !saving,
                            supportingText = "До 140 символов",
                        )
                        EditTextFieldRow(
                            label = "Деятельность",
                            value = activities,
                            onValueChange = { activities = it.take(500) },
                            enabled = !saving,
                        )
                        EditTextFieldRow(
                            label = "Интересы",
                            value = interests,
                            onValueChange = { interests = it.take(500) },
                            enabled = !saving,
                        )
                        EditTextFieldRow(
                            label = "Любимая музыка",
                            value = music,
                            onValueChange = { music = it.take(500) },
                            enabled = !saving,
                        )
                        EditTextFieldRow(
                            label = "Любимые фильмы",
                            value = movies,
                            onValueChange = { movies = it.take(500) },
                            enabled = !saving,
                        )
                        EditTextFieldRow(
                            label = "Любимые телешоу",
                            value = tv,
                            onValueChange = { tv = it.take(500) },
                            enabled = !saving,
                        )
                        EditTextFieldRow(
                            label = "Любимые книги",
                            value = books,
                            onValueChange = { books = it.take(500) },
                            enabled = !saving,
                        )
                        EditTextFieldRow(
                            label = "Любимые игры",
                            value = games,
                            onValueChange = { games = it.take(500) },
                            enabled = !saving,
                        )
                    }
                    item {
                        EditSectionHeader("Информация недоступна для правки в приложении")
                        EditReadOnlyRow(
                            label = "О себе",
                            hint = "Изменяется на веб-версии VK",
                        )
                        EditReadOnlyRow(
                            label = "Сайт",
                            hint = "Изменяется на веб-версии VK",
                        )
                    }
                    item {
                        EditSectionHeader("Фото и обложка")
                        Button(
                            onClick = { avatarPicker.launch("image/*") },
                            enabled = !avatarBusy && !saving,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .heightIn(min = 44.dp),
                        ) {
                            if (avatarBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Изменить фото")
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { coverPicker.launch("image/*") },
                            enabled = !coverBusy && !saving,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .heightIn(min = 44.dp),
                        ) {
                            if (coverBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Изменить обложку")
                        }
                        // «Удалить обложку» — только когда обложка реально есть
                        // (cover.enabled из usersGetFullExtended; иначе кнопка скрыта).
                        if (coverExists) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { removeCover() },
                                enabled = !coverBusy && !saving,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    .heightIn(min = 44.dp),
                            ) {
                                Text(
                                    "Удалить обложку",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (currentSaveError != null) {
                        item {
                            Text(
                                text = currentSaveError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог выбора семейного положения (relation 0..8; partner_id опускаем —
    // сигнатура accountSaveProfileInfo его не принимает).
    if (showRelationDialog) {
        AlertDialog(
            onDismissRequest = { if (!saving) showRelationDialog = false },
            title = { Text("Семейное положение") },
            text = {
                Column {
                    RELATION_OPTIONS.forEach { (value, label) ->
                        TextButton(
                            onClick = {
                                relation = value
                                showRelationDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (value == relation) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRelationDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    // Диалог поиска города (П-7-C): database.getCities через VKA; выбор →
    // selectedCityId/selectedCityTitle (dirty-diff отправит cityId только
    // при реальной смене). Страна поиска — searchCountryId (префилл country.id
    // или дефолт 1, отклонение — KDoc EditCitySearchDialog).
    if (showCityDialog) {
        EditCitySearchDialog(
            countryId = searchCountryId,
            onDismiss = { showCityDialog = false },
            onSelect = { id, title ->
                selectedCityId = id
                selectedCityTitle = title
                showCityDialog = false
            },
        )
    }
}

/** Заголовок секции формы. */
@Composable
private fun EditSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Текстовое поле формы (OutlinedTextField, тач-таргет ≥ 48dp по высоте поля). */
@Composable
private fun EditTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    // Локальный val (параметр-String? не смарт-кастится в захватываемой лямбде).
    val supportingTextValue = supportingText
    val supporting: (@Composable () -> Unit)? = if (supportingTextValue != null) {
        { Text(supportingTextValue) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        supportingText = supporting,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Выбор пола: чипы 1 (женский) / 2 (мужской), ≥44dp тач-таргет. */
@Composable
private fun EditSexRow(sex: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "Пол",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sex == 1,
                onClick = { onSelect(1) },
                enabled = enabled,
                label = { Text("Женский") },
                modifier = Modifier.heightIn(min = 44.dp),
            )
            FilterChip(
                selected = sex == 2,
                onClick = { onSelect(2) },
                enabled = enabled,
                label = { Text("Мужской") },
                modifier = Modifier.heightIn(min = 44.dp),
            )
        }
        if (sex == 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Пол не указан — выберите, чтобы установить",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Строка «Семейное положение» — тап открывает диалог выбора (relation). */
@Composable
private fun EditRelationRow(label: String, enabled: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onOpen() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Семейное положение",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Выбрать")
    }
}

/**
 * Строка «Город» — П-7-C: редактируемая, тап открывает диалог поиска
 * (EditCitySearchDialog → database.getCities → выбор = cityId+title).
 * Стиль — как EditRelationRow (кликабельная строка с шевроном).
 */
@Composable
private fun EditCityRow(cityText: String, enabled: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onOpen() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Город",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (cityText.isBlank()) "Не указан" else cityText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Нажмите, чтобы найти город в справочнике VK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Выбрать город")
    }
}

/** Read-only строка (about/site — вне сигнатуры accountSaveProfileInfo). */
@Composable
private fun EditReadOnlyRow(label: String, hint: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * Диалог поиска города (П-7-C, остаток §3 «профиль.этап-П5.решение.md»):
 * TextField «Поиск города» + debounce 350 мс → VKApiClient.databaseGetCities
 * (database.getCities, q/country_id/count=30/need_all=0) → список подсказок
 * → выбор = onSelect(id, title). Пустой запрос НЕ отправляется; loading /
 * ошибка / пустой результат показываются честно (no-stub).
 *
 * Честные отклонения:
 *  - countryId: передаётся из префилла (country.id account.getProfileInfo);
 *    если профиль страну не отдал — вызывающая сторона даёт 1 (Россия,
 *    серверный дефолт VK). Выбор страны в диалоге не делается (вне объёма).
 *  - «Очистить город» нет: поведение пустого city=0 в account.saveProfileInfo
 *    VK не документировано — не имитируем (см. шапку файла).
 *
 * Стиль — AlertDialog, как диалог relation выше (П-3). Список ≤30 (count=30)
 * — фиксированный Column.verticalScroll, НЕ Lazy (паттерн Fix #284:
 * items/itemsIndexed только в Lazy-скоупах; ленивость для 30 строк не нужна).
 */
@Composable
private fun EditCitySearchDialog(
    countryId: Int,
    onDismiss: () -> Unit,
    onSelect: (Int, String) -> Unit,
) {
    val app = SovaApp.get()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VKApiClient.CitySuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Debounce 350 мс: эффект перезапускается на каждый ввод (key = query),
    // отменяя предыдущую корутину (запрос/дозапись не переживают отмену);
    // сталeness-гвард перед применением результата — на случай гонки.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searching = false
            searchError = null
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        searchError = null
        try {
            val found = app.apiClient.databaseGetCities(q, countryId = countryId, count = 30)
            if (q == query.trim()) {
                results = found
                searching = false
            }
        } catch (e: Exception) {
            AppLog.e(EDIT_TAG, "city search failed", e)
            if (q == query.trim()) {
                results = emptyList()
                searchError = "Ошибка: ${e.message}"
                searching = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Город") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(100) },
                    label = { Text("Поиск города") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val err = searchError
                when {
                    searching -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Поиск…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    err != null -> {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    query.trim().isEmpty() -> {
                        Text(
                            text = "Начните вводить название города",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    results.isEmpty() -> {
                        Text(
                            text = "Ничего не найдено",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            results.forEach { city ->
                                TextButton(
                                    onClick = { onSelect(city.id, city.title) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                ) {
                                    Text(
                                        text = city.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
