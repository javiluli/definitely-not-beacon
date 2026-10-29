package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;

/** Publica variantes en memoria de los efectos originales sin nuevos archivos ni texturas. */
public final class BeaconFxAssets {
    static final String TINTED_BASE = "Beacon_FX_Legendary_Base_Tinted";
    private static final String BASE = "Beacon_FX_Legendary_Base";
    private static final String PREFIX = "Drop_Legendary_";
    private static final String[] PARTS = {"Ground", "Vortex", "Glow", "Smoke", "Sparks_Squares", "Sparks_Dark"};
    private static final double ROTATION_SPEED = 0.5;
    private static final float[] ROTATION_SPEEDS = {0.35f, 0.5f, 0.75f, 1f};
    private static final Set<Float> readySpeeds = new LinkedHashSet<>();
    private static final HytaleLogger LOGGER = HytaleLogger.get("Definitely Not Beacon");
    private static boolean available;

    private BeaconFxAssets() {}

    /** Indica si los assets del experimento se publicaron correctamente. */
    static boolean available() {
        return available;
    }

    /** Las velocidades se seleccionan de una lista acotada, sin publicar desde el hilo del mundo. */
    static boolean validRotationSpeed(float speed) {
        return Float.isFinite(speed) && speed >= 0.25f && speed <= 1f;
    }

    /** Obtiene la base recoloreable con el giro más cercano disponible. */
    static String tintedBase(float requested) {
        if (!available || readySpeeds.isEmpty()) return BASE;
        float nearest = ROTATION_SPEEDS[1];
        double difference = Double.POSITIVE_INFINITY;
        for (float speed : readySpeeds) {
            if (Math.abs(speed - requested) < difference) {
                nearest = speed;
                difference = Math.abs(speed - requested);
            }
        }
        return Math.abs(nearest - ROTATION_SPEED) < 0.0001 ? TINTED_BASE : variantId(nearest);
    }

    private static String variantId(float speed) {
        return TINTED_BASE + "_S" + Math.round(speed * 100);
    }

    /** Clona y publica al arrancar. Nunca registra assets desde el hilo de juego. */
    public static void load() {
        available = false;
        readySpeeds.clear();
        try {
            ParticleSystem base = ParticleSystem.getAssetMap().getAsset(BASE);
            if (base == null) throw new IllegalStateException("Missing original particle system " + BASE);
            String pack = ParticleSystem.getAssetMap().getAssetPack(BASE);
            if (pack == null) throw new IllegalStateException("Missing asset pack for " + BASE);

            List<ParticleSpawner> variants = new ArrayList<>();
            int slowed = 0;
            for (String part : PARTS) {
                String nativeId = PREFIX + part;
                ParticleSpawner original = ParticleSpawner.getAssetMap().getAsset(nativeId);
                if (original == null) throw new IllegalStateException("Missing native spawner " + nativeId);
                BsonDocument data = ParticleSpawner.CODEC.encode(original, new ExtraInfo()).asDocument();
                data.remove("Id");
                data.remove("Parent");
                BsonDocument particle = data.getDocument("Particle", null);
                if (particle != null) removeFixedColors(particle);
                if (part.equals("Ground")) slowed = slowGroundRotation(data);
                variants.add(ParticleSpawner.getAssetStore().decode(pack, "Beacon_FX_Legendary_" + part, data));
            }
            var spawnerResult = ParticleSpawner.getAssetStore().loadAssets(pack, variants);
            if (spawnerResult.hasFailed()) throw new IllegalStateException(
                    "Could not publish particle spawners: " + spawnerResult.getFailedToLoadKeys());

            BsonDocument system = ParticleSystem.CODEC.encode(base, new ExtraInfo()).asDocument();
            system.remove("Id");
            system.remove("Parent");
            BsonArray groups = system.getArray("Spawners");
            for (BsonValue value : groups) {
                BsonDocument group = value.asDocument();
                String nativeId = group.getString("SpawnerId").getValue();
                if (nativeId.startsWith(PREFIX)) {
                    group.put("SpawnerId", new BsonString("Beacon_FX_Legendary_" + nativeId.substring(PREFIX.length())));
                }
            }
            var variant = ParticleSystem.getAssetStore().decode(pack, TINTED_BASE, system);
            var systemResult = ParticleSystem.getAssetStore().loadAssets(pack, List.of(variant));
            if (systemResult.hasFailed()) throw new IllegalStateException(
                    "Could not publish tinted base: " + systemResult.getFailedToLoadKeys());
            available = true;
            readySpeeds.add((float) ROTATION_SPEED);
            for (float speed : ROTATION_SPEEDS) {
                if (Math.abs(speed - ROTATION_SPEED) < 0.0001f) continue;
                try {
                    publishGroundSpeed(pack, base, speed);
                    readySpeeds.add(speed);
                } catch (RuntimeException error) {
                    LOGGER.at(Level.WARNING).log("Base rotation variant %.2f unavailable: %s", speed, error.getMessage());
                }
            }
            if (slowed == 0) {
                LOGGER.at(Level.WARNING).log("Tinted base loaded, but Ground exposes no supported rotation control to slow");
            } else {
                LOGGER.at(Level.INFO).log("Tinted Legendary base ready; Ground rotation fields adjusted: %s", slowed);
            }
        } catch (RuntimeException error) {
            // Un error del laboratorio nunca debe impedir el arranque del Beacon real.
            LOGGER.at(Level.WARNING).log("Tinted Legendary base unavailable: %s", error.getMessage());
        }
    }

    /** Publica una variante de giro reutilizando las partículas decorativas comunes. */
    private static void publishGroundSpeed(String pack, ParticleSystem originalSystem, float speed) {
        ParticleSpawner original = ParticleSpawner.getAssetMap().getAsset(PREFIX + "Ground");
        BsonDocument data = ParticleSpawner.CODEC.encode(original, new ExtraInfo()).asDocument();
        data.remove("Id");
        data.remove("Parent");
        BsonDocument particle = data.getDocument("Particle", null);
        if (particle != null) removeFixedColors(particle);
        slowGroundRotation(data, speed);
        String groundId = "Beacon_FX_Legendary_Ground_S" + Math.round(speed * 100);
        var spawner = ParticleSpawner.getAssetStore().decode(pack, groundId, data);
        var result = ParticleSpawner.getAssetStore().loadAssets(pack, List.of(spawner));
        if (result.hasFailed()) throw new IllegalStateException("Failed ground variation: " + result.getFailedToLoadKeys());

        BsonDocument system = ParticleSystem.CODEC.encode(originalSystem, new ExtraInfo()).asDocument();
        system.remove("Id");
        system.remove("Parent");
        for (BsonValue value : system.getArray("Spawners")) {
            BsonDocument entry = value.asDocument();
            String nativeId = entry.getString("SpawnerId").getValue();
            if (nativeId.startsWith(PREFIX)) {
                String part = nativeId.substring(PREFIX.length());
                entry.put("SpawnerId", new BsonString(part.equals("Ground")
                        ? groundId : "Beacon_FX_Legendary_" + part));
            }
        }
        var variant = ParticleSystem.getAssetStore().decode(pack, variantId(speed), system);
        var resultSystem = ParticleSystem.getAssetStore().loadAssets(pack, List.of(variant));
        if (resultSystem.hasFailed()) throw new IllegalStateException("Failed base variation: "
                + resultSystem.getFailedToLoadKeys());
    }

    /** El color del comando prevalece frente a los colores de los frames de la partícula. */
    static int removeFixedColors(BsonValue value) {
        int removed = 0;
        if (value.isDocument()) {
            BsonDocument document = value.asDocument();
            if (document.remove("Color") != null) removed++;
            for (BsonValue child : document.values()) removed += removeFixedColors(child);
        } else if (value.isArray()) {
            for (BsonValue child : value.asArray()) removed += removeFixedColors(child);
        }
        return removed;
    }

    /** Reduce a la mitad las fuerzas circulares o la cadencia de animación del anillo. */
    static int slowGroundRotation(BsonDocument spawner) {
        return slowGroundRotation(spawner, ROTATION_SPEED);
    }

    /** Escala la rotación original sin modificar la velocidad de movimiento de las demás partículas. */
    static int slowGroundRotation(BsonDocument spawner, double speed) {
        if (!Double.isFinite(speed) || speed <= 0d || speed > 1d) {
            throw new IllegalArgumentException("Rotation speed must be in (0,1]");
        }
        int scaled = slowTangentialMotion(spawner, speed);
        if (scaled > 0) return scaled;
        BsonDocument particle = spawner.getDocument("Particle", null);
        BsonDocument animation = particle == null ? null : particle.getDocument("Animation", null);
        BsonDocument lifetime = spawner.getDocument("ParticleLifeSpan", null);
        if (animation == null || lifetime == null) return 0;
        boolean spins = animation.values().stream().anyMatch(frame -> frame.isDocument()
                && (frame.asDocument().containsKey("Rotation") || frame.asDocument().containsKey("FrameIndex")));
        return spins ? scalePositiveRange(lifetime, 1d / speed) : 0;
    }

    /** Ajusta solo velocidades tangenciales, no movimiento ni velocidad lineales. */
    private static int slowTangentialMotion(BsonValue value, double speed) {
        int scaled = 0;
        if (value.isDocument()) {
            BsonDocument document = value.asDocument();
            for (String key : List.copyOf(document.keySet())) {
                BsonValue child = document.get(key);
                if ((key.equals("RadialTangentAcceleration") || key.equals("RadialTangentImpulse")) && child.isNumber()) {
                    document.put(key, new BsonDouble(child.asNumber().doubleValue() * speed));
                    scaled++;
                } else {
                    scaled += slowTangentialMotion(child, speed);
                }
            }
        } else if (value.isArray()) {
            for (BsonValue child : value.asArray()) scaled += slowTangentialMotion(child, speed);
        }
        return scaled;
    }

    /** Duplica la vida de la partícula giratoria cuando su giro se anima por fotogramas. */
    private static int scalePositiveRange(BsonDocument range, double factor) {
        int changed = 0;
        for (String key : List.of("Min", "Max")) {
            BsonValue value = range.get(key);
            if (value != null && value.isNumber() && value.asNumber().doubleValue() > 0d) {
                range.put(key, new BsonDouble(value.asNumber().doubleValue() * factor));
                changed++;
            }
        }
        return changed;
    }
}
