package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.assetstore.AssetLoadResult;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.InteractionTypeUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import org.bson.*;

import java.util.*;

/**
 * Genera variantes de ataques y animaciones bajo el prefijo {@code Beacon_Haste_}.
 * Conserva los assets originales y la identidad de los cooldowns; publica todos
 * los pasos antes de compilar las raíces para resolver referencias entre ataques.
 */
final class BeaconHasteAssets {
    static final String PREFIX = "Beacon_Haste_";
    // Campos de referencias de los codecs Interaction/RootInteraction y sus variantes en 0.7.4.
    private static final Set<String> REFERENCES = Set.of("Interactions", "Next", "Failed", "DefaultValue", "HitEntity",
            "HitBlock", "ForkInteractions", "Forks", "Blocked", "GroundNext", "CollisionNext", "Click", "Held",
            "Forward", "Back", "Left", "Right", "ForwardLeft", "ForwardRight", "BackLeft", "BackRight", "Flags");
    private final Map<String, String> primaryByItem = new HashMap<>();
    private final Set<String> animations = new HashSet<>();
    private final Map<String, BsonDocument> roots = new LinkedHashMap<>();
    private final Map<String, BsonDocument> steps = new LinkedHashMap<>();
    private int inlineId;
    private final String prefix;
    private final float speed;

    BeaconHasteAssets(int strength) {
        prefix = PREFIX + (strength == 2 ? "II_" : "");
        speed = BeaconRules.attackSpeed(strength);
    }

    /** @return raíz acelerada del objeto, o {@code null} si no tiene una variante aplicable */
    String primary(String item) {
        return primaryByItem.get(item);
    }

    /**
     * Genera las variantes tras la carga nativa y libera los documentos si se publican correctamente.
     * @throws IllegalStateException si falla la publicación de animaciones, pasos o raíces
     */
    void load() {
        String pack = Item.getAssetMap().getAssetPack("Beacon_Block");
        // shortcut: Variants are built at startup; rebuild them if live asset editing is added.
        for (Item item : List.copyOf(Item.getAssetMap().getAssetMap().values())) {
            String root = item.getInteractions().get(InteractionType.Primary);
            String family = item.getPlayerAnimationsId();
            if (root != null && family != null && (item.getWeapon() != null || item.getTool() != null)) {
                primaryByItem.put(item.getId(), root(root, family));
            }
        }
        // Publish both namespaces before compiling roots; attacks can fork and call each other.
        var newSteps = steps.entrySet().stream().map(e -> Interaction.getAssetStore().decode(pack, e.getKey(), e.getValue())).toList();
        requireLoaded(Interaction.getAssetStore().loadAssets(pack, newSteps), "haste interactions " + prefix);
        var newRoots = roots.entrySet().stream().map(e -> RootInteraction.getAssetStore().decode(pack, e.getKey(), e.getValue())).toList();
        requireLoaded(RootInteraction.getAssetStore().loadAssets(pack, newRoots), "haste roots " + prefix);
        roots.clear();
        steps.clear();
    }

    private String root(String id, String family) {
        String key = prefix + family + "_" + id;
        if (!roots.containsKey(key)) {
            RootInteraction asset = RootInteraction.getAssetMap().getAsset(id);
            if (asset == null) {
                throw new IllegalArgumentException("Missing attack root: " + id);
            }
            BsonDocument document = RootInteraction.CODEC.encode(asset, new ExtraInfo()).asDocument();
            BsonDocument cooldown = document.getDocument("Cooldown", null);
            if (cooldown == null) {
                cooldown = new BsonDocument("Cooldown", new BsonDouble(InteractionTypeUtils.getDefaultCooldown(InteractionType.Primary)));
                document.put("Cooldown", cooldown);
            }
            // Preserve cooldown identity when entering/leaving range instead of granting a fresh attack.
            cooldown.putIfAbsent("Id", new BsonString(id));
            roots.put(key, document);
            transform(document, family, false);
        }
        return key;
    }

    private String step(String id, String family) {
        String key = prefix + family + "_" + id;
        if (!steps.containsKey(key)) {
            Interaction asset = Interaction.getAssetMap().getAsset(id);
            if (asset == null) throw new IllegalArgumentException("Missing attack step: " + id);
            BsonDocument document = Interaction.CODEC.encode(asset, new ExtraInfo()).asDocument();
            steps.put(key, document);
            transform(document, family, false);
        }
        return key;
    }

    private String animation(String id) {
        String pack = Item.getAssetMap().getAssetPack("Beacon_Block");
        String key = prefix + id;
        if (animations.add(id)) {
            ItemPlayerAnimations asset = ItemPlayerAnimations.getAssetMap().getAsset(id);
            if (asset == null) throw new IllegalArgumentException("Missing attack animation: " + id);
            BsonDocument document = ItemPlayerAnimations.CODEC.encode(asset, new ExtraInfo()).asDocument();
            document.remove("Id");
            document.remove("Parent");
            for (BsonValue value : document.getDocument("Animations").values()) {
                BsonDocument entry = value.asDocument();
                double speed = entry.getNumber("Speed", new BsonDouble(1)).doubleValue();
                entry.put("Speed", new BsonDouble(speed * this.speed));
            }
            var faster = ItemPlayerAnimations.getAssetStore().decode(pack, key, document);
            requireLoaded(ItemPlayerAnimations.getAssetStore().loadAssets(pack, List.of(faster)), "haste animation " + key);
        }
        return key;
    }

    private static void requireLoaded(AssetLoadResult<?, ?> result, String phase) {
        if (result.hasFailed()) {
            throw new IllegalStateException("Could not publish " + phase + ": failed keys="
                    + result.getFailedToLoadKeys() + "; failed paths=" + result.getFailedToLoadPaths()
                    + "; check native asset logs for child failures");
        }
    }

    /**
     * Reescribe referencias y tiempos sin modificar los assets originales.
     * @param value documento o valor generado, que puede modificarse durante el recorrido
     * @param family familia de animación del objeto
     * @param referenceContext indica si las cadenas son referencias, incluso dentro de mapas auxiliares
     * @return valor transformado, o el ID generado si contenía un asset inline
     */
    private BsonValue transform(BsonValue value, String family, boolean referenceContext) {
        if (referenceContext && value.isDocument()
                && (value.asDocument().containsKey("Type") || value.asDocument().containsKey("Interactions"))) {
            String key = prefix + family + "_Inline_" + inlineId++;
            BsonDocument document = value.asDocument();
            // Registrar antes de descender corta ciclos y mantiene estables los IDs compartidos.
            (document.containsKey("Type") ? steps : roots).put(key, document);
            transform(document, family, false);
            return new BsonString(key);
        }
        if (value.isString() && referenceContext) {
            return resolveReference(value.asString(), family);
        }
        if (value.isArray()) {
            BsonArray array = value.asArray();
            for (int i = 0; i < array.size(); i++) {
                array.set(i, transform(array.get(i), family, referenceContext));
            }
        } else if (value.isDocument()) {
            transformDocument(value.asDocument(), family, referenceContext);
        }
        return value;
    }

    private void transformDocument(BsonDocument document, String family, boolean referenceContext) {
        boolean asset = document.containsKey("Type") || document.containsKey("Interactions");
        if (asset) {
            document.remove("Id");
            document.remove("Parent");
        }
        for (String key : List.copyOf(document.keySet())) {
            document.put(key, transformField(key, document.get(key), family, referenceContext && !asset));
        }
        BsonDocument effects = document.getDocument("Effects", null);
        if (effects != null && effects.containsKey("ItemAnimationId")) {
            String original = effects.getString("ItemPlayerAnimationsId", new BsonString(family)).getValue();
            effects.put("ItemPlayerAnimationsId", new BsonString(animation(original)));
        }
        if (document.getString("Type", new BsonString("")).getValue().equals("Charging")) {
            BsonDocument thresholds = document.getDocument("Next", null);
            if (thresholds != null) {
                // ChargingInteraction.Next usa umbrales float; conservar su precisión al cambiar las claves.
                BsonDocument faster = new BsonDocument();
                thresholds.forEach((time, next) -> faster.put(Float.toString(Float.parseFloat(time) / speed), next));
                document.put("Next", faster);
            }
        }
    }

    /** Escala segundos de Interaction, RootInteraction.COOLDOWN_CODEC e InteractionEffects. */
    private BsonValue transformField(String key, BsonValue value, String family, boolean referenceContext) {
        if ((key.equals("RunTime") || key.equals("Cooldown") || key.equals("StartDelay")) && value.isNumber()) {
            return new BsonDouble(value.asNumber().doubleValue() / speed);
        }
        if (key.equals("Charges") && value.isArray()) {
            BsonArray charges = new BsonArray();
            value.asArray().forEach(charge -> charges.add(new BsonDouble(charge.asNumber().doubleValue() / speed)));
            return charges;
        }
        return transform(value, family, REFERENCES.contains(key) || referenceContext);
    }

    /** Un mismo ID puede existir en ambos registros; las referencias desconocidas se conservan. */
    private BsonString resolveReference(BsonString value, String family) {
        String id = value.getValue();
        boolean found = false;
        if (RootInteraction.getAssetMap().getAsset(id) != null) {
            root(id, family);
            found = true;
        }
        if (Interaction.getAssetMap().getAsset(id) != null) {
            step(id, family);
            found = true;
        }
        return found ? new BsonString(prefix + family + "_" + id) : value;
    }
}
