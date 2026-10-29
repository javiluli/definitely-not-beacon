package dev.hytalemodding.beacon;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Contratos independientes del cliente para la activación visual. */
class BeaconParticlesTest {
    @Test
    void raisesBothBeamAndBaseWithoutChangingTheHorizontalCenter() {
        var position = new BeaconStructures.Position(12, 70, -4);
        Vector3d origin = BeaconParticles.origin(position);
        assertEquals(12.5d, origin.x, 0.0001d);
        assertEquals(71d, origin.y, 0.0001d);
        assertEquals(-3.5d, origin.z, 0.0001d);
        assertEquals(0.90d, BeaconParticles.VISUAL_Y_ADJUSTMENT, 0.0001d);
        assertEquals(74.5d, BeaconParticles.footCenterY(origin.y), 0.0001d);
    }

    @Test
    void nativeGlowLowerFootRemainsShorterAndNarrowerThanMainShaft() {
        var foot = BeaconFxProfiles.select(BeaconParticles.FOOT_HEIGHT,
                BeaconParticles.FOOT_WIDTH, BeaconParticles.FOOT_BRIGHTNESS);
        var main = BeaconFxProfiles.select(BeaconParticles.HEIGHT,
                BeaconParticles.WIDTH, BeaconParticles.BRIGHTNESS);
        assertTrue(foot.heightAt(BeaconParticles.FOOT_WIDTH) < main.heightAt(BeaconParticles.WIDTH));
        assertTrue(BeaconParticles.FOOT_WIDTH < BeaconParticles.WIDTH);
        assertTrue(BeaconParticles.FOOT_BRIGHTNESS < BeaconParticles.BRIGHTNESS);
        // El borde inferior del sprite de apoyo no debe quedar hundido bajo la apertura.
        assertTrue(BeaconParticles.FOOT_CENTER_ABOVE_BASE
                - foot.heightAt(BeaconParticles.FOOT_WIDTH) / 2f >= -0.5d);
    }

    @Test
    void crystalsUseTheFinalMixedColorOrVanillaWhiteWithoutAnyTint() {
        var white = java.util.List.of(new BeaconCrystals.Span(0, 64, -1));
        assertEquals(-1, BeaconParticles.tintFromSpans(white));

        var blueThenPurple = java.util.List.of(
                new BeaconCrystals.Span(0, 32, -1),
                new BeaconCrystals.Span(32, 64, 0x0040F0),
                new BeaconCrystals.Span(64, 128, 0xA060C0));
        assertEquals(BeaconCrystals.luminousColor(0xA060C0),
                BeaconParticles.tintFromSpans(blueThenPurple));
    }

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
