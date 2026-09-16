package re.pinok.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O1 #PERF-BASELINE (Task 77): РіРµРЅРµСЂР°С‚РѕСЂ Baseline Profile РґР»СЏ PinoK_1.
 *
 * РџСЂРѕРіРѕРЅСЏРµС‚ РєР»СЋС‡РµРІС‹Рµ СЃС†РµРЅР°СЂРёРё СЃС‚Р°СЂС‚Р° РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ Android 13+ (Сѓ РЅР°СЃ Cyber 15),
 * Р·Р°РїРёСЃС‹РІР°РµС‚ РїСЂРѕС„РёР»СЊ AOT-РєРѕРјРїРёР»СЏС†РёРё. РџСЂРѕС„РёР»СЊ Р·Р°С‚РµРј СѓРїР°РєРѕРІС‹РІР°РµС‚СЃСЏ РІ release-APK
 * Рё СѓСЃРєРѕСЂСЏРµС‚ С…РѕР»РѕРґРЅС‹Р№ СЃС‚Р°СЂС‚ РЅР° 20-40%.
 *
 * Р—Р°РїСѓСЃРє: gradlew :baselineprofile:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "re.pinok.debug",
            maxIterations = 5,
            stableIterations = 3,
        ) {
            // РЎС‚Р°СЂС‚ РїСЂРёР»РѕР¶РµРЅРёСЏ (С…РѕР»РѕРґРЅС‹Р№ СЃС‚Р°СЂС‚).
            pressHome()
            startActivityAndWait()

            // Р”РѕР¶РґР°С‚СЊСЃСЏ РѕСЃРЅРѕРІРЅРѕРіРѕ РѕРєРЅР° (РіР»Р°РІРЅС‹Р№ СЌРєСЂР°РЅ/Р»РµРЅС‚Р°).
            val device = device
            device.wait(Until.hasObject(By.pkg("re.pinok.debug").depth(0)), 5_000)

            // РќРµР±РѕР»СЊС€Р°СЏ РїСЂРѕРєСЂСѓС‚РєР°, С‡С‚РѕР±С‹ РїСЂРѕРіСЂРµС‚СЊ Р»РµРЅС‚Сѓ.
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