package dev.hytalemodding.beacon;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Prepara perfiles del haz directamente en Java, sin generar PNG ni registrar assets durante los comandos.
 * La textura y el renderizador siguen siendo los originales de Hytale.
 */
public final class BeaconFxProfiles {
    private static final HytaleLogger LOGGER = HytaleLogger.get("Definitely Not Beacon");
    private static final int[] RATIOS = {
            2, 4, 6, 8, 12, 16, 20, 24, 28, 32, 40, 48, 56, 64, 72,
            80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640
    };
    private static final float[] BRIGHTNESS = {0.72f, 0.94f, 1f};
    private static final String PREFIX = "Beacon_FX_Profile_";
    private static boolean available;

    private BeaconFxProfiles() {}

    /** Perfil precalculado y reutilizable. */
    record Profile(String assetId, int ratio, float brightness) {
        float heightAt(float width) {
            return ratio * width;
        }
    }

    /** El asset debe estar publicado antes de generar partículas desde un mundo. */
    static boolean available() {
        return available;
    }

    /** Selecciona la relación geométrica con menor error proporcional y el brillo más próximo. */
    static Profile select(float height, float width, float brightness) {
        if (!Float.isFinite(height) || !Float.isFinite(width) || !Float.isFinite(brightness)
                || height <= 0 || width <= 0 || brightness < 0.5f || brightness > 1f) {
            throw new IllegalArgumentException("Altura/anchura invalidas o brillo fuera de 0.5..1.");
        }
        double desired = height / width;
        int ratio = RATIOS[0];
        for (int candidate : RATIOS) {
            if (Math.abs(Math.log(candidate / desired)) < Math.abs(Math.log(ratio / desired))) ratio = candidate;
        }
        float chosen = BRIGHTNESS[0];
        for (float candidate : BRIGHTNESS) {
            if (Math.abs(candidate - brightness) < Math.abs(chosen - brightness)) chosen = candidate;
        }
        return new Profile(id(ratio, chosen), ratio, chosen);
    }

    /** Registra una paleta acotada; nunca se crean recursos por cada número que introduce el jugador. */
    public static void load() {
        available = false;
        try {
            String pack = ParticleSystem.getAssetMap().getAssetPack("Beacon_FX_Legendary_Base");
            if (pack == null) throw new IllegalStateException("Missing particle asset pack");
            List<ParticleSpawner> spawners = new ArrayList<>();
            List<ParticleSystem> systems = new ArrayList<>();
            for (int ratio : RATIOS) {
                for (float brightness : BRIGHTNESS) {
                    String id = id(ratio, brightness);
                    String spawnerId = id + "_Spawner";
                    spawners.add(ParticleSpawner.getAssetStore().decode(pack, spawnerId, spawner(ratio, brightness)));
                    systems.add(ParticleSystem.getAssetStore().decode(pack, id, system(spawnerId, ratio)));
                }
            }
            var result = ParticleSpawner.getAssetStore().loadAssets(pack, spawners);
            if (result.hasFailed()) throw new IllegalStateException(
                    "Failed to load ray spawners: " + result.getFailedToLoadKeys());
            var systemsResult = ParticleSystem.getAssetStore().loadAssets(pack, systems);
            if (systemsResult.hasFailed()) throw new IllegalStateException(
                    "Failed to load ray systems: " + systemsResult.getFailedToLoadKeys());
            available = true;
            LOGGER.at(Level.INFO).log("Particle profiles ready: %s ratios, %s brightness levels",
                    RATIOS.length, BRIGHTNESS.length);
        } catch (RuntimeException failure) {
            // El haz estable y las mecanicas originales no dependen de este laboratorio.
            LOGGER.at(Level.WARNING).log("Particle profiles unavailable: %s", failure.getMessage());
        }
    }

    /** Mantiene la geometría de Glow usada en la prueba visual que funcionó. */
    static BsonDocument spawner(int ratio, float brightness) {
        if (ratio < 2 || ratio > 640 || !Float.isFinite(brightness)
                || brightness < 0.5f || brightness > 1f) {
            throw new IllegalArgumentException("Invalid ray profile");
        }
        BsonDocument document = BsonDocument.parse("""
                {
                  "RenderMode": "BlendLinear",
                  "ParticleRotationInfluence": "BillboardY",
                  "LightInfluence": 0,
                  "LinearFiltering": true,
                  "SpawnBurst": true,
                  "SpawnRate": {"Min": 1, "Max": 1},
                  "WaveDelay": {"Min": 999, "Max": 999},
                  "TotalParticles": {"Min": 1, "Max": 1},
                  "LifeSpan": 8,
                  "MaxConcurrentParticles": 1,
                  "ParticleLifeSpan": {"Min": 8, "Max": 8},
                  "Particle": {
                    "Texture": "Particles/Textures/Basic/Glow.png",
                    "ScaleRatioConstraint": "None",
                    "UVOption": "None",
                    "SoftParticles": "Disable",
                    "InitialAnimationFrame": {
                      "Opacity": 0.94,
                      "Scale": {"X": {"Min": 1, "Max": 1}, "Y": {"Min": 64, "Max": 64}}
                    },
                    "Animation": {"0": {"Opacity": 1}, "100": {"Opacity": 1}}
                  }
                }
                """);
        BsonDocument frame = document.getDocument("Particle").getDocument("InitialAnimationFrame");
        frame.put("Opacity", new BsonDouble(brightness));
        frame.getDocument("Scale").put("Y", range(ratio));
        return document;
    }

    /** Un único emisor vertical por haz, con culling proporcional a su alcance. */
    static BsonDocument system(String spawnerId, int ratio) {
        return new BsonDocument("Spawners",
                new BsonArray(List.of(new BsonDocument("SpawnerId", new BsonString(spawnerId)))))
                .append("CullDistance", new BsonDouble(256))
                .append("BoundingRadius", new BsonDouble(Math.max(16, ratio / 2d)))
                .append("IsImportant", org.bson.BsonBoolean.TRUE);
    }

    private static BsonDocument range(double value) {
        return new BsonDocument("Min", new BsonDouble(value))
                .append("Max", new BsonDouble(value));
    }

    private static String id(int ratio, float brightness) {
        return PREFIX + ratio + "_B" + String.format(Locale.ROOT, "%03d", Math.round(brightness * 100));
    }
}
