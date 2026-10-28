package moe.n4tsu.dextop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceMatchTest {
    private val xiaomiAurora = DeviceIdentity(
        "Xiaomi", "Xiaomi", "24030PN60G", "aurora", "aurora_global", "Xiaomi/aurora_global/aurora:16/build", 36,
    )

    @Test fun redmi12cAndroid15GsiUsesAospProfileBeforeTheBroadXiaomiRule() {
        val redmi = DeviceIdentity(
            "Xiaomi", "Redmi", "22120RN86C", "earth", "earth",
            "Redmi/earth/earth:12/SP1A.210812.016/V13.0.2.0.SCVCNXM:user/release-keys", 35
        )
        val profile = DesktopEnvironmentRegistry.resolve(redmi)

        assertEquals("redmi12c_android15_gsi", profile.id)
        assertFalse(profile.directPlasmaDisplay)
        assertFalse(profile.untrustedMirrorDisplay)
        assertFalse(profile.fullscreenApplicationDisplays)
        assertFalse(profile.privateApplicationDisplays)
        assertFalse(profile.passivePlasmaShellFocus)
        assertEquals("system", profile.defaultWindowManager)
        assertTrue(profile.configureFreeformWindowing)
        assertEquals("1", profile.temporaryGlobalSettings["enable_freeform_support"])
        assertEquals("1", profile.temporaryGlobalSettings["force_resizable_activities"])
        assertEquals("virtual_display", profile.mirrorStrategies.first())
    }

    @Test fun redmi12cGsiOverrideDoesNotMatchAnotherDeviceModelOrAndroidVersion() {
        val redmi = DeviceIdentity(
            "Xiaomi", "Redmi", "22120RN86C", "earth", "earth",
            "Redmi/earth/earth:12/SP1A.210812.016/V13.0.2.0.SCVCNXM:user/release-keys", 35
        )
        assertEquals("xiaomi_hyperos", DesktopEnvironmentRegistry.resolve(redmi.copy(model = "other")).id)
        assertEquals("xiaomi_hyperos", DesktopEnvironmentRegistry.resolve(redmi.copy(device = "other")).id)
        assertEquals("xiaomi_hyperos", DesktopEnvironmentRegistry.resolve(redmi.copy(sdk = 34)).id)
    }

    @Test fun everyXiaomiDeviceUsesTheHyperosProfileAndStartsOnTheDextopWindowManager() {
        for (identity in listOf(
            xiaomiAurora,
            xiaomiAurora.copy(model = "other", device = "other"),
            xiaomiAurora.copy(sdk = 33), xiaomiAurora.copy(sdk = 37),
            xiaomiAurora.copy(manufacturer = "Redmi", brand = "Redmi"),
            xiaomiAurora.copy(manufacturer = "POCO", brand = "POCO"),
        )) {
            val profile = DesktopEnvironmentRegistry.resolve(identity)
            assertEquals("xiaomi_hyperos", profile.id)
            assertTrue(profile.directPlasmaDisplay)
            assertTrue(profile.untrustedMirrorDisplay)
            assertTrue(profile.fullscreenApplicationDisplays)
            assertTrue(profile.privateApplicationDisplays)
            assertTrue(profile.passivePlasmaShellFocus)
            assertEquals("dextop_plasma_v2", profile.defaultWindowManager)
        }
    }

    @Test fun xperiaStartsOnTheDextopWindowManagerAndOthersKeepTheSystemOne() {
        val xperia = DeviceIdentity("Sony", "Sony", "A401SO", "A401SO", "A401SO", "Sony/A401SO/build", 36)
        assertEquals("dextop_plasma_v2", DesktopEnvironmentRegistry.resolve(xperia).defaultWindowManager)
        assertEquals("system", DesktopEnvironmentRegistry.resolve(pixel).defaultWindowManager)
    }

    private val pixel = DeviceIdentity("Google", "google", "Pixel 9", "tokay", "tokay", "google/tokay/build", 35)

    @Test fun normalizesAndMatchesAllSpecifiedFields() {
        val match = DeviceMatch(
            manufacturers = setOf("google"), models = setOf("pixel 9"),
            devices = setOf("tokay"), fingerprintPrefixes = setOf("google/tokay"),
            minSdk = 35, maxSdk = 35
        )
        assertTrue(match.matches(pixel))
    }

    @Test fun modelOverrideDoesNotLeakToOtherModels() {
        val match = DeviceMatch(manufacturers = setOf("google"), models = setOf("pixel 9"))
        assertFalse(match.matches(pixel.copy(model = "Pixel 8")))
        assertFalse(match.matches(pixel.copy(manufacturer = "Samsung", model = "Pixel 9")))
    }

    @Test fun sdkRangeIsIsolated() {
        val match = DeviceMatch(manufacturers = setOf("google"), minSdk = 35, maxSdk = 35)
        assertFalse(match.matches(pixel.copy(sdk = 34)))
        assertFalse(match.matches(pixel.copy(sdk = 36)))
    }

    @Test fun legacyPixelProfileRestoresAospFlagsAndOlderMirrorOrder() {
        val environment = DesktopEnvironmentRegistry.legacyPixelAosp(pixel.copy(sdk = 36))
        assertEquals(DesktopStartupMode.COMPAT_WINDOWING, environment.startupMode)
        assertTrue(environment.configureFreeformWindowing)
        assertEquals("1", environment.temporaryGlobalSettings["enable_freeform_support"])
        assertEquals("1", environment.temporaryGlobalSettings["force_resizable_activities"])
        assertEquals("window_manager", environment.mirrorStrategies.first())
    }

    @Test fun modernPixelProfileForcesWindowingFlagsOnVirtualDisplay() {
        val environment = DesktopEnvironmentRegistry.resolve(pixel.copy(sdk = 37))
        assertEquals(DesktopStartupMode.SYSTEM_MANAGED, environment.startupMode)
        assertFalse(environment.configureFreeformWindowing)
        assertEquals("virtual_display", environment.mirrorStrategies.first())
        assertEquals("1", environment.temporaryGlobalSettings["force_resizable_activities"])
        assertEquals("1", environment.temporaryGlobalSettings["force_desktop_mode_on_external_displays"])
        assertEquals(null, environment.temporaryGlobalSettings["override_desktop_experience_features"])
        assertEquals(null, environment.temporaryGlobalSettings["override_desktop_mode_features"])
        assertEquals(null, environment.temporaryGlobalSettings["enable_non_resizable_multi_window"])
        assertEquals(null, environment.temporaryGlobalSettings["force_desktop_mode_on_secondary_displays"])
        assertEquals(null, environment.temporaryGlobalSettings["enable_freeform_support"])
    }

    @Test fun galaxyTriFoldUsesNarrowAutoResizeProfile() {
        val triFold = DeviceIdentity(
            "samsung", "samsung", "SM-F968N", "q7mq", "q7mqksx",
            "samsung/q7mqksx/q7mq:16/build", 36
        )
        val environment = DesktopEnvironmentRegistry.resolve(triFold)
        assertEquals("samsung_dex", environment.id)
        assertTrue(environment.autoResizeWithHostDisplay)

        val fold7 = triFold.copy(model = "SM-F966Q", device = "q7q", product = "q7qjpnw")
        assertFalse(DesktopEnvironmentRegistry.resolve(fold7).autoResizeWithHostDisplay)
    }

    @Test fun regionalSamsungCarrierModelPrefixesUseSamsungProfile() {
        listOf("SC-56F", "SCG39", "SCV46").forEach { model ->
            val identity = DeviceIdentity(
                "unknown", "unknown", model, model, model,
                "unknown/$model/build", 36
            )
            val environment = DesktopEnvironmentRegistry.resolve(identity)
            assertEquals("samsung_dex", environment.id)
            assertTrue(environment.platformManaged)
        }
    }
}
