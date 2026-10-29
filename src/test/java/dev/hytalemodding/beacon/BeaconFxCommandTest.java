package dev.hytalemodding.beacon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Prueba las reglas puras del laboratorio sin iniciar un mundo Hytale. */
class BeaconFxCommandTest {
    @Test
    void selectsAvailableRatiosForCommonBeamSizes() {
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
}
