package dev.hytalemodding.beacon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Prueba las reglas puras del laboratorio sin iniciar un mundo Hytale. */
class BeaconFxCommandTest {
    @Test
    void selectsAvailableRatiosForCommonBeamSizes() {
        assertEquals(2, BeaconFxCommand.ratio(4 / 2.0));
        assertEquals(8, BeaconFxCommand.ratio(4 / 0.5));
        assertEquals(64, BeaconFxCommand.ratio(32 / 0.5));
        assertEquals(80, BeaconFxCommand.ratio(64 / 0.8));
        assertEquals(128, BeaconFxCommand.ratio(128));
    }

    @Test
    void parsesColorsAndNativeOption() {
        assertNull(BeaconFxCommand.color("native"));
        var cyan = BeaconFxCommand.color("cyan");
        assertEquals(0x49, Byte.toUnsignedInt(cyan.red));
        assertEquals(0xDF, Byte.toUnsignedInt(cyan.green));
        assertEquals(0xFF, Byte.toUnsignedInt(cyan.blue));
        assertThrows(IllegalArgumentException.class, () -> BeaconFxCommand.color("not-a-color"));
    }
    @Test
    void previewAssetsAllowRuntimeTintWithoutFixedWhite() throws Exception {
        int[] ratios = {2, 4, 8, 16, 32, 48, 64, 80, 96, 128, 160, 256, 384, 512, 640};
        for (int ratio : ratios) {
            String path = "/Server/Particles/Beacon/Beacon_FX_Ray_" + ratio + "_Spawner.particlespawner";
            try (var stream = BeaconFxCommandTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, "Asset de rayo no encontrado: " + ratio);
                String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(json.contains("\"Color\""), "El color fijo anula el tintado Java: " + ratio);
                assertTrue(json.contains("BlendLinear"), "El modo aditivo puede blanquear el color: " + ratio);
            }
        }
    }

    @Test
    void legendaryBaseUsesNativeDecorationOnly() throws Exception {
        try (var stream = BeaconFxCommandTest.class.getResourceAsStream(
                "/Server/Particles/Beacon/Beacon_FX_Legendary_Base.particlesystem")) {
            assertNotNull(stream);
            String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(json.contains("Drop_Legendary_Ground"));
            assertTrue(json.contains("Drop_Legendary_Sparks_Squares"));
            assertFalse(json.contains("Drop_Legendary_Ray_Center"));
            assertFalse(json.contains("\"Drop_Legendary_Ray\""));
        }
    }

    @Test
    void tintedBaseDoesNotReferenceHardCodedLegendaryColors() throws Exception {
        try (var stream = BeaconFxCommandTest.class.getResourceAsStream(
                "/Server/Particles/Beacon/Beacon_FX_Tinted_Base.particlesystem")) {
            assertNotNull(stream);
            String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(json.contains("Beacon_FX_Tinted_Glow_Spawner"));
            assertTrue(json.contains("Beacon_FX_Tinted_Sparks_Spawner"));
            assertFalse(json.contains("Drop_Legendary_"));
        }
        for (String name : new String[]{"Glow", "Sparks"}) {
            String path = "/Server/Particles/Beacon/Beacon_FX_Tinted_" + name + "_Spawner.particlespawner";
            try (var stream = BeaconFxCommandTest.class.getResourceAsStream(path)) {
                assertNotNull(stream);
                String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(json.contains("\"Color\""), "El color se debe recibir desde Java: " + name);
            }
        }
    }

    @Test
    void nativeCenterOnlyReferencesOriginalSpawner() throws Exception {
        try (var stream = BeaconFxCommandTest.class.getResourceAsStream(
                "/Server/Particles/Beacon/Beacon_FX_Native_Ray_Center.particlesystem")) {
            assertNotNull(stream);
            String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(json.contains("Drop_Legendary_Ray_Center"));
            assertFalse(json.contains("Drop_Legendary_Ground"));
        }
    }

}
