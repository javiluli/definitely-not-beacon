package dev.hytalemodding.beacon;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import org.bson.BsonDocument;
import org.bson.BsonDouble;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** Prepara piezas y extensiones del núcleo sin alterar su anchura ni sus animaciones. */
public final class BeaconBeamModels {
    /** Coordenadas del trazado en treintaidosavos; el origen queda un píxel bajo la cara azul. */
    static final int BLOCK_UNITS = 32, ORIGIN_UNITS = 27, HEIGHT_UNITS = 4096 * BLOCK_UNITS;
    private static final int MODEL_UNITS = 64;
    private static final Model[][] CORES = new Model[ChunkUtil.HEIGHT / ChunkUtil.SIZE][14];
    private static final Model[] TAILS = new Model[ChunkUtil.HEIGHT];
    private static Model full;

    private BeaconBeamModels() {}

    /** Se prepara en start, una vez disponibles los assets originales y antes de abrir mundos. */
    public static void load() {
        Model base = Model.createUnitScaleModel(ModelAsset.getAssetMap().getAsset("Beacon_Beam_Core"));
        full = base;
        for (int power = 0; power < CORES[0].length; power++) {
            String path = "VFX/Beacon/Sections/Beacon_Core_" + power + ".blockymodel";
            BsonDocument template = read(path);
            for (int section = 0; section < CORES.length; section++) {
                Box bounds = new Box(base.getBoundingBox());
                bounds.min.y = ChunkUtil.minBlock(section);
                bounds.max.y = bounds.min.y + (1 << power) / (double) BLOCK_UNITS;
                var data = template.clone();
                data.getArray("nodes").getFirst().asDocument().getDocument("position")
                        .put("y", new BsonDouble(bounds.min.y * MODEL_UNITS));
                CORES[section][power] = section == 0 ? copy(base, bounds, path)
                        : publish(base, bounds, path.replace(".blockymodel", "_Lift_" + section + ".blockymodel"), data);
            }
        }
        BsonDocument template = read("VFX/Beacon/Beacon_Beam_Core.blockymodel");
        for (int offset = 1; offset < TAILS.length; offset++) loadTail(base, template, offset);
    }

    private static BsonDocument read(String path) {
        try (var input = BeaconBeamModels.class.getResourceAsStream("/Common/" + path)) {
            if (input == null) throw new IllegalStateException("Missing Beacon model template: " + path);
            return BsonDocument.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Could not read Beacon model template: " + path, error);
        }
    }

    /** Prepara alturas finales posibles en start; nunca genera recursos ni ancla entidades en el cielo al jugar. */
    private static void loadTail(Model base, BsonDocument template, int offset) {
        double start = offset + 1 - ORIGIN_UNITS / (double) BLOCK_UNITS;
        double height = HEIGHT_UNITS / (double) BLOCK_UNITS - start;
        var data = template.clone();
        var root = data.getArray("nodes").getFirst().asDocument();
        root.getDocument("position").put("y", new BsonDouble(start * MODEL_UNITS));
        var last = root.getArray("children").getLast().asDocument();
        var shape = last.getDocument("shape");
        double size = shape.getDocument("settings").getDocument("size").getNumber("y").doubleValue();
        double bottom = last.getDocument("position").getNumber("y").doubleValue()
                - shape.getDocument("stretch").getNumber("y").doubleValue() * size / 2;
        last.getDocument("position").put("y", new BsonDouble((bottom + height * MODEL_UNITS) / 2));
        shape.getDocument("stretch").put("y", new BsonDouble((height * MODEL_UNITS - bottom) / size));
        String path = "VFX/Beacon/Tails/Beacon_Tail_" + offset + ".blockymodel";
        Box bounds = new Box(base.getBoundingBox());
        bounds.min.y = start;
        TAILS[offset] = publish(base, bounds, path, data);
    }

    /** Publica una variante una sola vez en start, conservando sus bytes para clientes futuros. */
    private static Model publish(Model base, Box bounds, String path, BsonDocument data) {
        String pack = ModelAsset.getAssetMap().getAssetPack(base.getModelAssetId());
        byte[] bytes = data.toJson().getBytes(StandardCharsets.UTF_8);
        // CommonAsset usa una caché débil; estas variantes en memoria deben conservar sus bytes para futuros clientes.
        CommonAssetModule.get().addCommonAsset(pack, new CommonAsset(path, bytes) {
            @Override protected CompletableFuture<byte[]> getBlob0() { return CompletableFuture.completedFuture(bytes); }
        }, false);
        return copy(base, bounds, path);
    }

    private static Model copy(Model base, Box bounds, String path) {
        return new Model(base.getModelAssetId(), base.getScale(), base.getRandomAttachmentIds(),
                base.getAttachments(), bounds, path, base.getTexture(), base.getGradientSet(), base.getGradientId(),
                base.getEyeHeight(), base.getCrouchOffset(), base.getSittingOffset(), base.getSleepingOffset(),
                base.getAnimationSetMap(), base.getCamera(), base.getLight(), base.getParticles(), base.getTrails(),
                base.getPhysicsValues(), base.getDetailBoxes(), base.getPhobia(), base.getPhobiaModelAssetId());
    }

    /** Devuelve la geometría desplazada para anclar el tramo en la sección activa del Beacon. Límites en 1/32. */
    static Model core(int start, int end, int beaconY) {
        int length = end - start;
        if (start >= 0 && length > 0 && end <= HEIGHT_UNITS) {
            if (length == HEIGHT_UNITS) return full;
            if (Integer.bitCount(length) == 1) {
                int power = Integer.numberOfTrailingZeros(length);
                int section = ChunkUtil.indexSection(beaconY + (ORIGIN_UNITS + start) / BLOCK_UNITS) - ChunkUtil.indexSection(beaconY);
                if (power < CORES[0].length && section >= 0 && section < CORES.length) return CORES[section][power];
            }
            int offset = start / BLOCK_UNITS;
            // Tras el bloque del cristal quedan 5/32 respecto al origen, situado a 27/32 sobre el Beacon.
            if (end == HEIGHT_UNITS && start % BLOCK_UNITS == BLOCK_UNITS - ORIGIN_UNITS
                    && offset > 0 && offset < TAILS.length) return TAILS[offset];
        }
        throw new IllegalArgumentException("Unsupported Beacon core span: " + start + ".." + end);
    }
}
