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
    private static final float POLISHED_HEIGHT = 40f;
    private static final float POLISHED_WIDTH = 0.65f;
    private static final float POLISHED_OFFSET = -5f;
    private static final float POLISHED_BASE_SCALE = 0.85f;
    private static final int[] RATIOS = {16, 32, 48, 64, 80, 96, 128, 160, 256, 384, 512, 640};

    public BeaconFxCommand() {
        super("beaconfx", "Pruebas temporales de partículas para el Beacon");
        addSubCommand(new Test());
        addSubCommand(new Beam());
        addSubCommand(new Legendary());
        addSubCommand(new Align());
        addSubCommand(new Center());
        addSubCommand(new Polish());
        addSubCommand(new Tune());
        addSubCommand(new Custom());
        addSubCommand(new CustomAlign());
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

    /** Centro propuesto del sprite, ajustable para corregir su anclaje sin cambiar recursos. */
    static double centerY(double groundY, float height, float verticalOffset) {
        return groundY + 0.05d + height / 2d + verticalOffset;
    }

    /** Vista habitual: conserva el anclaje original como referencia visual. */
    private static float previewRay(float height, float width, Color tint, boolean decoration,
                                    Ref<EntityStore> player, Store<EntityStore> store) {
        return previewRay(height, width, tint, decoration, 0f, 1f, player, store);
    }

    /** Vista con ajuste vertical, conservando la escala de base original. */
    private static float previewRay(float height, float width, Color tint, boolean decoration,
                                    float verticalOffset, Ref<EntityStore> player, Store<EntityStore> store) {
        return previewRay(height, width, tint, decoration, verticalOffset, 1f, player, store);
    }

    /** Mantiene independientes la escala de la base, el grosor del rayo y el anclaje vertical. */
    private static float previewRay(float height, float width, Color tint, boolean decoration,
                                    float verticalOffset, float baseScale, Ref<EntityStore> player, Store<EntityStore> store) {
        int selectedRatio = ratio(height / width);
        float actualHeight = selectedRatio * width;
        Vector3d position = store.getComponent(player, TransformComponent.getComponentType()).getPosition();
        if (decoration) {
            // Los adornos de Drop_Legendary mantienen su escala original, sin los dos rayos vanilla.
            String base = tint != null && BeaconFxAssets.available()
                    ? BeaconFxAssets.TINTED_BASE : "Beacon_FX_Legendary_Base";
            spawn(base, new Vector3d(position), baseScale, tint, player, store);
        }
        Vector3d middle = new Vector3d(position.x, centerY(position.y, actualHeight, verticalOffset), position.z);
        spawn("Beacon_FX_Ray_" + selectedRatio, middle, width, tint, player, store);
        return actualHeight;
    }

    /** Mensaje de diagnóstico si falló la carga de la variante recoloreable. */
    private static void reportTintStatus(CommandContext context, Color tint) {
        if (tint != null && !BeaconFxAssets.available()) {
            context.sendMessage(Message.raw("La base personalizada no se pudo cargar; se usa la amarilla original. Revisa el log."));
        }
    }

    /** Rango de escala prudente para que el anillo no tape el haz ni crezca sin control. */
    static boolean validBaseScale(float baseScale) {
        return Float.isFinite(baseScale) && baseScale >= 0.5f && baseScale <= 1.5f;
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
    /** Reproduce la base legendaria a escala normal y dibuja solamente el rayo en grande. */
    private static final class Legendary extends AbstractPlayerCommand {
        private final RequiredArg<Float> height = withRequiredArg("height", "Altura del rayo (4 a 128)", ArgTypes.FLOAT);
        private final RequiredArg<Float> width = withRequiredArg("width", "Anchura del rayo (0.2 a 2)", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color del rayo o hexadecimal", ArgTypes.STRING);

        Legendary() {
            super("legendary", "Base de Drop_Legendary sin escalar y rayo luminoso escalado por separado");
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
                reportTintStatus(context, color);
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "Base legendaria al 100%%, rayo %.1f x %.2f, color %s (8 s).",
                        actualHeight, w, tint.get(context))));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

    /** Ajusta el anclaje del rayo en bloques, para calibrarlo dentro del cliente. */
    private static final class Align extends AbstractPlayerCommand {
        private final RequiredArg<Float> height = withRequiredArg("height", "Altura solicitada de 4 a 128", ArgTypes.FLOAT);
        private final RequiredArg<Float> width = withRequiredArg("width", "Anchura de 0.2 a 2", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color o hexadecimal", ArgTypes.STRING);
        private final RequiredArg<Float> offset = withRequiredArg("offset", "Desplazamiento Y en bloques, p.ej. -8", ArgTypes.FLOAT);

        Align() {
            super("align", "Ajusta la altura visual del rayo sin agrandar su base");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float h = height.get(context);
            float w = width.get(context);
            float shift = offset.get(context);
            if (!validDimensions(h, w) || !Float.isFinite(shift) || Math.abs(shift) > 128f) {
                context.sendMessage(Message.raw("Altura: 4-128, anchura: 0.2-2, offset: -128 a +128."));
                return;
            }
            try {
                Color selectedColor = color(tint.get(context));
                float actual = previewRay(h, w, selectedColor, true, shift, ref, store);
                reportTintStatus(context, selectedColor);
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "Anclaje: altura solicitada %.1f, altura estimada %.1f, offset Y %+.1f. "
                                + "Es una calibracion visual, no modifica los assets.", h, actual, shift)));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

    /** Compara el emisor original de Hytale, sin alterar textura ni animaciones. */
    private static final class Center extends AbstractPlayerCommand {
        private final RequiredArg<Float> scale = withRequiredArg("scale", "Escala uniforme, 0.1 a 8", ArgTypes.FLOAT);

        Center() {
            super("center", "Muestra solo Drop_Legendary_Ray_Center tal como lo dibuja Hytale");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float size = scale.get(context);
            if (!Float.isFinite(size) || size < 0.1f || size > 8f) {
                context.sendMessage(Message.raw("Escala permitida: 0.1 a 8."));
                return;
            }
            Vector3d position = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
            try {
                spawn("Beacon_FX_Native_Ray_Center", new Vector3d(position), size, null, ref, store);
                context.sendMessage(Message.raw("Rayo central original: escala uniforme, sin tintado forzado."));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

    /** Preset conservador: brillo mayor, base un poco menor y menos rayo bajo el suelo. */
    private static final class Polish extends AbstractPlayerCommand {
        private final RequiredArg<String> tint = withRequiredArg("color", "Color o hexadecimal", ArgTypes.STRING);

        Polish() {
            super("polish", "Prueba el acabado afinado, conservando el estilo anterior");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            try {
                Color selected = color(tint.get(context));
                float actual = previewRay(POLISHED_HEIGHT, POLISHED_WIDTH, selected, true,
                        POLISHED_OFFSET, POLISHED_BASE_SCALE, ref, store);
                reportTintStatus(context, selected);
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "Acabado: haz ~%.1f alto / %.2f ancho; offset %+.1f; base %.2f (8 s).",
                        actual, POLISHED_WIDTH, POLISHED_OFFSET, POLISHED_BASE_SCALE)));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

    /** Controles independientes para comparar ajustes sin recompilar el mod. */
    private static final class Tune extends AbstractPlayerCommand {
        private final RequiredArg<Float> height = withRequiredArg("height", "Altura de 4 a 128 bloques", ArgTypes.FLOAT);
        private final RequiredArg<Float> width = withRequiredArg("width", "Anchura de 0.2 a 2 bloques", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color o hexadecimal", ArgTypes.STRING);
        private final RequiredArg<Float> offset = withRequiredArg("offset", "Desplazamiento vertical de -128 a 128", ArgTypes.FLOAT);
        private final RequiredArg<Float> baseScale = withRequiredArg("baseScale", "Escala independiente de base: 0.5 a 1.5", ArgTypes.FLOAT);

        Tune() {
            super("tune", "Ajusta altura y anchura del haz, desplazamiento y tamaño de la base");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            float h = height.get(context);
            float w = width.get(context);
            float shift = offset.get(context);
            float base = baseScale.get(context);
            if (!validDimensions(h, w) || !validBaseScale(base)
                    || !Float.isFinite(shift) || Math.abs(shift) > 128f) {
                context.sendMessage(Message.raw("Altura 4-128, ancho 0.2-2, offset -128..128, base 0.5-1.5."));
                return;
            }
            try {
                Color selected = color(tint.get(context));
                float actual = previewRay(h, w, selected, true, shift, base, ref, store);
                reportTintStatus(context, selected);
                context.sendMessage(Message.raw(String.format(Locale.ROOT,
                        "Ajuste: haz solicitado %.1f (real aprox. %.1f), ancho %.2f, offset %+.1f, base %.2f (8 s).",
                        h, actual, w, shift, base)));
            } catch (IllegalArgumentException error) {
                context.sendMessage(Message.raw(error.getMessage()));
            }
        }
    }

    /** Usa los perfiles Java sin offsets manuales y conserva el anillo coloreado original. */
    static float automaticOffset(float actualHeight) {
        return -actualHeight / 8f;
    }

    /** Selecciona los assets publicados una sola vez al iniciar el plugin. */
    private static void previewCustom(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player,
                                      float height, float width, String colorName, float brightness,
                                      float baseScale, float rotationSpeed, Float requestedOffset) {
        if (!validDimensions(height, width) || !validBaseScale(baseScale)
                || !BeaconFxAssets.validRotationSpeed(rotationSpeed)) {
            context.sendMessage(Message.raw("Altura 4-128, ancho 0.2-2, base 0.5-1.5, giro 0.25-1."));
            return;
        }
        if (!BeaconFxProfiles.available()) {
            context.sendMessage(Message.raw("Los perfiles Java no se cargaron. Revisa el log del servidor; sigue disponible /beaconfx tune."));
            return;
        }
        try {
            Color tint = color(colorName);
            BeaconFxProfiles.Profile profile = BeaconFxProfiles.select(height, width, brightness);
            float actualHeight = profile.heightAt(width);
            float offset = requestedOffset == null ? automaticOffset(actualHeight) : requestedOffset;
            if (!Float.isFinite(offset) || Math.abs(offset) > 128f) {
                context.sendMessage(Message.raw("Offset vertical permitido: -128 a 128 bloques."));
                return;
            }
            Vector3d pos = store.getComponent(player, TransformComponent.getComponentType()).getPosition();
            String base = tint != null ? BeaconFxAssets.tintedBase(rotationSpeed) : "Beacon_FX_Legendary_Base";
            spawn(base, new Vector3d(pos), baseScale, tint, player, store);
            Vector3d center = new Vector3d(pos.x, centerY(pos.y, actualHeight, offset), pos.z);
            spawn(profile.assetId(), center, width, tint, player, store);
            reportTintStatus(context, tint);
            context.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "Java FX: alto %.2f (pedido %.2f), ancho %.2f, brillo %.2f, base %.2f, giro pedido %.2f, offset %+.2f.",
                    actualHeight, height, width, profile.brightness(), baseScale, rotationSpeed, offset)));
        } catch (IllegalArgumentException error) {
            context.sendMessage(Message.raw(error.getMessage()));
        }
    }

    /** Vista parametrizada con desplazamiento automático hacia la base. */
    private static final class Custom extends AbstractPlayerCommand {
        private final RequiredArg<Float> height = withRequiredArg("height", "Altura entre 4 y 128", ArgTypes.FLOAT);
        private final RequiredArg<Float> width = withRequiredArg("width", "Anchura entre 0.2 y 2", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color o RRGGBB", ArgTypes.STRING);
        private final RequiredArg<Float> brightness = withRequiredArg("brightness", "Brillo entre 0.5 y 1", ArgTypes.FLOAT);
        private final RequiredArg<Float> base = withRequiredArg("baseScale", "Tamano de base entre 0.5 y 1.5", ArgTypes.FLOAT);
        private final RequiredArg<Float> speed = withRequiredArg("rotationSpeed", "Velocidad del anillo entre 0.25 y 1", ArgTypes.FLOAT);

        Custom() {
            super("custom", "Prueba un haz creado desde Java con brillo y giro configurables");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            previewCustom(context, store, ref, height.get(context), width.get(context), tint.get(context),
                    brightness.get(context), base.get(context), speed.get(context), null);
        }
    }

    /** Mismos parámetros, con control explícito del anclaje vertical para comparar visualmente. */
    private static final class CustomAlign extends AbstractPlayerCommand {
        private final RequiredArg<Float> height = withRequiredArg("height", "Altura entre 4 y 128", ArgTypes.FLOAT);
        private final RequiredArg<Float> width = withRequiredArg("width", "Anchura entre 0.2 y 2", ArgTypes.FLOAT);
        private final RequiredArg<String> tint = withRequiredArg("color", "Color o RRGGBB", ArgTypes.STRING);
        private final RequiredArg<Float> brightness = withRequiredArg("brightness", "Brillo entre 0.5 y 1", ArgTypes.FLOAT);
        private final RequiredArg<Float> base = withRequiredArg("baseScale", "Tamano de base entre 0.5 y 1.5", ArgTypes.FLOAT);
        private final RequiredArg<Float> speed = withRequiredArg("rotationSpeed", "Velocidad del anillo entre 0.25 y 1", ArgTypes.FLOAT);
        private final RequiredArg<Float> offset = withRequiredArg("offset", "Desplazamiento vertical -128..128", ArgTypes.FLOAT);

        CustomAlign() {
            super("customalign", "Como custom, pero con offset vertical elegido por el jugador");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            previewCustom(context, store, ref, height.get(context), width.get(context), tint.get(context),
                    brightness.get(context), base.get(context), speed.get(context), offset.get(context));
        }
    }

}
