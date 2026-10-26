package dev.hytalemodding.beacon;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.modelvfx.config.ModelVFX;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static dev.hytalemodding.beacon.BeaconBeamModels.BLOCK_UNITS;
import static dev.hytalemodding.beacon.BeaconBeamModels.HEIGHT_UNITS;
import static dev.hytalemodding.beacon.BeaconBeamModels.ORIGIN_UNITS;

/** Lee los cristales pequeños nativos y colorea el núcleo del haz sin cambiar sus reglas de colocación. */
public final class BeaconCrystals {
    private static final String TINT_PREFIX = "Beacon_Beam_Tint_";
    private static final ConcurrentHashMap<Integer, CompletableFuture<EntityEffect>> TINTS = new ConcurrentHashMap<>();

    private BeaconCrystals() {}

    /** Prepara colores nativos y su entrada desde blanco antes de abrir mundos, sin tocar sus bloques. */
    public static void loadPalette() {
        for (BlockType block : BlockType.getAssetMap().getAssetMap().values()) {
            Color color = crystalColor(block);
            if (color == null || "Rock_Crystal_White_Small".equals(block.getId())) continue;
            int rgb = ColorParseUtil.colorToARGBInt(color) & 0xFFFFFF;
            TINTS.put(rgb, CompletableFuture.completedFuture(publishTint(rgb)));
            for (int step = 0; step < 8; step++) {
                int intermediate = blend(0xFFFFFF, luminousColor(rgb), step * 2 + 1);
                TINTS.put(intermediate, CompletableFuture.completedFuture(publishTint(intermediate)));
            }
        }
    }

    /** Límites en treintaidosavos de bloque desde el origen del haz; color -1 conserva el blanco original. */
    record Span(int start, int end, int color) {}

    /** Recorre toda la columna cargada: los huecos y otros bloques no impiden encontrar cristales más altos. */
    static List<Span> trace(ChunkStore chunks, BeaconStructures.Position position) {
        List<Span> spans = new ArrayList<>();
        long red = 0, green = 0, blue = 0;
        int count = 0, start = 0, current = -1;
        boolean onlyWhite = true;
        for (int sectionY = ChunkUtil.indexSection(position.y() + 1); sectionY < ChunkUtil.HEIGHT / ChunkUtil.SIZE; sectionY++) {
            BlockSection section = section(chunks, position, sectionY);
            if (section == null) continue;
            for (int y = Math.max(position.y() + 1, ChunkUtil.minBlock(sectionY)); y < ChunkUtil.minBlock(sectionY + 1); y++) {
                BlockType block = BlockType.getAssetMap().getAsset(section.get(position.x(), y, position.z()));
                Color color = crystalColor(block);
                if (color == null) continue;
                red += Byte.toUnsignedInt(color.red);
                green += Byte.toUnsignedInt(color.green);
                blue += Byte.toUnsignedInt(color.blue);
                count++;
                onlyWhite &= "Rock_Crystal_White_Small".equals(block.getId());
                int next = onlyWhite ? -1 : (int) ((red / count) << 16 | (green / count) << 8 | blue / count);
                if (next == current) continue;
                int bottom = (y - position.y()) * BLOCK_UNITS - ORIGIN_UNITS;
                add(spans, start, bottom, current);
                // Ocho pasos dentro del bloque del cristal suavizan el cambio sin texturas por color.
                int from = current < 0 ? 0xFFFFFF : luminousColor(current);
                int to = next < 0 ? 0xFFFFFF : luminousColor(next);
                for (int step = 0; step < 8; step++) {
                    add(spans, bottom + step * 4, bottom + (step + 1) * 4, blend(from, to, step * 2 + 1));
                }
                start = bottom + BLOCK_UNITS;
                current = next;
            }
        }
        // La extensión superior es una sola pieza: dividirla crearía entidades fuera de la altura del mundo.
        spans.add(new Span(start, HEIGHT_UNITS, current));
        return List.copyOf(spans);
    }

    /** Lee solo secciones ya cargadas; también se usa para detectar cambios sin recorrer todos sus bloques. */
    static BlockSection section(ChunkStore chunks, BeaconStructures.Position position, int y) {
        var ref = chunks.getChunkSectionReferenceAtBlock(position.x(), ChunkUtil.minBlock(y), position.z());
        return ref == null || !ref.isValid() ? null : chunks.getStore().getComponent(ref, BlockSection.getComponentType());
    }

    private static int blend(int from, int to, int weight) {
        int red = (((from >> 16) & 255) * (16 - weight) + ((to >> 16) & 255) * weight) / 16;
        int green = (((from >> 8) & 255) * (16 - weight) + ((to >> 8) & 255) * weight) / 16;
        int blue = ((from & 255) * (16 - weight) + (to & 255) * weight) / 16;
        return luminousColor((red << 16) | (green << 8) | blue);
    }

    /** Descompone una longitud exacta en piezas reutilizables de alturas potencia de dos. */
    private static void add(List<Span> spans, int start, int end, int color) {
        while (start < end) {
            int length = Integer.highestOneBit(end - start);
            spans.add(new Span(start, start + length, color));
            start += length;
        }
    }

    /** Lleva el canal dominante del RGB a 255 conservando el tono para no apagar el bloom del haz. */
    static int luminousColor(int color) {
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        int maximum = Math.max(red, Math.max(green, blue));
        if (maximum == 0) return 0xFFFFFF;
        return ((red * 255 / maximum) << 16)
                | ((green * 255 / maximum) << 8) | (blue * 255 / maximum);
    }

    /** Solicita cada RGB una sola vez, sin bloquear el hilo del mundo durante su publicación. */
    static CompletableFuture<EntityEffect> tint(int color) {
        // World.tick mantiene el bloqueo de lectura de assets: publicarlos allí bloquearía su escritura.
        return TINTS.computeIfAbsent(color, rgb -> CompletableFuture.supplyAsync(() -> publishTint(rgb)));
    }

    private static EntityEffect publishTint(int color) {
        String pack = EntityEffect.getAssetMap().getAssetPack(BeaconPower.SPEED.effectId(1));
        String id = tintId(color);
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
        if (effect != null) return effect;
        String luminous = ColorParseUtil.toHexString(luminousColor(color));
        // El tinte de entidad aclara el blanco del haz; PostColor sí lo colorea y conserva parte de la textura.
        BsonDocument appearance = new BsonDocument("SwitchTo", new BsonString("PostColor"))
                .append("PostColor", new BsonString(luminous))
                .append("PostColorOpacity", new BsonDouble(0.85))
                // PostColor necesita un reloj válido; los efectos nativos persistentes usan duración y bucle.
                .append("AnimationDuration", new BsonDouble(1))
                .append("LoopOption", new BsonString("Loop"))
                .append("AnimationRange", new BsonDocument("X", new BsonDouble(1)).append("Y", new BsonDouble(1)))
                .append("HighlightThickness", new BsonDouble(0));
        var appearances = ModelVFX.getAssetStore();
        if (appearances.loadAssets(pack, List.of(appearances.decode(pack, id, appearance))).hasFailed()) {
            throw new IllegalStateException("Could not publish Beacon model tint " + id);
        }
        BsonDocument data = new BsonDocument("Infinite", BsonBoolean.TRUE)
                .append("ApplicationEffects", new BsonDocument("ModelVFXId", new BsonString(id)));
        var store = EntityEffect.getAssetStore();
        effect = store.decode(pack, id, data);
        // shortcut: Los tintes quedan registrados hasta reiniciar; revisar si se acumulan miles de mezclas distintas.
        if (store.loadAssets(pack, List.of(effect)).hasFailed()) {
            throw new IllegalStateException("Could not publish Beacon tint " + id);
        }
        return effect;
    }

    static String tintId(int color) {
        return TINT_PREFIX + "%06X".formatted(color);
    }

    /** La paleta inicial y el trazado usan la misma prioridad de color nativo. */
    @Nullable
    private static Color crystalColor(@Nullable BlockType block) {
        if (block == null || !block.getId().startsWith("Rock_Crystal_") || !block.getId().endsWith("_Small")) return null;
        Color color = block.getTextureComputedColor();
        return color != null ? color : block.getParticleColor();
    }
}
