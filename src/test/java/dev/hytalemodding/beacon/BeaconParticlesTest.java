package dev.hytalemodding.beacon;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Contratos independientes del cliente para la activación visual. */
class BeaconParticlesTest {
    @Test
    void keepsAViewerInRangeWithoutCheckingVerticalDistance() {
        Vector3d beacon = new Vector3d(0.5, 90, 0.5);
        assertTrue(BeaconParticles.visible(beacon, new Vector3d(0.5, 180, 0.5)));
        assertTrue(BeaconParticles.visible(beacon, new Vector3d(150.5, 0, 0.5)));
        assertFalse(BeaconParticles.visible(beacon, new Vector3d(151.5, 0, 0.5)));
        assertFalse(BeaconParticles.visible(beacon, new Vector3d(140, 0, 140)));
    }

    @Test
    void refreshesOnlyAfterSevenSecondsOrClockReset() {
        assertFalse(BeaconParticles.shouldRefresh(139, 0));
        assertTrue(BeaconParticles.shouldRefresh(140, 0));
        assertFalse(BeaconParticles.shouldRefresh(160, 140));
        assertTrue(BeaconParticles.shouldRefresh(280, 140));
        assertTrue(BeaconParticles.shouldRefresh(0, 140));
    }

    @Test
    void matchesTheValidatedCustomPreviewSettings() {
        assertEquals(40f, BeaconParticles.HEIGHT);
        assertEquals(0.65f, BeaconParticles.WIDTH);
        assertEquals(0.94f, BeaconParticles.BRIGHTNESS);
        assertEquals(0.85f, BeaconParticles.BASE_SCALE);
        assertEquals(0.5f, BeaconParticles.BASE_ROTATION);
        assertTrue(BeaconParticles.EFFECT_DURATION > BeaconParticles.REFRESH_TICKS / 20f);
        assertEquals(64, BeaconFxProfiles.select(BeaconParticles.HEIGHT,
                BeaconParticles.WIDTH, BeaconParticles.BRIGHTNESS).ratio());
    }
}
