package re.pinok.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * #AUTH-FIRST-OPEN-GUEST (2026-09-23): guest-drawer для режима БЕЗ авторизации.
 *
 * Требование юзера: при первом открытии приложения (нет валидного токена)
 * экран входа (AuthActivity → LandingScreen) НЕ показывается автоматически.
 * Вход — только по кнопке «Войти в аккаунт», закреплённой в боковой панели.
 *
 * Особенности:
 *  - Используется ТОЛЬКО в MainActivity guest-ветке (isOfflineMode), вокруг
 *    OfflineManagerScreen. Основной drawer приложения живёт в SovaNavHost
 *    (авторизованный режим) и не затрагивается.
 *  - Кнопка «Войти в аккаунт» — ФИКСИРОВАННЫЙ хвост (Fix #337-стиль):
 *    HorizontalDivider + NavigationDrawerItem, не редактируется пользователем
 *    (sidebar-редактор SovaNavHost сюда не применяется по определению).
 *  - Кнопка запускает AuthActivity через launchAuth(reason = "drawer-login") —
 *    LandingScreen является внутренней фазой AuthActivity (private enum
 *    AuthPhase.LANDING) и напрямую показан быть не может. См.
 *    docs/AUTH-FIRST-OPEN-GUEST-PLAN.md §2.
 *  - Пункты middle: «Офлайн-данные» (текущий guest-экран) + «Настройки»
 *    (#AUTH-FIRST-OPEN-GUEST-2: локальные настройки — интерфейс/сеть/офлайн/
 *    данные/обновления — работают без токена; секции, требующие API
 *    (звонки, VK ID, уведомления), покажут inline-ошибку при попытке).
 *
 * @param drawerState state drawer'а, хостится снаружи (MainActivity guest-ветка)
 *   чтобы стрелка «Назад»/меню в TopAppBar OfflineManagerScreen могла открыть
 *   панель (см. onMenu-параметр OfflineManagerScreen).
 * @param onSettings колбэк пункта «Настройки» — открывает guest-оверлей настроек.
 * @param onLogin колбэк кнопки «Войти в аккаунт» — ЗАПУСКАЕТ AuthActivity.
 * @param content контент под drawer'ом (OfflineManagerScreen + оверлеи).
 */
@Composable
fun GuestDrawer(
    drawerState: DrawerState,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    // Header: заголовок + кнопка сворачивания — стиль-паритет
                    // с drawer'ом SovaNavHost (#247).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "PinoK",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.MenuOpen,
                                contentDescription = "Свернуть меню",
                            )
                        }
                    }
                    // Middle: guest-экраны — офлайн-данные (текущий) + настройки
                    // (#AUTH-FIRST-OPEN-GUEST-2: локальные настройки доступны без токена).
                    // weight(1f) держит фиксированный хвост прижатым к низу
                    // при любом fontScale (Fix #337-стиль).
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        NavigationDrawerItem(
                            label = { Text("Офлайн-данные") },
                            selected = true,
                            onClick = { scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
                        )
                        NavigationDrawerItem(
                            label = { Text("Настройки") },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSettings()
                            },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        )
                    }
                    // Фиксированный хвост: «Войти в аккаунт» — единственный
                    // способ войти из guest-режима (#AUTH-FIRST-OPEN-GUEST).
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("Войти в аккаунт") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLogin()
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                    )
                }
            }
        },
    ) {
        content()
    }
}
