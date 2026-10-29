package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;

/** Laboratorio aislado de partículas. No altera la representación normal del Beacon. */
public final class BeaconFxCommand extends AbstractCommandCollection {
    private static final float PREVIEW_SECONDS = 8f;
    private static final int[] RATIOS = {2, 4, 8, 16, 32, 48, 64, 80, 96, 128, 160, 256, 384, 512, 640};

    public BeaconFxCommand() {
        super("beaconfx", "Pruebas temporales de partículas para el Beacon");
        addSubCommand(new Test());
        addSubCommand(new Beam());
        addSubCommand(new Legendary());
        addSubCommand(new Center());
    }

    /** Traduce colores comunes o un hexadecimal; 'native' conserva el color definido por el asset. */
    static Color color(String input) {
        String name = input.toLowerCase(Locale.ROOT);
        String hex = switch (name) {
            case "native", "default" -> null;
            case "cyan" -> "49DFFF";
            case "purple" -> "B36BFF";
            case "red" -> "FF5050";
            case "green" -> "64F7A0";
            case "white" -> "FFFFFF";
            case "blue" -> "468DFF";
            case "yellow" -> "FFE055";
            default -> name.startsWith("#") ? name.substring(1) : name;
        };
        if (hex == null) return null;
        if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException(
                "Color no valido: usa cyan, purple, red, green, white, blue, yellow, native o RRGGBB");
        int rgb = Integer.parseInt(hex, 16);
        return new Color((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb);
    }

    /** Selecciona el recurso de geometría cuya relación alto/ancho se aproxima más a la solicitada. */
    static int ratio(double value) {
        int closest = RATIOS[0];
        for (int candidate : RATIOS) {
            if (Math.abs(candidate - value) < Math.abs(closest - value)) closest = candidate;
        }
        return closest;
    }

    /** Envía una prueba solo al jugador que la solicita; el cliente la descarta al expirar. */
    private static void spawn(String asset, Vector3d position, float scale, Color color,
                              Ref<EntityStore> player, Store<EntityStore> store) {
        if (ParticleSystem.getAssetMap().getAsset(asset) == null) throw new IllegalArgumentException(
                "No existe el ParticleSystem: " + asset);
        ParticleUtil.spawnParticleEffect(asset, position.x, position.y, position.z,
                0f, 0f, 0f, scale, color, null, List.of(player), store, PREVIEW_SECONDS);
    }

    /** Dibuja un segmento vertical usando la textura Glow nativa, con la altura aproximada. */
    private static float rayPart(float height, float width, Vector3d middle, Color tint,
                                 Ref<EntityStore> player, Store<EntityStore> store) {
        int selectedRatio = ratio(height / width);
        spawn("Beacon_FX_Ray_" + selectedRatio, middle, width, tint, player, store);
        return selectedRatio * width;
    }

    /** Separa base y columna. El nucleo superpuesto mantiene mas brillo en la zona central. */
    private static float previewRay(float height, float width, Color tint, boolean decoration,
                                    Ref<EntityStore> player, Store<EntityStore> store) {
        Vector3d position = store.getComponent(player, TransformComponent.getComponentType()).getPosition();
        int selectedRatio = ratio(height / width);
        float actualHeight = selectedRatio * width;
        if (decoration) {
            // La base coloreable es independiente del rayo: nunca se escala con su altura.
            spawn("Beacon_FX_Tinted_Base", new Vector3d(position), 1f, tint, player, store);
        }
        double bottom = position.y + 0.05;
        rayPart(actualHeight, width, new Vector3d(position.x, bottom + actualHeight / 2d, position.z),
                tint, player, store);
        if (decoration) {
            // Glow se desvanece en los extremos; capas mas cortas se solapan para reforzar el recorrido.
            float innerWidth = Math.max(width * 0.65f, actualHeight / 640f);
            rayPart(actualHeight * 0.92f, innerWidth,
                    new Vector3d(position.x, bottom + actualHeight * 0.5d, position.z), tint, player, store);

            float sectionHeight = actualHeight * 0.58f;
            float sectionWidth = Math.max(width * 0.48f, sectionHeight / 640f);
            for (double fraction : new double[]{0.32, 0.68}) {
                rayPart(sectionHeight, sectionWidth,
                        new Vector3d(position.x, bottom + actualHeight * fraction, position.z), tint, player, store);
            }
        }
        return actualHeight;
    }

    /** Rechaza tamaños no finitos o fuera de los límites de pruebas. */
    private static boolean validDimensions(float height, float width) {
        return Float.isFinite(height) && Float.isFinite(width) &&
                height >= 4f && height <= 128f && width >= 0.2f && width <= 2f;
    }

    /** Prueba el sistema nativo completo con escalado uniforme; no modifica sus assets. */
    private static final class Test extends AbstractPlayerCommand {
        private final RequiredArg<String> effect = withRequiredArg("effect", "ID nativo, por ejemplo Drop_Legendary", ArgTypes.STRING);
        private final RequiredArg<Float> scale = withRequiredArg("scale", "Escala uniforme de 0.1 a 8", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color, hexadecimal o native", ArgTypes.STRING);

        Test() {
            super("test", "Prueba un efecto original con escala y color");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float size = scale.get(context);
            if (!Float.isFinite(size) || size < 0.1f || size > 8f) {
                context.sendMessage(Message.raw("Escala permitida: 0.1 a 8."));
                return;
            }
            try {
                Vector3d position = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
                spawn(effect.get(context), new Vector3d(position), size, color(tint.get(context)), ref, store);
                context.sendMessage(Message.raw("Efecto original a escala uniforme: tambien crecen los adornos. "
                        + "Su color depende de los ajustes de cada emisor nativo."));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

    /** Haz de un solo emisor con textura Glow nativa y proporciones verticales precalculadas. */
    private static final class Beam extends AbstractPlayerCommand {
        private final RequiredArg<Float> height = withRequiredArg("height", "Altura en bloques (4 a 128)", ArgTypes.FLOAT);
        private final RequiredArg<Float> width = withRequiredArg("width", "Anchura en bloques (0.2 a 2)", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color o hexadecimal", ArgTypes.STRING);

        Beam() {
            super("beam", "Previsualiza un rayo vertical de partículas con textura nativa Glow");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float h = height.get(context);
            float w = width.get(context);
            if (!validDimensions(h, w)) {
                context.sendMessage(Message.raw("Altura: 4 a 128; anchura: 0.2 a 2 bloques."));
                return;
            }
            try {
                float actualHeight = previewRay(h, w, color(tint.get(context)), false, ref, store);
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "Rayo solo: alto aprox. %.1f, ancho %.2f, color solicitado %s (8 s).",
                        actualHeight, w, tint.get(context))));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }
    /** Usa base recoloreable y un nucleo mas intenso que la prueba de rayo aislado. */
    private static final class Legendary extends AbstractPlayerCommand {
        private final RequiredArg<Float> height = withRequiredArg("height", "Altura del rayo (4 a 128)", ArgTypes.FLOAT);
        private final RequiredArg<Float> width = withRequiredArg("width", "Anchura del rayo (0.2 a 2)", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color del rayo o hexadecimal", ArgTypes.STRING);

        Legendary() {
            super("legendary", "Base recoloreable y rayo de Glow con nucleo reforzado");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float h = height.get(context);
            float w = width.get(context);
            if (!validDimensions(h, w)) {
                context.sendMessage(Message.raw("Altura: 4 a 128; anchura: 0.2 a 2 bloques."));
                return;
            }
            try {
                Color color = color(tint.get(context));
                float actualHeight = previewRay(h, w, color, true, ref, store);
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "Base recoloreable con rayo reforzado %.1f x %.2f, color %s (8 s).",
                        actualHeight, w, tint.get(context))));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

    /** Comparacion: reutiliza el Ray_Center original, sin modificar su recurso. */
    private static final class Center extends AbstractPlayerCommand {
        private final RequiredArg<Float> scale = withRequiredArg("scale", "Escala uniforme del rayo original (0.1 a 8)", ArgTypes.FLOAT);

        Center() {
            super("center", "Prueba aislada del Drop_Legendary_Ray_Center original");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float size = scale.get(context);
            if (!Float.isFinite(size) || size < 0.1f || size > 8f) {
                context.sendMessage(Message.raw("Escala permitida: 0.1 a 8."));
                return;
            }
            try {
                Vector3d position = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
                spawn("Beacon_FX_Native_Ray_Center", new Vector3d(position), size, null, ref, store);
                context.sendMessage(Message.raw("Ray_Center original: escala uniforme. No se ha cambiado su color ni su proporcion."));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

}
