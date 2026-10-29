package dev.hytalemodding.beacon;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Comprueba los perfiles puros sin necesitar un cliente gráfico. */
class BeaconFxProfilesTest {
    @Test
    void picksNearestAspectRatioAndBrightness() {
        var profile = BeaconFxProfiles.select(40f, 0.65f, 0.94f);
        assertEquals(64, profile.ratio());
        assertEquals(0.94f, profile.brightness());
        assertEquals(41.6f, profile.heightAt(0.65f), 0.001f);
        assertEquals("Beacon_FX_Profile_64_B094", profile.assetId());
        assertEquals(0.72f, BeaconFxProfiles.select(32, 0.5f, 0.6f).brightness());
        assertEquals(2, BeaconFxProfiles.select(4, 2f, 1).ratio());
    }

    @Test
    void validatesRequestedDimensionsAndBrightness() {
        assertThrows(IllegalArgumentException.class, () -> BeaconFxProfiles.select(0, 0.5f, 0.94f));
        assertThrows(IllegalArgumentException.class, () -> BeaconFxProfiles.select(32, Float.NaN, 0.94f));
        assertThrows(IllegalArgumentException.class, () -> BeaconFxProfiles.select(32, 0.5f, 0.2f));
        assertThrows(IllegalArgumentException.class, () -> BeaconFxProfiles.select(32, 0.5f, Float.POSITIVE_INFINITY));
    }

    @Test
    void generatedSpawnersKeepTheNativeGlowAndIndependentDimensions() {
        BsonDocument data = BeaconFxProfiles.spawner(112, 0.72f);
        BsonDocument particle = data.getDocument("Particle");
        BsonDocument frame = particle.getDocument("InitialAnimationFrame");
        assertEquals("Particles/Textures/Basic/Glow.png", particle.getString("Texture").getValue());
        assertEquals("None", particle.getString("ScaleRatioConstraint").getValue());
        assertEquals(112, frame.getDocument("Scale").getDocument("Y").getNumber("Min").intValue());
        assertEquals(1, frame.getDocument("Scale").getDocument("X").getNumber("Min").intValue());
        assertEquals(0.72f, frame.getDouble("Opacity").getValue(), 0.001);
        assertFalse(frame.containsKey("Color"));
        assertEquals("BlendLinear", data.getString("RenderMode").getValue());
    }

    @Test
    void generatedSystemsReferenceTheIntendedSpawner() {
        BsonDocument data = BeaconFxProfiles.system("Beacon_FX_Profile_64_B094_Spawner", 64);
        assertEquals("Beacon_FX_Profile_64_B094_Spawner",
                data.getArray("Spawners").get(0).asDocument().getString("SpawnerId").getValue());
        assertTrue(data.getBoolean("IsImportant").getValue());
    }
}
