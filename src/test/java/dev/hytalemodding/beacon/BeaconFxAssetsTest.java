package dev.hytalemodding.beacon;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica las variantes sin necesidad de arrancar Hytale. */
class BeaconFxAssetsTest {
    @Test
    void removesColorsButPreservesTexturesAndOtherFields() {
        BsonDocument original = BsonDocument.parse("""
                {
                  "Texture": "Particles/Textures/Basic/Glow.png",
                  "InitialAnimationFrame": {"Color": "#FFD700", "Opacity": 0.7},
                  "Animation": {"0": {"Color": "#FFFFFF"}, "100": {"Color": "#FFFF00"}}
                }
                """);
        assertEquals(3, BeaconFxAssets.removeFixedColors(original));
        assertFalse(original.toJson().contains("\"Color\""));
        assertEquals("Particles/Textures/Basic/Glow.png", original.getString("Texture").getValue());
        assertEquals(0.7d, original.getDocument("InitialAnimationFrame").getDouble("Opacity").getValue(), 0.00001);
    }

    @Test
    void reducesTangentialSpinWithoutChangingLinearMovement() {
        BsonDocument original = BsonDocument.parse("""
                {
                  "Attractors": [
                    {"RadialTangentAcceleration": 4, "RadialTangentImpulse": -2, "LinearAcceleration": {"Y": 3}}
                  ],
                  "ParticleLifeSpan": {"Min": 2, "Max": 2}
                }
                """);
        assertEquals(2, BeaconFxAssets.slowGroundRotation(original));
        BsonDocument attractor = original.getArray("Attractors").get(0).asDocument();
        assertEquals(2, attractor.getDouble("RadialTangentAcceleration").getValue(), 0.00001);
        assertEquals(-1, attractor.getDouble("RadialTangentImpulse").getValue(), 0.00001);
        assertEquals(3, attractor.getDocument("LinearAcceleration").getInt32("Y").getValue());
        assertEquals(2, original.getDocument("ParticleLifeSpan").getInt32("Min").getValue());
    }

    @Test
    void doublesRotatingParticleLifetimeWhenMovementIsKeyframed() {
        BsonDocument original = BsonDocument.parse("""
                {
                  "Particle": {
                    "Animation": {
                      "0": {"Rotation": {"Z": {"Min": 0, "Max": 0}}},
                      "100": {"Rotation": {"Z": {"Min": 360, "Max": 360}}}
                    }
                  },
                  "ParticleLifeSpan": {"Min": 1, "Max": 1.5}
                }
                """);
        assertEquals(2, BeaconFxAssets.slowGroundRotation(original));
        assertEquals(2, original.getDocument("ParticleLifeSpan").getDouble("Min").getValue(), 0.00001);
        assertEquals(3, original.getDocument("ParticleLifeSpan").getDouble("Max").getValue(), 0.00001);
    }

    @Test
    void doesNotInventMotionWhenGroundHasNoRotationalFields() {
        BsonDocument original = BsonDocument.parse("""
                {"Particle": {"Texture": "Example.png", "Animation": {"0": {"Opacity": 0.5}}},
                 "ParticleLifeSpan": {"Min": 1, "Max": 2}}
                """);
        assertEquals(0, BeaconFxAssets.slowGroundRotation(original));
        assertEquals(1, original.getDocument("ParticleLifeSpan").getInt32("Min").getValue());
    }
}
