package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.assetstore.AssetLoadResult;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.RawAsset;
import com.hypixel.hytale.assetstore.codec.ContainedAssetCodec;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Caracteriza los documentos de Prisa con BSON y registros reales, sin ampliar la API del mod. */
final class BeaconHasteAssetsChecks {
    private static final String PACK = Item.getAssetMap().getAssetPack("Beacon_Block");
    private final BeaconHasteAssets assets;
    private final Method transform;
    private final String prefix;
    private final float speed;

    private BeaconHasteAssetsChecks(int rank) throws ReflectiveOperationException {
        assets = new BeaconHasteAssets(rank);
        prefix = "Beacon_Haste_" + (rank == 2 ? "II_" : "");
        speed = BeaconRules.attackSpeed(rank);
        // Solo la entrada BSON y sus documentos de salida; los helpers extraídos siguen siendo privados.
        transform = BeaconHasteAssets.class.getDeclaredMethod("transform", BsonValue.class, String.class, boolean.class);
        transform.setAccessible(true);
    }

    static void run(List<String> results) throws ReflectiveOperationException {
        registerReferences();
        for (int rank = 1; rank <= 2; rank++) {
            var checks = new BeaconHasteAssetsChecks(rank);
            checks.referencesAndCycles();
            checks.timingsAndInlineAssets();
        }
        results.add("Haste documents I/II: shared/cyclic references, separate root/step namespaces, inline IDs, unknown references, timings, charges, thresholds and animations preserved");
        publicationFailures();
        results.add("Asset failures: native incompatible interaction rejected; publication guard aborts on direct/child failures; missing steps and animations identify their IDs");
    }

    /** Comprueba variantes publicadas, originales intactos y sustitución reversible del ataque. */
    static void checkLoadedVariants(Store<EntityStore> store, Ref<EntityStore> actor, Interaction fastMine) {
        check(Math.abs(fastMine.getRunTime() - 0.083f/1.4f) < 0.00001f, "Mining interaction timing not accelerated");
        var fastAnimation = ItemPlayerAnimations.getAssetMap().getAsset("Beacon_Haste_Pickaxe");
        check(fastAnimation != null && Math.abs(fastAnimation.toPacket().animations.get("Mine").speed - 1.4f) < 0.00001f, "Mining animation not accelerated");
        check(Math.abs(Interaction.getAssetMap().getAsset("Pickaxe_Mine").getRunTime()-0.083f) < 0.00001f, "Original attack timing was modified globally");
        check(ItemPlayerAnimations.getAssetMap().getAsset("Pickaxe").toPacket().animations.get("Mine").speed == 1f, "Original animation was modified globally");
        check(RootInteraction.getAssetMap().getAsset("Beacon_Haste_Sword_" + Item.getAssetMap().getAsset("Weapon_Sword_Iron").getInteractions().get(InteractionType.Primary)) != null, "Basic sword attack root not generated");
        for (var entry : Interaction.getAssetMap().getAssetMap().entrySet()) {
            if (entry.getKey().contains("Beacon_Haste_")) {
                String json = Interaction.CODEC.encode(entry.getValue(), new ExtraInfo()).toString();
                check(!json.contains("Missing interaction:"), "Unresolved fast interaction: " + entry.getKey());
            }
        }
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            String original = item.getInteractions().get(InteractionType.Primary);
            String family = item.getPlayerAnimationsId();
            if (original != null && family != null && (item.getWeapon()!=null || item.getTool()!=null)) {
                RootInteraction normal = RootInteraction.getAssetMap().getAsset(original);
                RootInteraction fast = RootInteraction.getAssetMap().getAsset("Beacon_Haste_"+family+"_"+original);
                check(fast != null && normal.getOperationMax() == fast.getOperationMax(), "Fast primary lost operations: " + item.getId());
            }
        }
        String fastRoot = "Beacon_Haste_Pickaxe_Pickaxe_Attack";
        var fastCooldown = RootInteraction.getAssetMap().getAsset(fastRoot).getCooldown();
        check(fastCooldown != null && Math.abs(fastCooldown.cooldown-0.35f/1.4f)<0.00001f, "Default cooldown still limits attack speed");
        check("Pickaxe_Attack".equals(fastCooldown.cooldownId), "Fast and normal attacks do not share their cooldown");
        BeaconAttackSpeedSystem.updatePrimary(actor, store, fastRoot);
        check(fastRoot.equals(store.getComponent(actor, Interactions.getComponentType()).getInteractionId(InteractionType.Primary)), "Fast attack override not installed");
        BeaconAttackSpeedSystem.updatePrimary(actor, store, null);
        check(store.getComponent(actor, Interactions.getComponentType()).getInteractionId(InteractionType.Primary) == null, "Fast attack override not restored");
    }

    private static void registerReferences() {
        var store = Interaction.getAssetStore();
        var first = store.decode(PACK, "Probe_Beacon_CycleA", BsonDocument.parse("""
                {"Type":"Simple","RunTime":0.6,"Next":"Probe_Beacon_CycleB"}
                """));
        var second = store.decode(PACK, "Probe_Beacon_CycleB", BsonDocument.parse("""
                {"Type":"Simple","Next":"Probe_Beacon_CycleA"}
                """));
        var terminal = store.decode(PACK, "Probe_Beacon_Terminal", BsonDocument.parse("{\"Type\":\"Simple\",\"RunTime\":0.3}"));
        check(!store.loadAssets(PACK, List.of(first, second, terminal)).hasFailed(), "Could not register native cycle fixture");
        check(!RootInteraction.getAssetStore().loadAssets(PACK,
                List.of(new RootInteraction("Probe_Beacon_CycleA", "Probe_Beacon_Terminal"))).hasFailed(), "Could not register native root fixture");
    }

    private void referencesAndCycles() throws ReflectiveOperationException {
        String id = prefix + "Pickaxe_Probe_Beacon_CycleA";
        var source = Interaction.CODEC.encode(Interaction.getAssetMap().getAsset("Probe_Beacon_CycleA"), new ExtraInfo());
        var references = BsonDocument.parse("""
                {"Next":["Probe_Beacon_CycleA","Probe_Beacon_CycleA","Probe_Beacon_Unknown"],
                 "Label":"Probe_Beacon_CycleA"}
                """);
        rewrite(references, false);
        var next = references.getArray("Next");
        check(next.get(0).equals(new BsonString(id)) && next.get(0).equals(next.get(1)), "Shared reference lost stable ID");
        check(next.get(2).equals(new BsonString("Probe_Beacon_Unknown")), "Unknown reference was rewritten");
        check(references.getString("Label").getValue().equals("Probe_Beacon_CycleA"), "Ordinary string was treated as an asset reference");
        var steps = documents("steps");
        var roots = documents("roots");
        check(steps.size() == 3 && roots.size() == 1 && steps.containsKey(id) && roots.containsKey(id), "Root/step namespaces were merged or cycle repeated");
        check(steps.get(id).getString("Next").getValue().equals(prefix + "Pickaxe_Probe_Beacon_CycleB"), "Cycle forward reference changed");
        check(steps.get(prefix + "Pickaxe_Probe_Beacon_CycleB").getString("Next").getValue().equals(id), "Cycle return reference changed");
        check(source.equals(Interaction.CODEC.encode(Interaction.getAssetMap().getAsset("Probe_Beacon_CycleA"), new ExtraInfo())), "Original interaction was modified");
    }

    private void timingsAndInlineAssets() throws ReflectiveOperationException {
        var originalAnimation = ItemPlayerAnimations.CODEC.encode(ItemPlayerAnimations.getAssetMap().getAsset("Pickaxe"), new ExtraInfo());
        var document = BsonDocument.parse("""
                {"Type":"Charging","Id":"Original","Parent":"OriginalParent","RunTime":0.6,
                 "Cooldown":{"Id":"SharedCooldown","Cooldown":1.2,"Charges":[0.3,0.9]},
                 "Effects":{"ItemAnimationId":"Mine","StartDelay":0.24},
                 "Next":{"0.6":{"Type":"Simple","RunTime":0.3},"1.2":"Probe_Beacon_Unknown"}}
                """);
        rewrite(document, false);
        check(!document.containsKey("Id") && !document.containsKey("Parent"), "Generated asset retained source identity");
        near(document.getNumber("RunTime").doubleValue(), 0.6 / speed);
        var cooldown = document.getDocument("Cooldown");
        check(cooldown.getString("Id").getValue().equals("SharedCooldown"), "Cooldown identity changed");
        near(cooldown.getNumber("Cooldown").doubleValue(), 1.2 / speed);
        near(cooldown.getArray("Charges").get(0).asNumber().doubleValue(), 0.3 / speed);
        near(cooldown.getArray("Charges").get(1).asNumber().doubleValue(), 0.9 / speed);
        var effects = document.getDocument("Effects");
        near(effects.getNumber("StartDelay").doubleValue(), 0.24 / speed);
        check(effects.getString("ItemPlayerAnimationsId").getValue().equals(prefix + "Pickaxe"), "Animation family was not accelerated");
        var thresholds = document.getDocument("Next");
        String inline = thresholds.getString(Float.toString(0.6f / speed)).getValue();
        check(inline.equals(prefix + "Pickaxe_Inline_0"), "Inline ID order changed");
        near(documents("steps").get(inline).getNumber("RunTime").doubleValue(), 0.3 / speed);
        check(thresholds.getString(Float.toString(1.2f / speed)).getValue().equals("Probe_Beacon_Unknown"), "Charging fallback changed");
        var inlineRoot = BsonDocument.parse("{\"Interactions\":[\"Pickaxe_Mine\"]}");
        String root = rewrite(inlineRoot, true).asString().getValue();
        check(root.equals(prefix + "Pickaxe_Inline_1") && documents("roots").containsKey(root), "Inline root lost its namespace or ID");
        check(originalAnimation.equals(ItemPlayerAnimations.CODEC.encode(ItemPlayerAnimations.getAssetMap().getAsset("Pickaxe"), new ExtraInfo())), "Original animation was modified");
        var faster = ItemPlayerAnimations.getAssetMap().getAsset(prefix + "Pickaxe");
        near(faster.toPacket().animations.get("Mine").speed, speed);
    }

    private BsonValue rewrite(BsonValue value, boolean reference) throws ReflectiveOperationException {
        try {
            return (BsonValue) transform.invoke(assets, value, "Pickaxe", reference);
        } catch (InvocationTargetException error) {
            throw new AssertionError("Haste transformation failed", error.getCause());
        }
    }

    private static void publicationFailures() throws ReflectiveOperationException {
        String badId = "Probe_Beacon_Incompatible";
        var raw = new RawAsset<String>(null, badId, null, 0,
                "{\"Type\":\"Probe_Beacon_UnknownType\"}".toCharArray(), null, ContainedAssetCodec.Mode.NONE);
        var failed = Interaction.getAssetStore().loadBuffersWithKeys(PACK, List.of(raw), AssetUpdateQuery.DEFAULT, false);
        check(failed.hasFailed() && failed.getFailedToLoadKeys().contains(badId)
                && !failed.getLoadedAssets().containsKey(badId), "Native incompatible document was not rejected");
        var child = new AssetLoadResult<String, Interaction>(Map.of(), Map.of(), Set.of(), Set.of(), Map.of(Interaction.class, failed));
        var guard = BeaconHasteAssets.class.getDeclaredMethod("requireLoaded", AssetLoadResult.class, String.class);
        guard.setAccessible(true);
        for (var result : List.of(failed, child)) {
            try {
                guard.invoke(null, result, "probe publication");
                throw new AssertionError("Failed publication was treated as success");
            } catch (InvocationTargetException error) {
                check(error.getCause() instanceof IllegalStateException
                        && error.getCause().getMessage().contains("probe publication"), "Publication failure lost its phase");
                if (result == failed) check(error.getCause().getMessage().contains(badId), "Publication failure lost its asset ID");
            }
        }
        for (String methodName : List.of("step", "animation")) {
            Method method = methodName.equals("step")
                    ? BeaconHasteAssets.class.getDeclaredMethod(methodName, String.class, String.class)
                    : BeaconHasteAssets.class.getDeclaredMethod(methodName, String.class);
            method.setAccessible(true);
            try {
                var assets = new BeaconHasteAssets(1);
                if (methodName.equals("step")) method.invoke(assets, badId, "Pickaxe");
                else method.invoke(assets, badId);
                throw new AssertionError("Missing asset was accepted: " + methodName);
            } catch (InvocationTargetException error) {
                check(error.getCause() instanceof IllegalArgumentException
                        && error.getCause().getMessage().contains(badId), "Missing asset lost its diagnosis: " + methodName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, BsonDocument> documents(String name) throws ReflectiveOperationException {
        var field = BeaconHasteAssets.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, BsonDocument>) field.get(assets);
    }

    private static void near(double actual, double expected) {
        check(Math.abs(actual - expected) < 0.000001, "Unexpected haste value: " + actual + ", expected " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
