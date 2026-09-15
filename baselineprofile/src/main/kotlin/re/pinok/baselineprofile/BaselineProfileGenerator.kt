package re.pinok.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O1 #PERF-BASELINE (Task 77): генератор Baseline Profile для PinoK_1.
 *
 * Прогоняет ключевые сценарии старта на устройстве Android 13+ (у нас Cyber 15),
 * записывает профиль AOT-компиляции. Профиль затем упаковывается в release-APK
 * и ускоряет холодный старт на 20-40%.
 *
 * Запуск: gradlew :baselineprofile:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "re.pinok",
            maxIterations = 5,
            stableIterations = 3,
        ) {
            // Старт приложения (холодный старт).
            pressHome()
            startActivityAndWait()

            // Дождаться основного окна (главный экран/лента).
            val device = device
            device.wait(Until.hasObject(By.pkg("re.pinok").depth(0)), 5_000)

            // Небольшая прокрутка, чтобы прогреть ленту.
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                20,
            )
            device.waitForIdle()
        }
    }
}