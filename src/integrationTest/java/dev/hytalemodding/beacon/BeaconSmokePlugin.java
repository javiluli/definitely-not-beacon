package dev.hytalemodding.beacon;

import dev.hytalemodding.DefinitelyNotBeaconPlugin;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.modelvfx.config.ModelVFX;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.*;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import org.joml.Vector3d;
import org.joml.Vector3i;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Comprueba la mecánica con actores sin cliente en mundos desechables del servidor nativo. */
public class BeaconSmokePlugin extends DefinitelyNotBeaconPlugin {
    private final int timeoutSeconds;
    private final AtomicBoolean finished = new AtomicBoolean();
    private Ref<EntityStore> actor;
    private boolean forwardDamage;
    private ItemDropList nativeBossLoot;
    private com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType nativeStamina;
    private final Map<String, BlockType> nativeCrystals = new HashMap<>();
    private final List<String> results = new ArrayList<>();

    public BeaconSmokePlugin(JavaPluginInit init) { this(init, 45); }

    /** La variante de profiling amplía el plazo porque ejecuta mediciones después de las comprobaciones. */
    protected BeaconSmokePlugin(JavaPluginInit init, int timeoutSeconds) {
        super(init);
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override protected void setup() {
        super.setup();
        getEntityStoreRegistry().registerSystem(new ProbeDamage());
        getEventRegistry().registerGlobal(BootEvent.class, event -> begin());
    }

    @Override protected void start() {
        nativeStamina = com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType.getAssetMap().getAsset("Stamina");
        for (BlockType block : BlockType.getAssetMap().getAssetMap().values()) {
            if (block.getId().startsWith("Rock_Crystal_") && block.getId().endsWith("_Small")) {
                nativeCrystals.put(block.getId(), block);
            }
        }
        nativeBossLoot = ItemDropList.getAssetMap().getAsset("Drop_Goblin_Gobliterator");
        check(nativeBossLoot != null && nativeBossLoot.getContainer().getAllDrops(new ArrayList<>()).stream()
                .noneMatch(drop -> "Beacon_Block".equals(drop.getItemId()) || "Iron_Block".equals(drop.getItemId())),
                "Boss loot must not include Beacon items");
        super.start();
    }

    /** Arranca las comprobaciones cuando Hytale ya ha cargado los registros y los assets. */
    protected void begin() {
        CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS).execute(() -> finish(new TimeoutException("Native smoke test timed out")));
        try {
            BeaconHasteAssetsChecks.run(results);
            BeaconTextChecks.run(results);
            checkSelectionValues();
            registerResistanceArmor();
            WorldConfig config = new WorldConfig();
            config.setWorldGenProvider(new VoidWorldGenProvider());
            config.setCanUnloadChunks(false);
            String name = "beacon_probe_" + System.nanoTime();
            Universe.get().makeWorld(name, Path.of(name), config)
                    .whenComplete((world, error) -> {
                        if (error != null) { finish(error); return; }
                        world.execute(() -> load(world));
                    });
        } catch (Throwable error) { finish(error); }
    }

    /** Asset exclusivo de la fixture: combina herencia, reducción fija y protección cercana al límite. */
    private void registerResistanceArmor() {
        String pack = getIdentifier().toString();
        var assets = Item.getAssetStore();
        var data = org.bson.BsonDocument.parse("""
                {"Parent":"Armor_Iron_Chest","Armor":{"ArmorSlot":"Chest","DamageResistance":{
                    "Physical":[{"CalculationType":"Percent","Amount":0.09},{"CalculationType":"Flat","Amount":3}],
                    "Slashing":[{"CalculationType":"Percent","Amount":0.10}],
                    "Projectile":[{"CalculationType":"Percent","Amount":0.94}]
                }}}
                """);
        check(!assets.loadAssets(pack, List.of(assets.decode(pack, "Probe_Beacon_Armor", data))).hasFailed(),
                "Could not publish resistance fixture armor");
    }

    private void load(World world) {
        try {
            List<CompletableFuture<?>> loaded = new ArrayList<>();
            for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
                loaded.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z));
            }
            CompletableFuture.allOf(loaded.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
                if (error != null) { finish(error); return; }
                world.execute(() -> build(world));
            });
        } catch (Throwable error) { finish(error); }
    }

    private void build(World world) {
        try {
            Item beacon = Item.getAssetMap().getAsset("Beacon_Block");
            check(beacon.getMaxStack() == 100, "Beacon stack must hold 100 items");
            var beaconStack = new SimpleItemContainer((short) 1);
            beaconStack.addItemStack(new ItemStack("Beacon_Block", 100));
            check(beaconStack.getItemStack((short) 0).getQuantity() == 100, "Native inventory split the Beacon stack");
            check(Item.getAssetMap().getAsset("Beacon_Iron_Block") == null
                    && BlockType.getAssetMap().getIndex("Beacon_Iron_Block") < 0, "Old iron ID remains registered");
            results.add("Items: Iron_Block is the only custom iron ID; 100 Beacons fit in one native inventory slot; old iron has no migration");
            Item fragment = Item.getAssetMap().getAsset("PortalKey_The_Deepworks");
            check(beacon.getQualityIndex() == ItemQuality.getAssetMap().getIndex("Legendary")
                    && beacon.getQualityIndex() == fragment.getQualityIndex(), "Beacon did not inherit the fragment's Legendary quality");
            check("Drop_Legendary".equals(beacon.getItemEntityConfig().toPacket().particleSystemId)
                    && beacon.getItemEntityConfig().toPacket().showItemParticles, "Beacon is missing native Legendary drop particles");
            check(Item.getAssetMap().getAsset("Iron_Block").getQualityIndex()
                    != beacon.getQualityIndex(), "Iron block must not become Legendary");
            results.add("Beacon: native Legendary quality and Drop_Legendary particles, matching The Deepworks fragment; iron unchanged");
            checkBossLoot();
            checkRecipes();
            checkBlockLight(beacon);

            results.add("Beacon: original native held block renderer and #567/radius 1 light restored; core pulse capped at 1 particle, 4 seconds, 24-block culling");
            for (int x = 30; x <= 32; x++) for (int z = 30; z <= 32; z++) put(world, x, 31, z, "Iron_Block");
            put(world, 31, 32, 31, "Beacon_Block");
            check(BeaconStructures.findActive(world.getChunkStore()).containsKey(new BeaconStructures.Position(31,32,31)), "Discovery across section borders failed");
            check(BeaconSelection.get(world.getChunkStore(), new BeaconStructures.Position(31,32,31)) == null, "New beacon should not be paid");
            checkPyramids(world);
            after(world, () -> resident(world));
        } catch (Throwable error) { finish(error); }
    }

    /** Conserva el botín nativo, incluido el núcleo requerido para fabricar el Beacon. */
    private void checkBossLoot() {
        var loot = ItemDropList.getAssetMap().getAsset("Drop_Goblin_Gobliterator");
        check(loot == nativeBossLoot, "Beacon replaced the native boss loot table");
        var drops = loot.getContainer().getAllDrops(new ArrayList<>());
        check(drops.stream().noneMatch(drop -> "Beacon_Block".equals(drop.getItemId())
                || "Iron_Block".equals(drop.getItemId())), "Boss still drops Beacon items");
        check(drops.stream().anyMatch(drop -> "Ingredient_Gobliterator_Core".equals(drop.getItemId())),
                "Native Gobliterator Core reward is missing");
        results.add("Gobliterator: native loot table unchanged; core preserved; no Beacon or iron block rewards");
    }

    /** Comprueba las recetas publicadas, sus mesas y el consumo nativo de materiales. */
    private void checkRecipes() {
        checkRecipe("Iron_Block", Map.of("Ingredient_Bar_Iron", 10),
                "Bench_Builders", BenchType.StructuralCrafting, "Bricks");
        checkRecipe("Beacon_Block", Map.of("Ingredient_Crystal_Cyan", 20, "Rock_Basalt_Brick", 20,
                "Ingredient_Bar_Thorium", 8, "Ingredient_Life_Essence", 10, "Ingredient_Gobliterator_Core", 1),
                "Bench_Arcane", BenchType.Crafting, "Arcane_Misc");
        results.add("Crafting: Builders 10 iron ingots -> 1 iron block; Arcanebench/Misc 20 cyan shards, 20 basalt bricks, 8 thorium ingots, 10 life essence, 1 core -> 1 Beacon; incomplete inputs rejected without loss");
    }

    private void checkRecipe(String itemId, Map<String, Integer> expected, String benchItemId, BenchType type, String category) {
        Item item = Item.getAssetMap().getAsset(itemId);
        String recipeId = CraftingRecipe.generateIdFromItemRecipe(item, 0);
        CraftingRecipe recipe = CraftingRecipe.getAssetMap().getAsset(recipeId);
        check(recipe != null, "Recipe missing: " + itemId);
        Map<String, Integer> inputs = new HashMap<>();
        for (var material : recipe.getInput()) {
            check(material.getItemId() != null && Item.getAssetMap().getAsset(material.getItemId()) != null,
                    "Recipe ingredient missing: " + material);
            check(inputs.put(material.getItemId(), material.getQuantity()) == null, "Duplicate recipe ingredient");
        }
        check(inputs.equals(expected), "Unexpected ingredients: " + inputs);
        var output = recipe.getPrimaryOutput();
        check(output != null && itemId.equals(output.getItemId()) && output.getQuantity() == 1
                && recipe.getOutputs().length == 1, "Recipe must produce exactly one " + itemId);
        var requirements = recipe.getBenchRequirement();
        var bench = BlockType.getAssetMap().getAsset(benchItemId).getBench();
        check(requirements.length == 1 && requirements[0].type == type && bench.getType() == type
                && bench.getId().equals(requirements[0].id)
                && Arrays.equals(requirements[0].categories, new String[]{category}), "Wrong recipe bench/category");
        var available = CraftingPlugin.getAvailableRecipesForCategory(bench.getId(), category);
        check(available != null && available.contains(recipeId), "Recipe not registered in bench category");
        for (var input : recipe.getInput()) {
            check(CraftingPlugin.isValidCraftingMaterialForBench(bench, new ItemStack(input.getItemId(), 1)),
                    "Bench rejects recipe ingredient: " + input.getItemId());
        }
        checkCraftingMaterials(recipe);
    }

    /** Usa la misma transacción de retirada que CraftingManager, sin necesitar un cliente conectado. */
    private void checkCraftingMaterials(CraftingRecipe recipe) {
        var inputs = recipe.getInput();
        for (int missing = 0; missing < inputs.length; missing++) {
            var inventory = new SimpleItemContainer((short) inputs.length);
            for (short slot = 0; slot < inputs.length; slot++) {
                int quantity = inputs[slot].getQuantity() - (slot == missing ? 1 : 0);
                if (quantity > 0) inventory.setItemStackForSlot(slot, new ItemStack(inputs[slot].getItemId(), quantity));
            }
            check(!inventory.removeMaterials(Arrays.asList(inputs), true, true, true).succeeded(),
                    "Recipe accepted incomplete ingredients");
            for (short slot = 0; slot < inputs.length; slot++) {
                int expected = inputs[slot].getQuantity() - (slot == missing ? 1 : 0);
                var stack = inventory.getItemStack(slot);
                check(expected == 0 ? ItemStack.isEmpty(stack) : !ItemStack.isEmpty(stack) && stack.getQuantity() == expected,
                        "Failed crafting consumed materials");
            }
        }
        var inventory = new SimpleItemContainer((short) inputs.length);
        for (short slot = 0; slot < inputs.length; slot++) {
            inventory.setItemStackForSlot(slot, new ItemStack(inputs[slot].getItemId(), inputs[slot].getQuantity() + 1));
        }
        check(inventory.removeMaterials(Arrays.asList(inputs), true, true, true).succeeded(), "Valid crafting rejected");
        for (short slot = 0; slot < inputs.length; slot++) {
            check(inventory.getItemStack(slot).getQuantity() == 1, "Crafting consumed the wrong quantity");
        }
    }

    private void checkBlockLight(Item beacon) {
        var block = BlockType.getAssetMap().getAsset("Beacon_Block");
        check(beacon.toPacket().model == null && beacon.getLight() == null
                && beacon.toPacket().blockId == BlockType.getAssetMap().getIndex("Beacon_Block")
                && "Block".equals(beacon.toPacket().playerAnimationsId),
                "Beacon must use the original native block renderer and holding animation");
        var blockLight = block.getLight();
        check(blockLight != null && blockLight.red == 5 && blockLight.green == 6 && blockLight.blue == 7 && blockLight.radius == 1
                && blockLight.equals(new ItemComponent(new ItemStack("Beacon_Block")).computeDynamicLight()),
                "Native Beacon light must retain its original intensity in hand, placed and dropped");
        var pulse = ParticleSystem.getAssetMap().getAsset("Beacon_Core_Pulse").toPacket();
        var spawner = ParticleSpawner.getAssetMap().getAsset("Beacon_Core_Pulse").toPacket();
        check(pulse.spawners.length == 1 && pulse.cullDistance == 24 && pulse.boundingRadius == 1,
                "Core pulse must stay local and use only one spawner");
        check(spawner.maxConcurrentParticles == 1 && spawner.particleLifeSpan.min == 4 && spawner.spawnRate.max == .25f,
                "Core pulse exceeds its one-particle budget");
        check(block.getParticles().length == 1 && "Beacon_Core_Pulse".equals(block.getParticles()[0].toPacket().systemId),
                "Placed Beacon is missing its core pulse");
        check(Arrays.stream(beacon.toPacket().particles).anyMatch(particle -> "Beacon_Core_Pulse".equals(particle.systemId)),
                "Held Beacon is missing its core pulse");
    }

    private void resident(World world) {
        try {
            check(beamCount(world.getEntityStore().getStore()) == 0, "Beam spawned in a non-ticking section");
            ChunkStore chunks = world.getChunkStore();
            for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
                chunks.getStore().tryRemoveComponent(chunks.getChunkSectionReferenceAtBlock(x*32,y*32,z*32), ChunkStore.REGISTRY.getNonTickingComponentType());
            }
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(31.5,33,31.5), new Rotation3f()));
            holder.addComponent(EffectControllerComponent.getComponentType(), new EffectControllerComponent());
            holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(UUID.randomUUID()));
            holder.addComponent(InventoryComponent.Hotbar.getComponentType(), new InventoryComponent.Hotbar((short) 9));
            holder.addComponent(InventoryComponent.Storage.getComponentType(), new InventoryComponent.Storage((short) 36));
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            actor = world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
            check(actor != null, "Native actor spawn failed");
            check(damage(world.getEntityStore().getStore(), "Tool_Pickaxe_Crude") == 10f, "Unpaid beacon granted haste");
            BeaconPaymentChecks paymentChecks = new BeaconPaymentChecks(actor, results);
            paymentChecks.checkInventorySections(world.getEntityStore().getStore());
            paymentChecks.checkPayment(world.getEntityStore().getStore());
            var position = new BeaconStructures.Position(31,32,31);
            var block = BeaconSelection.blockEntity(chunks, position, true);
            check(block != null, "Could not create persistent block entity");
            BeaconSelection.save(block, BeaconPower.HASTE, "NONE");
            checkPersistence(world, block, position);
            paymentChecks.checkPage(world.getEntityStore().getStore(), block, position);
            checkOverlappingPowers(world);
            refreshPowers(world);
            var haste = world.getEntityStore().getStore().getComponent(actor, EffectControllerComponent.getComponentType()).getActiveEffects()
                    .get(EntityEffect.getAssetMap().getIndex(BeaconPower.HASTE.effectId(1)));
            check(haste != null && haste.getRemainingDuration() == 11f, "Tier one did not grant 11 seconds of actual haste");
            prepareBeamTints(world);
        } catch (Throwable error) { finish(error); }
    }

    private void checkPyramids(World world) {
        var position = new BeaconStructures.Position(31,32,31);
        for (int depth = 2; depth <= 4; depth++) {
            for (int x = -depth; x <= depth; x++) for (int z = -depth; z <= depth; z++)
                put(world, 31+x,32-depth,31+z,"Iron_Block");
            check(BeaconStructures.level(world.getChunkStore(), position) == depth, "Native pyramid tier " + depth + " not detected");
            put(world, 31-depth,32-depth,31-depth,"Empty");
            check(BeaconStructures.level(world.getChunkStore(), position) == depth-1, "Incomplete layer did not downgrade the tier");
            put(world, 31-depth,32-depth,31-depth,"Iron_Block");
        }
        for (int depth = 2; depth <= 4; depth++)
            for (int x = -depth; x <= depth; x++) for (int z = -depth; z <= depth; z++) put(world,31+x,32-depth,31+z,"Empty");
        results.add("Native pyramids: levels 1-4 across chunk borders; missing corners downgrade correctly");
    }

    private void checkSelectionValues() {
        for (String json : List.of(
                "{\"Primary\":\"HASTE\",\"Secondary\":\"UPGRADE\"}",
                "{\"Primary\":\"HASTE\",\"Secondary\":\"future\"}",
                "{\"Primary\":\"future\",\"Secondary\":\"REGENERATION\"}",
                "{\"Primary\":\"future\",\"Secondary\":\"future\"}")) {
            var document = org.bson.BsonDocument.parse(json);
            var selection = BeaconSelection.CODEC.decode(document, new ExtraInfo());
            check(document.equals(BeaconSelection.CODEC.encode(selection, new ExtraInfo())), "Selection values were normalized on codec round trip");
            check(document.equals(BeaconSelection.CODEC.encode(selection.clone(), new ExtraInfo())), "Selection clone changed persisted values");
            if ("HASTE".equals(selection.primary)) {
                boolean upgrade = "UPGRADE".equals(selection.secondary);
                check(selection.strength(BeaconPower.HASTE, 4) == (upgrade ? 2 : 1)
                        && selection.strength(BeaconPower.HASTE, 3) == 1, "Paid primary lost legacy secondary/downgrade behavior");
            } else {
                check(selection.strength(BeaconPower.HASTE, 4) == 0
                        && selection.strength(BeaconPower.REGENERATION, 4) == ("REGENERATION".equals(selection.secondary) ? 1 : 0),
                        "Unknown primary changed legacy effect behavior");
            }
        }
        var nulls = BeaconSelection.CODEC.decode(org.bson.BsonDocument.parse("{\"Primary\":null,\"Secondary\":null}"), new ExtraInfo());
        check(nulls.primary == null && nulls.secondary == null, "Explicit BSON nulls became missing-field defaults");
        var copy = nulls.clone();
        check(copy.primary == null && copy.secondary == null && copy.strength(BeaconPower.HASTE, 4) == 0, "Clone normalized explicit nulls");
        var nullSecondary = BeaconSelection.CODEC.decode(org.bson.BsonDocument.parse("{\"Primary\":\"HASTE\",\"Secondary\":null}"), new ExtraInfo());
        check(nullSecondary.strength(BeaconPower.HASTE, 4) == 1, "Null secondary stopped the paid primary");
        try {
            nullSecondary.matches(BeaconPower.HASTE, "NONE");
            throw new AssertionError("Legacy null-secondary comparison no longer throws");
        } catch (NullPointerException expected) {
            // Caracteriza el límite existente; H4 no cambia la política de datos incoherentes.
        }
        results.add("Selection codec: known/unknown strings and explicit BSON nulls preserved; clone, legacy regeneration and null-secondary comparison characterized");
    }

    private void checkPersistence(World world, Ref<ChunkStore> block, BeaconStructures.Position position) {
        var selection = BeaconSelection.get(world.getChunkStore(), position);
        var encoded = BeaconSelection.CODEC.encode(selection, new ExtraInfo());
        check(encoded.asDocument().keySet().equals(Set.of("Primary", "Secondary"))
                && "HASTE".equals(encoded.asDocument().getString("Primary").getValue())
                && "NONE".equals(encoded.asDocument().getString("Secondary").getValue()), "Paid selection wire format changed");
        var defaults = BeaconSelection.CODEC.decode(new org.bson.BsonDocument(), new ExtraInfo());
        check(defaults.primary.isEmpty() && "NONE".equals(defaults.secondary)
                && defaults.strength(BeaconPower.HASTE, 4) == 0, "Missing selection fields must stay unpaid");
        var decoded = BeaconSelection.CODEC.decode(encoded, new ExtraInfo());
        check(decoded.strength(BeaconPower.HASTE, 1) == 1, "Paid selection did not survive codec round trip");
        check(decoded.matches(BeaconPower.HASTE, "NONE") && !decoded.matches(BeaconPower.SPEED, "NONE"), "Paid selection comparison failed");
        var info = block.getStore().getComponent(block, BlockModule.BlockStateInfo.getComponentType());
        var section = block.getStore().getComponent(info.getSectionRef(), BlockComponentSection.getComponentType());
        var snapshot = BlockComponentSection.CODEC.encode(section, new ExtraInfo());
        check(snapshot.toString().contains("BeaconSelection"), "Selection not included in native chunk serialization");
        results.add("Native chunk serialization includes paid BeaconSelection");
    }

    /** Caracteriza solapamientos, desempates y descenso inmediato de II a I sin un jugador simulado. */
    private void checkOverlappingPowers(World world) {
        var near = new BeaconStructures.Position(31, 32, 31);
        var other = new BeaconStructures.Position(47, 32, 31);
        put(world, other.x(), other.y(), other.z(), "Beacon_Block");
        layers(world, other, 1, 3, "Iron_Block");
        var nearBlock = BeaconSelection.blockEntity(world.getChunkStore(), near, false);
        var otherBlock = BeaconSelection.blockEntity(world.getChunkStore(), other, true);
        check(nearBlock != null && otherBlock != null, "Overlap block entities missing");
        BeaconSelection.save(otherBlock, BeaconPower.HASTE, "NONE");
        checkHasteFromBases(world, 1, 15f);

        layers(world, near, 2, 3, "Iron_Block");
        layers(world, other, 2, 3, "Empty");
        checkHasteFromBases(world, 1, 15f);

        layers(world, near, 4, 4, "Iron_Block");
        BeaconSelection.save(nearBlock, BeaconPower.HASTE, "UPGRADE");
        checkHasteFromBases(world, 2, 17f);
        layers(world, other, 2, 4, "Iron_Block");
        BeaconSelection.save(otherBlock, BeaconPower.HASTE, "UPGRADE");
        checkHasteFromBases(world, 2, 17f);

        layers(world, near, 4, 4, "Empty");
        layers(world, other, 4, 4, "Empty");
        checkHasteFromBases(world, 1, 15f);
        layers(world, near, 4, 4, "Iron_Block");
        checkHasteFromBases(world, 2, 17f);
        put(world, 30, 31, 30, "Empty");
        put(world, 46, 31, 30, "Empty");
        check(BeaconStructures.findActive(world.getChunkStore()).isEmpty(), "Broken overlapping bases remained active");
        checkHasteFromBases(world, 2, 17f);

        put(world, 30, 31, 30, "Iron_Block");
        put(world, other.x(), other.y(), other.z(), "Empty");
        layers(world, near, 2, 4, "Empty");
        BeaconSelection.save(nearBlock, BeaconPower.HASTE, "NONE");
        checkHasteFromBases(world, 1, 11f);
        results.add("Overlaps: equal ranks use longest duration in either tier order; II never stacks; downgrade II -> I is immediate; repair restores II; no sources preserve residual II");
        checkBeamRepair(world);
    }

    private void checkBeamRepair(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        var oldCore = beamLayer(store, "Beacon_Beam_Core");
        var oldHalo = beamLayer(store, "Beacon_Beam_Halo");
        store.removeEntity(oldCore, RemoveReason.REMOVE);
        checkHasteFromBases(world, 1, 11f);
        check(!oldCore.isValid() && !oldHalo.isValid() && beamCount(store) == 2, "Partial beam was not replaced as a pair");
        var core = beamLayer(store, "Beacon_Beam_Core");
        var halo = beamLayer(store, "Beacon_Beam_Halo");
        var expiry = store.getResource(TimeResource.getResourceType()).getNow().plusSeconds(3);
        for (var layer : List.of(core, halo)) {
            check(expiry.equals(store.getComponent(layer, DespawnComponent.getComponentType()).getDespawn()), "Beam lifetime was not renewed to three seconds");
        }
        checkHasteFromBases(world, 1, 11f);
        check(core.isValid() && halo.isValid() && beamCount(store) == 2, "Healthy beam layers were replaced or duplicated");
        results.add("Beam lifecycle: missing core removes the old halo, recreates exactly two layers and renews their three-second lifetime; healthy layers are reused");
    }

    private void checkHasteFromBases(World world, int rank, float duration) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        var registered = registeredSystem(BeaconStatusSystem.class);
        registered.instance().delayedTick(1f, registered.index(), store);
        registered.instance().refreshPowers(actor, store.getComponent(actor, TransformComponent.getComponentType()).getPosition(), store, store);
        var effects = store.getComponent(actor, EffectControllerComponent.getComponentType());
        var active = effects.getActiveEffects().get(EntityEffect.getAssetMap().getIndex(BeaconPower.HASTE.effectId(rank)));
        check(active != null && active.getRemainingDuration() == duration, "Unexpected overlapping haste rank/duration: " + rank + "/" + duration);
        check(!effects.hasEffect(EntityEffect.getAssetMap().getIndex(BeaconPower.HASTE.effectId(3 - rank))), "Both haste ranks remained active");
    }

    private void layers(World world, BeaconStructures.Position position, int first, int last, String block) {
        for (int depth = first; depth <= last; depth++) {
            for (int x = -depth; x <= depth; x++) for (int z = -depth; z <= depth; z++) {
                put(world, position.x() + x, position.y() - depth, position.z() + z, block);
            }
        }
    }

    private void checkNativeEffects() {
        for (BeaconPower power : BeaconPower.values()) for (int rank = 1; rank <= (power == BeaconPower.REGENERATION ? 1 : 2); rank++) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(power.effectId(rank));
            check(effect != null && effect.getStatusEffectIcon() != null, "Power asset missing: " + power + rank);
            if (power == BeaconPower.SPEED) check(Math.abs(effect.getApplicationEffects().getHorizontalSpeedMultiplier() - (rank == 1 ? 1.4 : 1.6)) < .0001, "Speed multiplier missing");
            if (power == BeaconPower.RESISTANCE) check(effect.getDamageResistanceValues() == null || effect.getDamageResistanceValues().isEmpty(), "Native resistance would double-apply the Beacon damage filter");
            if (power == BeaconPower.STRENGTH) check(effect.getOutgoingDamageValues() == null || effect.getOutgoingDamageValues().isEmpty(), "Strength must not boost native ranged damage");
            if (power == BeaconPower.REGENERATION) check(effect.getEntityStats() != null && effect.getDamageCalculatorCooldown() == BeaconPower.REGENERATION_INTERVAL_SECONDS, "Regeneration missing");
        }
        results.add("All 11 native power assets loaded from the central Java balance; no native strength map affects ranged damage");
    }

    /** Verifica los registros del plugin, la versión nativa y el orden de los filtros de daño. */
    private void checkCompatibility() {
        check(!CommandManager.get().getCommandRegistration().containsKey("beacon"),
                "Removed /beacon information command is still registered");
        results.add("Mod entry: no obsolete /beacon information command registered");
        var systems = EntityStore.REGISTRY._internal_getData();
        for (Class<?> type : List.of(BeaconStatusSystem.class, BeaconBeamRotationSystem.class,
                BeaconMiningSystem.class, BeaconResistanceSystem.class, BeaconStrengthSystem.class,
                BeaconAttackSpeedSystem.class, BeaconAttackSpeedSystem.Cleanup.class)) {
            int count = 0;
            for (int i = 0; i < systems.getSystemSize(); i++) {
                if (type.isInstance(systems.getSystem(i))) count++;
            }
            check(count == 1, "Expected one registered " + type.getSimpleName() + ", found " + count);
        }
        results.add("Plugin setup: all seven Beacon systems are registered exactly once");
        String version = ManifestUtil.getVersion();
        var range = getManifest().getServerVersion();
        check(PluginManifest.checkServerVersionCompatibility(range, version) == PluginManifest.ServerVersionCheck.COMPATIBLE,
                "Native server " + version + " is outside the declared range " + range);
        int outgoing = registeredSystem(DamageSystems.ScaleOutgoingDamageFromEntityEffects.class).index();
        int strength = registeredSystem(BeaconStrengthSystem.class).index();
        int armor = registeredSystem(DamageSystems.ArmorDamageReduction.class).index();
        int resistance = registeredSystem(BeaconResistanceSystem.class).index();
        check(outgoing < strength && strength < armor && armor < resistance,
                "Expected outgoing effects < strength < armor < resistance, found "
                        + outgoing + ", " + strength + ", " + armor + ", " + resistance);
        results.add("Native compatibility: Hytale " + version + ", revision=" + ManifestUtil.getImplementationRevisionId()
                + ", ServerVersion=" + range);
        results.add("Registered damage order: outgoing effects " + outgoing + " < strength " + strength
                + " < armor " + armor + " < resistance " + resistance);
    }

    private void checkCombatAndHealing(Store<EntityStore> store) {
        EffectControllerComponent controller = store.getComponent(actor, EffectControllerComponent.getComponentType());
        for (int rank = 1; rank <= 2; rank++) {
            EntityEffect strength = EntityEffect.getAssetMap().getAsset(BeaconPower.STRENGTH.effectId(rank));
            controller.addEffect(actor, strength, store);
            Damage outgoing = new Damage(new Damage.EntitySource(actor), DamageCause.getAssetMap().getAsset("Slashing"), 100f);
            applyNativeDamageSystem(store, outgoing, true);
            check(Math.abs(outgoing.getAmount() - (rank == 1 ? 140f : 180f)) < .001f, "Native strength did not scale actual damage");
            Damage projectile = new Damage(new Damage.ProjectileSource(actor, actor), DamageCause.getAssetMap().getAsset("Slashing"), 100f);
            applyNativeDamageSystem(store, projectile, true);
            check(projectile.getAmount() == 100f, "Strength incorrectly boosted a projectile");
            controller.removeEffect(actor, EntityEffect.getAssetMap().getIndex(strength.getId()), store);
            EntityEffect resistance = EntityEffect.getAssetMap().getAsset(BeaconPower.RESISTANCE.effectId(rank));
            controller.addEffect(actor, resistance, store);
            for (String cause : List.of("Physical", "Slashing", "Bludgeoning", "Projectile")) {
                Damage incoming = new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getAsset(cause), 100f);
                applyNativeDamageSystem(store, incoming, false);
                check(Math.abs(incoming.getAmount() - (rank == 1 ? 95f : 92f)) < .001f, "Resistance missed " + cause);
            }
            for (String cause : List.of("Fire", "Poison", "Fall", "OutOfWorld")) {
                Damage incoming = new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getAsset(cause), 100f);
                applyNativeDamageSystem(store, incoming, false);
                check(incoming.getAmount() == 100f, "Resistance reduced an unprotected cause: " + cause);
            }
            controller.removeEffect(actor, EntityEffect.getAssetMap().getIndex(resistance.getId()), store);
            EntityEffect jump = EntityEffect.getAssetMap().getAsset(BeaconPower.JUMP_BOOST.effectId(rank));
            controller.addEffect(actor, jump, store);
            Damage fall = new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getAsset("Fall"), 100f);
            applyNativeDamageSystem(store, fall, false);
            check(Math.abs(fall.getAmount() - (100f - 20f*rank)) < .001f, "Jump did not reduce fall damage");
            Damage ordinary = new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getAsset("Slashing"), 100f);
            applyNativeDamageSystem(store, ordinary, false);
            check(ordinary.getAmount() == 100f, "Jump reduced ordinary combat damage");
            controller.removeEffect(actor, EntityEffect.getAssetMap().getIndex(jump.getId()), store);
        }
        checkResistanceHealth(store, controller);
        checkRegeneration(store, controller);
        BeaconStaminaChecks.run(store, actor, nativeStamina, results);
        results.add("Native combat: melee 100 -> 140/180; physical/projectile 100 -> 95/92; fire, poison, fall and bypass unchanged; jump protection preserved");
    }

    /** El máximo se suma al equipo, sustituye intensidades y desaparece sin curación instantánea ni acumulación. */
    private void checkResistanceHealth(Store<EntityStore> store, EffectControllerComponent controller) {
        var previousStats = store.getComponent(actor, EntityStatMap.getComponentType());
        var previousArmor = store.getComponent(actor, InventoryComponent.Armor.getComponentType());
        var stats = new EntityStatMap();
        stats.update();
        int health = DefaultEntityStatTypes.getHealth();
        float base = stats.get(health).getMax();
        stats.setStatValue(health, base / 2);
        var armor = new InventoryComponent.Armor((short) 4);
        var chest = Item.getAssetMap().getAsset("Armor_Iron_Chest").getArmor();
        check(armor.getInventory().setItemStackForSlot((short) chest.getArmorSlot().ordinal(), new ItemStack("Armor_Iron_Chest")).succeeded(),
                "Could not equip native iron chest");
        store.putComponent(actor, EntityStatMap.getComponentType(), stats);
        store.putComponent(actor, InventoryComponent.Armor.getComponentType(), armor);
        try {
            for (int rank : List.of(1, 1, 2, 1, 0)) {
                for (int remove = 1; remove <= 2; remove++) {
                    if (remove != rank) controller.removeEffect(actor, EntityEffect.getAssetMap().getIndex(BeaconPower.RESISTANCE.effectId(remove)), store);
                }
                if (rank > 0) controller.addEffect(actor, EntityEffect.getAssetMap().getAsset(BeaconPower.RESISTANCE.effectId(rank)), store);
                stats.getStatModifiersManager().scheduleRecalculate();
                stats.getStatModifiersManager().recalculateEntityStatModifiers(actor, stats, store);
                float bonus = rank == 0 ? 0 : rank == 1 ? 9 : 14;
                check(Math.abs(stats.get(health).getMax() - (base + 17 + bonus)) < .001f, "Resistance health did not compose with armor at rank " + rank);
                check(stats.get(health).get() == base / 2, "Resistance granted instant healing");
                for (String cause : List.of("Physical", "Projectile")) {
                    Damage incoming = new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getAsset(cause), 100f);
                    applyNativeDamageSystem(store, incoming, false);
                    float expected = rank == 0 ? 91 : rank == 1 ? 86 : 83;
                    check(Math.abs(incoming.getAmount() - expected) < .001f, "Resistance was not added to native armor protection");
                }
            }
            checkResistanceArmor(store, controller, armor);
            results.add("Resistance: native additive +9/+14 health on top of iron armor's +17; 9% armor + 5/8% Beacon = 14/17% protection; refresh, replacement, downgrade and removal do not stack or heal");
        } finally {
            if (previousStats == null) store.tryRemoveComponent(actor, EntityStatMap.getComponentType());
            else store.putComponent(actor, EntityStatMap.getComponentType(), previousStats);
            if (previousArmor == null) store.tryRemoveComponent(actor, InventoryComponent.Armor.getComponentType());
            else store.putComponent(actor, InventoryComponent.Armor.getComponentType(), previousArmor);
        }
    }

    /** La compensación conserva la rama física y sus reducciones fijas; el porcentaje final se limita a cero daño. */
    private void checkResistanceArmor(Store<EntityStore> store, EffectControllerComponent controller, InventoryComponent.Armor armor) {
        short slot = (short) Item.getAssetMap().getAsset("Probe_Beacon_Armor").getArmor().getArmorSlot().ordinal();
        check(armor.getInventory().setItemStackForSlot(slot, new ItemStack("Probe_Beacon_Armor")).succeeded(), "Could not equip fixture armor");
        for (int rank : List.of(1, 2)) {
            var effect = EntityEffect.getAssetMap().getAsset(BeaconPower.RESISTANCE.effectId(rank));
            controller.addEffect(actor, effect, store);
            Damage slashing = new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getAsset("Slashing"), 100f);
            applyNativeDamageSystem(store, slashing, false);
            check(Math.abs(slashing.getAmount() - (rank == 1 ? 74.82f : 72.21f)) < .001f,
                    "Inherited armor or flat protection changed at rank " + rank + ": " + slashing.getAmount());
            Damage projectile = new Damage(Damage.NULL_SOURCE, DamageCause.getAssetMap().getAsset("Projectile"), 100f);
            applyNativeDamageSystem(store, projectile, false);
            check(Math.abs(projectile.getAmount() - (rank == 1 ? 1f : 0f)) < .001f, "Armor bonus overflowed 100% protection");
            controller.removeEffect(actor, EntityEffect.getAssetMap().getIndex(effect.getId()), store);
        }
        results.add("Armor composition: inherited Slashing/Physical protection and flat reduction preserved; 94% + 5% = 99%, +8% caps at zero damage");
    }

    /** Usa el pulso fijo del tótem, reducido a dos puntos, sin reiniciar el reloj al renovar. */
    private void checkRegeneration(Store<EntityStore> store, EffectControllerComponent controller) {
        EntityStatMap stats = new EntityStatMap();
        stats.update();
        int health = DefaultEntityStatTypes.getHealth();
        float maximum = stats.get(health).getMax();
        stats.setStatValue(health, maximum / 2);
        EntityEffect regeneration = EntityEffect.getAssetMap().getAsset(BeaconPower.REGENERATION.effectId(1));
        controller.addEffect(actor, regeneration, store);
        ActiveEntityEffect active = controller.getActiveEffects().get(EntityEffect.getAssetMap().getIndex(regeneration.getId()));
        store.forEachChunk(TransformComponent.getComponentType(), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) if (actor.equals(entities.getReferenceTo(i))) active.tick(buffer, actor, regeneration, stats, .75f);
        });
        check(Math.abs(stats.get(health).get() - maximum*.50f) < .001f, "Regeneration healed before its one-second interval");
        controller.addEffect(actor, regeneration, 11f, com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE, store);
        store.forEachChunk(TransformComponent.getComponentType(), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) if (actor.equals(entities.getReferenceTo(i))) active.tick(buffer, actor, regeneration, stats, .25f);
        });
        check(Math.abs(stats.get(health).get() - (maximum*.50f + 2f)) < .001f, "Regeneration did not heal two fixed points");
        var totem = EntityEffect.getAssetMap().getAsset("Healing_Totem_Heal");
        check(totem.getEntityStats().get(health) == 5f && totem.getDamageCalculatorCooldown() == 1f
                && totem.getValueType() == regeneration.getValueType(), "Native totem healing contract changed");
        var nativeIcon = com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.getByName(totem.getStatusEffectIcon());
        var beaconIcon = com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.getByName(regeneration.getStatusEffectIcon());
        check(totem.getStatusEffectIcon().equals(regeneration.getStatusEffectIcon()) && nativeIcon != null && beaconIcon == nativeIcon,
                "Beacon does not reference the native healing totem icon directly");
        stats.setStatValue(health, maximum - 1);
        store.forEachChunk(TransformComponent.getComponentType(), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) if (actor.equals(entities.getReferenceTo(i))) active.tick(buffer, actor, regeneration, stats, 1f);
        });
        check(stats.get(health).get() == maximum, "Regeneration exceeded maximum health");
        results.add("Healing: native totem 5 health/s, Beacon 2 health/s; refresh preserves the clock, health caps at maximum, exact native totem status icon");
    }

    private void applyNativeDamageSystem(Store<EntityStore> store, Damage damage, boolean outgoing) {
        store.forEachChunk(TransformComponent.getComponentType(), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) if (actor.equals(entities.getReferenceTo(i))) {
                if (outgoing) {
                    new DamageSystems.ScaleOutgoingDamageFromEntityEffects().handle(i, entities, store, buffer, damage);
                    new BeaconStrengthSystem().handle(i, entities, store, buffer, damage);
                }
                else {
                    new DamageSystems.ArmorDamageReduction().handle(i, entities, store, buffer, damage);
                    new BeaconResistanceSystem().handle(i, entities, store, buffer, damage);
                }
            }
        });
    }

    private void active(World world) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            check(beamCount(store) == 2, "Expected core and halo, found " + beamCount(store));
            Interaction fastMine = Interaction.getAssetMap().getAsset("Beacon_Haste_Pickaxe_Pickaxe_Mine");
            check(fastMine != null, "Fast mining interaction missing");
            var fasterMine = Interaction.getAssetMap().getAsset("Beacon_Haste_II_Pickaxe_Pickaxe_Mine");
            check(fasterMine != null && Math.abs(fasterMine.getRunTime() - .083f/1.6f) < .00001f, "Haste II timing missing");
            checkCompatibility();
            checkNativeEffects();
            checkCombatAndHealing(store);
            BeaconHasteAssetsChecks.checkLoadedVariants(store, actor, fastMine);
            compareHits(world, "Tool_Pickaxe_Iron", 8, 7);
            compareHits(world, "Tool_Pickaxe_Crude", 16, 14);
            check(Math.abs(damage(store, "Tool_Pickaxe_Crude") - 12f) < 0.0001f, "OreIron did not receive +20% mining damage");
            check(damage(store, "Tool_Hatchet_Crude") == 10f, "Hatchet incorrectly received mining bonus");
            EntityEffect effect = EntityEffect.getAssetMap().getAsset("Beacon_HASTE_1");
            check(effect != null && "UI/Custom/Beacon/haste.png".equals(effect.getStatusEffectIcon()), "Native status effect asset missing");
            EffectControllerComponent effects = store.getComponent(actor, EffectControllerComponent.getComponentType());
            check(effects.addEffect(actor, effect, store) && effects.hasEffect(effect), "Native effect controller rejected Beacon status");
            effects.removeEffect(actor, EntityEffect.getAssetMap().getIndex("Beacon_HASTE_1"), store);
            checkColoredBeamSurvival(world, store);
        } catch (Throwable error) { finish(error); }
    }

    /** Mantiene un haz dividido durante ticks reales, conservando entidades, modelos y fase de giro. */
    private void checkColoredBeamSurvival(World world, Store<EntityStore> store) {
        checkBeamModels();
        putSideCrystal(world, 45, "Rock_Crystal_Blue_Small");
        putSideCrystal(world, 319, "Rock_Crystal_Red_Small");
        var upperSection = world.getChunkStore().getChunkSectionReferenceAtBlock(31, 319, 31);
        check(world.getChunkStore().getStore().getArchetype(upperSection)
                .contains(ChunkStore.REGISTRY.getNonTickingComponentType()), "Expected a loaded but cold upper section");
        var registered = registeredSystem(BeaconStatusSystem.class);
        registered.instance().delayedTick(1f, registered.index(), store);
        var pieces = checkBeamPlan(world, store);
        var models = pieces.stream().map(ref -> store.getComponent(ref, ModelComponent.getComponentType())).toList();
        float yaw = store.getComponent(pieces.getFirst(), TransformComponent.getComponentType()).getRotation().yaw();
        after(world, () -> {
            try {
                check(pieces.stream().allMatch(Ref::isValid), "Colored beam pieces were unloaded or despawned during native ticks");
                check(pieces.equals(checkBeamPlan(world, store)), "Stable colored beam recreated its core entities");
                for (int i = 0; i < pieces.size(); i++) {
                    check(models.get(i) == store.getComponent(pieces.get(i), ModelComponent.getComponentType()),
                            "Stable colored beam replaced a model and restarted its animation");
                }
                check(Math.abs(store.getComponent(pieces.getFirst(), TransformComponent.getComponentType()).getRotation().yaw() - yaw) > 0.2f,
                        "Colored beam rotation stopped");
                for (int y : List.of(45, 319)) {
                    put(world, 31, y, 31, "Empty");
                    put(world, 32, y, 31, "Empty");
                }
                registered.instance().delayedTick(1f, registered.index(), store);
                check(world.getChunkStore().getStore().getArchetype(upperSection)
                        .contains(ChunkStore.REGISTRY.getNonTickingComponentType()), "Beam forced an upper section to tick");
                results.add("Colored beam stability: crystals at y=45/319, cold upper section stays cold; same entities and models survive native ticks with continuous rotation");
                checkAsyncBeamColors(world, store);
            } catch (Throwable error) { finish(error); }
        });
    }

    /** Una mezcla nueva se aplica al terminar su publicación, aunque ya no cambie ningún bloque. */
    private void checkAsyncBeamColors(World world, Store<EntityStore> store) {
        putSideCrystal(world, 45, "Rock_Crystal_Green_Small");
        putSideCrystal(world, 50, "Rock_Crystal_Purple_Small");
        var registered = registeredSystem(BeaconStatusSystem.class);
        registered.instance().delayedTick(0f, registered.index(), store);
        List<CompletableFuture<EntityEffect>> tints = new ArrayList<>();
        requestBeamTints(world, tints);
        check(tints.stream().anyMatch(tint -> !tint.isDone()), "Expected a new asynchronous mix");
        check(beamCount(store) == 2, "Pending colors partially replaced the default beam");
        whenTintsReady(world, tints, () -> {
            registered.instance().delayedTick(0f, registered.index(), store);
            checkBeamPlan(world, store);
            checkSupersededBeamColors(world, store);
        });
    }

    /** Una publicación pendiente no puede restaurar un trazado que el jugador ya retiró. */
    private void checkSupersededBeamColors(World world, Store<EntityStore> store) {
        put(world, 32, 45, 31, "Empty");
        put(world, 32, 50, 31, "Empty");
        putSideCrystal(world, 45, "Rock_Crystal_Purple_Small");
        putSideCrystal(world, 50, "Rock_Crystal_Green_Small");
        var registered = registeredSystem(BeaconStatusSystem.class);
        registered.instance().delayedTick(0f, registered.index(), store);
        List<CompletableFuture<EntityEffect>> tints = new ArrayList<>();
        requestBeamTints(world, tints);
        check(tints.stream().anyMatch(tint -> !tint.isDone()), "Expected a pending reversed transition");
        for (int y : List.of(45, 50)) {
            put(world, 31, y, 31, "Empty");
            put(world, 32, y, 31, "Empty");
        }
        registered.instance().delayedTick(0f, registered.index(), store);
        whenTintsReady(world, tints, () -> {
            registered.instance().delayedTick(0f, registered.index(), store);
            checkDefaultBeam(store, List.of(beamLayer(store, "Beacon_Beam_Core"), beamLayer(store, "Beacon_Beam_Halo")));
            results.add("Async beam colors: a pending mix applies without another block change; superseded colors cannot restore removed crystals");
            checkActiveBeam(world, store);
        });
    }

    /** Espera fuera del hilo del mundo y reanuda allí las aserciones, propagando cualquier fallo. */
    private void whenTintsReady(World world, List<CompletableFuture<EntityEffect>> tints, Runnable next) {
        CompletableFuture.allOf(tints.toArray(CompletableFuture[]::new)).thenRunAsync(next, world)
                .exceptionally(error -> { finish(error); return null; });
    }

    /** Valida las variantes desplazadas y su disponibilidad en el índice enviado a jugadores nuevos. */
    private void checkBeamModels() {
        Set<String> required = new HashSet<>();
        for (var asset : com.hypixel.hytale.server.core.asset.common.CommonAssetModule.get().getRequiredAssets()) required.add(asset.name);
        long bytes = 0;
        for (int section = 0; section < 10; section++) {
            for (int power = 0; power < 14; power++) {
                int start = section * 32 * 32;
                var model = BeaconBeamModels.core(start, start + (1 << power), 0);
                check(model.getBoundingBox().min.y == section * 32
                        && model.getBoundingBox().height() == (1 << power) / 32.0, "Wrong shifted core bounds");
                bytes += checkBeamGeometry(model, required);
            }
        }
        for (int offset = 1; offset < com.hypixel.hytale.math.util.ChunkUtil.HEIGHT; offset++) {
            int length = 4096 * 32 - offset * 32 - 5;
            var model = BeaconBeamModels.core(4096 * 32 - length, 4096 * 32, 0);
            check(model.getBoundingBox().min.y == offset + 5.0 / 32 && model.getBoundingBox().height() == length / 32.0
                    && model.getBoundingBox().max.y == 4096, "Wrong tail bounds");
            bytes += checkBeamGeometry(model, required);
        }
        results.add("Beam models: 140 finite variants and 319 tails retain exact geometry, fullbright, animation targets and login assets; total bytes=" + bytes);
    }

    private int checkBeamGeometry(com.hypixel.hytale.server.core.asset.type.model.config.Model model, Set<String> required) {
        var asset = com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.getByName(model.getModel());
        check(asset != null && required.contains(model.getModel()), "Beam asset missing from login index");
        var blob = asset.getBlob();
        if (model.getModel().contains("/Tails/") || model.getModel().contains("_Lift_")) {
            check(blob.isDone(), "Generated beam bytes were not retained");
        }
        // Los modelos empaquetados, a diferencia de las variantes en memoria, se leen de disco bajo demanda.
        byte[] bytes = blob.orTimeout(5, TimeUnit.SECONDS).join();
        var data = org.bson.BsonDocument.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        var root = data.getArray("nodes").getFirst().asDocument();
        double offset = root.getDocument("position").getNumber("y").doubleValue();
        double top = model.getBoundingBox().min.y * 64;
        var children = root.getArray("children");
        check(children.size() == 17, "Beam lost animation nodes");
        for (int i = 0; i < children.size(); i++) {
            var node = children.get(i).asDocument();
            check(node.getString("name").getValue().equals("Beam" + i), "Beam lost an animation target");
            var shape = node.getDocument("shape");
            if (!shape.getBoolean("visible").getValue()) continue;
            double half = shape.getDocument("stretch").getNumber("y").doubleValue()
                    * shape.getDocument("settings").getDocument("size").getNumber("y").doubleValue() / 2;
            double center = offset + node.getDocument("position").getNumber("y").doubleValue();
            check(Math.abs(center - half - top) < 0.000001 && shape.getString("shadingMode").getValue().equals("fullbright"),
                    "Beam geometry has a gap, overlap or wrong shading");
            top = center + half;
        }
        check(Math.abs(top / 64 - model.getBoundingBox().max.y) < 0.000001, "Beam geometry and bounds disagree");
        return bytes.length;
    }

    /** Publica los colores y las transiciones antes de ejercitar cambios visuales en el hilo del mundo. */
    private void prepareBeamTints(World world) {
        List<CompletableFuture<EntityEffect>> tints = new ArrayList<>();
        for (BlockType crystal : List.copyOf(BlockType.getAssetMap().getAssetMap().values())) {
            if (!crystal.getId().startsWith("Rock_Crystal_") || !crystal.getId().endsWith("_Small")) continue;
            put(world, 31, 33, 31, crystal.getId());
            for (var span : beamTrace(world)) {
                check(span.color() < 0 || BeaconCrystals.tint(span.color()).isDone(), "Native palette was not prepared at startup");
            }
            requestBeamTints(world, tints);
            put(world, 31, 33, 31, "Empty");
        }
        put(world, 31, 33, 31, "Rock_Crystal_Blue_Small");
        put(world, 31, 34, 31, "Rock_Crystal_Red_Small");
        requestBeamTints(world, tints);
        put(world, 31, 33, 31, "Rock_Crystal_Red_Small");
        put(world, 31, 34, 31, "Rock_Crystal_Blue_Small");
        requestBeamTints(world, tints);
        put(world, 31, 33, 31, "Rock_Crystal_White_Small");
        requestBeamTints(world, tints);
        put(world, 31, 33, 31, "Empty");
        put(world, 31, 34, 31, "Empty");
        whenTintsReady(world, tints, () -> after(world, () -> active(world)));
    }

    private List<BeaconCrystals.Span> beamTrace(World world) {
        return BeaconCrystals.trace(world.getChunkStore(), new BeaconStructures.Position(31, 32, 31));
    }

    private void requestBeamTints(World world, List<CompletableFuture<EntityEffect>> tints) {
        for (var span : beamTrace(world)) if (span.color() >= 0) tints.add(BeaconCrystals.tint(span.color()));
    }

    /** Comprueba los colores nativos, su mezcla y el retorno al haz sin cristales. */
    private void checkBeamCrystals(World world, Store<EntityStore> store) {
        var position = new BeaconStructures.Position(31, 32, 31);
        var core = beamLayer(store, "Beacon_Beam_Core");
        var halo = beamLayer(store, "Beacon_Beam_Halo");
        for (var layer : List.of(core, halo)) {
            check(store.getComponent(layer, Intangible.getComponentType()) != null, "Beam can intercept top-face interaction");
        }
        check(BlockType.getAssetMap().getAsset("Beacon_Block").getSupporting(0).containsKey(BlockFace.UP),
                "Beacon cannot support a crystal above it");
        int colors = 0;
        for (BlockType crystal : List.copyOf(BlockType.getAssetMap().getAssetMap().values())) {
            if (!crystal.getId().startsWith("Rock_Crystal_") || !crystal.getId().endsWith("_Small")) continue;
            check(crystal == nativeCrystals.get(crystal.getId()) && !crystal.getSupporting(0).containsKey(BlockFace.UP),
                    "Beacon changed native crystal placement/support: " + crystal.getId());
            put(world, 31, 33, 31, crystal.getId());
            checkHasteFromBases(world, 1, 11f);
            int color = beamTrace(world).getLast().color();
            if ("Rock_Crystal_White_Small".equals(crystal.getId())) {
                check(color == -1, "White crystal must preserve the original beam");
                checkDefaultBeam(store, List.of(core, halo));
            } else {
                check(color >= 0, "Crystal color missing: " + crystal.getId());
                checkBeamTint(store, List.of(core, halo), color);
            }
            put(world, 31, 33, 31, "Empty");
            colors++;
        }
        check(colors == 10, "Expected all ten native small crystal colors, found " + colors);
        put(world, 31, 33, 31, "Rock_Crystal_Blue_Small");
        check(beamTrace(world).getLast().color() == 0x3C5BBF, "Blue texture color changed");
        put(world, 31, 34, 31, "Rock_Crystal_Red_Small");
        checkHasteFromBases(world, 1, 11f);
        check(beamTrace(world).getLast().color() == 0x63456D, "Red/blue mix is not the RGB average");
        var mixed = checkBeamTint(store, List.of(core, halo), 0x63456D);
        checkBeamEffectReplication(store, mixed, 0x63456D);
        put(world, 31, 33, 31, "Rock_Crystal_Red_Small");
        put(world, 31, 34, 31, "Rock_Crystal_Blue_Small");
        check(beamTrace(world).getLast().color() == 0x63456D, "Crystal mixing depends on order");
        put(world, 31, 33, 31, "Rock_Crystal_White_Small");
        check(beamTrace(world).getLast().color() == 0x7084B3, "White crystal was omitted from a colored mix");
        put(world, 31, 34, 31, "Rock_Crystal_White_Small");
        check(beamTrace(world).getLast().color() == -1, "All-white column must keep the original beam");
        checkHasteFromBases(world, 1, 11f);
        checkDefaultBeam(store, List.of(core, halo));
        put(world, 31, 33, 31, "Iron_Block");
        check(beamTrace(world).getLast().color() == -1, "All-white column changed behind another block");
        put(world, 31, 33, 31, "Empty");
        put(world, 31, 34, 31, "Empty");
        checkHasteFromBases(world, 1, 11f);
        check(beamTrace(world).getLast().color() == -1, "Removing crystals did not restore the default beam");
        checkDefaultBeam(store, List.of(core, halo));
        check(core.isValid() && halo.isValid() && beamCount(store) == 2, "Tinting replaced or duplicated beam entities");
        check(BeaconCrystals.luminousColor(0) == 0xFFFFFF, "Missing hue must keep a luminous white fallback");
        checkElevatedCrystals(world, store, core, halo);
        results.add("Crystal beam: ten native colors, full-intensity RGB tint, white crystal keeps default beam, red+blue RGB average, order independence, native placement/support unchanged, intangible layers and default restoration; existing beam pair reused");
    }

    /** Los cristales laterales y separados afectan solo al tramo situado a partir de su altura. */
    private void checkElevatedCrystals(World world, Store<EntityStore> store, Ref<EntityStore> core, Ref<EntityStore> halo) {
        var registered = registeredSystem(BeaconStatusSystem.class);
        check(registered.instance().getIntervalSec() == 0.05f, "Visual update interval exceeds 50 ms");
        put(world, 31, 40, 31, "Iron_Block");
        putSideCrystal(world, 45, "Rock_Crystal_Blue_Small");
        int boundary = (45 - 32) * 32 - 27;
        var spans = beamTrace(world);
        check(spans.stream().filter(span -> span.end() <= boundary).allMatch(span -> span.color() == -1),
                "Crystal colored the beam below its block");
        check(spans.stream().filter(span -> span.start() >= boundary && span.end() <= boundary + 32).count() == 8,
                "Color transition must occupy eight steps inside the crystal block");
        check(spans.getLast().color() == 0x3C5BBF, "Sideways crystal was ignored across the air gap or solid block");
        registered.instance().delayedTick(0f, registered.index(), store);
        var pieces = checkBeamPlan(world, store);
        registered.instance().delayedTick(0f, registered.index(), store);
        check(pieces.equals(checkBeamPlan(world, store)), "Unchanged fast update recreated core pieces");
        putSideCrystal(world, 50, "Rock_Crystal_Red_Small");
        check(beamTrace(world).getLast().color() == 0x63456D, "Separated crystals failed to mix above the second block");
        registered.instance().delayedTick(0f, registered.index(), store);
        checkBeamPlan(world, store);
        for (int y : List.of(45, 50)) {
            put(world, 31, y, 31, "Empty");
            put(world, 32, y, 31, "Empty");
        }
        put(world, 31, 40, 31, "Empty");
        registered.instance().delayedTick(0f, registered.index(), store);
        checkDefaultBeam(store, List.of(core, halo));
        results.add("Beam sections: sideways crystals at y=45/50 across gaps and a solid block; height-local color, eight-step transitions, exact 4096-block coverage, aligned rotation, cached fast updates and cleanup");
    }

    private void putSideCrystal(World world, int y, String id) {
        put(world, 32, y, 31, "Iron_Block");
        var chunks = world.getChunkStore();
        var block = BlockType.getAssetMap().getAsset(id);
        var none = com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.None;
        int rotation = com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple.index(none, none,
                com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.Ninety);
        check(BlockOperations.setBlock(chunks, chunks.getChunkSectionReferenceAtBlock(31, y, 31), 31, y, 31,
                BlockType.getAssetMap().getIndex(id), block, rotation, 0, 0), "Sideways crystal placement failed");
    }

    /** Contrasta las entidades reales con el trazado: posición, altura, color, fase y recursos empaquetados. */
    private List<Ref<EntityStore>> checkBeamPlan(World world, Store<EntityStore> store) {
        List<Ref<EntityStore>> pieces = new ArrayList<>();
        store.forEachChunk(ModelComponent.getComponentType(), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) {
                if ("Beacon_Beam_Core".equals(entities.getComponent(i, ModelComponent.getComponentType()).getModel().getModelAssetId()))
                    pieces.add(entities.getReferenceTo(i));
            }
        });
        pieces.sort(Comparator.comparingDouble(ref -> store.getComponent(ref, TransformComponent.getComponentType()).getPosition().y
                + store.getComponent(ref, ModelComponent.getComponentType()).getModel().getBoundingBox().min.y));
        var spans = beamTrace(world);
        check(pieces.size() == spans.size() && beamCount(store) == spans.size() + 1, "Beam pieces disagree with its trace");
        float yaw = store.getComponent(pieces.getFirst(), TransformComponent.getComponentType()).getRotation().yaw();
        for (int i = 0; i < pieces.size(); i++) {
            var ref = pieces.get(i);
            var span = spans.get(i);
            var model = store.getComponent(ref, ModelComponent.getComponentType()).getModel();
            var transform = store.getComponent(ref, TransformComponent.getComponentType());
            check(transform.getPosition().y + model.getBoundingBox().min.y == 32 + 27.0 / 32 + span.start() / 32.0
                    && model.getBoundingBox().height() == (span.end() - span.start()) / 32.0,
                    "Beam piece has a gap, overlap or wrong height");
            check(transform.getPosition().y >= 0 && transform.getPosition().y < com.hypixel.hytale.math.util.ChunkUtil.HEIGHT,
                    "Beam entity anchor is outside the world");
            check((int) transform.getPosition().y / 32 == 1, "Beam anchor escaped the Beacon's active section");
            var bounds = store.getComponent(ref, BoundingBox.getComponentType()).getBoundingBox();
            check(bounds.min.y == model.getBoundingBox().min.y && bounds.max.y == model.getBoundingBox().max.y,
                    "Native model systems did not update beam bounds");
            check(transform.getRotation().yaw() == yaw && store.getComponent(ref, Intangible.getComponentType()) != null,
                    "Core pieces lost rotation phase or intangibility");
            check(com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.getByName(model.getModel()) != null,
                    "Finite beam model missing from Common assets: " + model.getModel());
            var effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
            check(span.color() < 0 || effects.hasEffect(EntityEffect.getAssetMap().getAsset(BeaconCrystals.tintId(span.color()))),
                    "Beam piece has the wrong tint");
        }
        check(spans.getFirst().start() == 0 && spans.getLast().end() == 4096 * 32, "Beam height changed");
        return pieces;
    }

    /** El blanco y la retirada de cristales dejan intacto el haz original, sin ningún tinte activo. */
    private void checkDefaultBeam(Store<EntityStore> store, List<Ref<EntityStore>> layers) {
        check(beamCount(store) == 2, "Default beam retained extra core pieces");
        for (var layer : layers) {
            var effects = store.getComponent(layer, EffectControllerComponent.getComponentType());
            check(effects.getActiveEffects().keySet().intStream().noneMatch(index ->
                    EntityEffect.getAssetMap().getAsset(index).getId().startsWith("Beacon_Beam_Tint_")), "Beam retained its old tint");
        }
    }

    /** Comprueba también el paquete de color que recibirá el cliente y la transparencia original del halo. */
    private Ref<EntityStore> checkBeamTint(Store<EntityStore> store, List<Ref<EntityStore>> layers, int color) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(BeaconCrystals.tintId(color));
        check(effect != null && effect == BeaconCrystals.tint(color).getNow(null), "Tint was not published/reused");
        var appearance = effect.getApplicationEffects().toPacket();
        check(appearance.entityTopTint == null && appearance.entityBottomTint == null,
                "Entity tint cannot recolor the white beam texture");
        ModelVFX vfx = ModelVFX.getAssetMap().getAsset(appearance.modelVFXId);
        check(vfx != null, "Beam color ModelVFX missing");
        var packet = vfx.toPacket();
        int red = Byte.toUnsignedInt(packet.postColor.red);
        int green = Byte.toUnsignedInt(packet.postColor.green);
        int blue = Byte.toUnsignedInt(packet.postColor.blue);
        check(Math.max(red, Math.max(green, blue)) == 255, "Crystal texture shadows dim the beam tint");
        Integer expected = Map.of(0x3C5BBF, 0x5079FF, 0x8B2F1B, 0xFF5631,
                0x491C3C, 0xFF61D1, 0x63456D, 0xE7A1FF).get(color);
        check(expected == null || expected == ((red << 16) | (green << 8) | blue), "Luminous tint changed the expected hue");
        check(packet.switchTo == com.hypixel.hytale.protocol.SwitchTo.PostColor
                && Math.abs(packet.postColorOpacity - 0.85f) < 0.0001f && packet.highlightThickness == 0
                && packet.animationDuration > 0 && packet.loopOption == com.hypixel.hytale.protocol.LoopOption.Loop
                && packet.animationRange.x() == 1 && packet.animationRange.y() == 1,
                "Beam PostColor needs a positive looping clock, full coverage and texture detail");
        List<Ref<EntityStore>> tinted = new ArrayList<>();
        store.forEachChunk(Query.and(ModelComponent.getComponentType(), EffectControllerComponent.getComponentType()), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) {
                if (entities.getComponent(i, EffectControllerComponent.getComponentType()).hasEffect(effect)) tinted.add(entities.getReferenceTo(i));
            }
        });
        check(!tinted.isEmpty(), "Core pieces did not receive the color ModelVFX");
        check(!store.getComponent(layers.getLast(), EffectControllerComponent.getComponentType()).hasEffect(effect),
                "Color ModelVFX would replace halo transparency");
        check(store.getComponent(layers.getLast(), EffectControllerComponent.getComponentType())
                .hasEffect(EntityEffect.getAssetMap().getAsset("Beacon_Beam_Glow")), "Tint removed halo transparency");
        var halo = ModelVFX.getAssetMap().getAsset("Beacon_Beam_Halo").toPacket();
        check(halo.switchTo == com.hypixel.hytale.protocol.SwitchTo.AlphaTransparency && halo.opacity == 0.15f,
                "Native halo rendering contract changed");
        return tinted.getFirst();
    }

    /** Ejercita el tracker real: cambio de color visible y snapshot para un observador nuevo. */
    private void checkBeamEffectReplication(Store<EntityStore> store, Ref<EntityStore> core, int color) {
        var visible = store.ensureAndGetComponent(core, EntityTrackerSystems.Visible.getComponentType());
        var receiver = new com.hypixel.hytale.server.core.receiver.IPacketReceiver() {
            @Override public void write(com.hypixel.hytale.protocol.ToClientPacket packet) { throw new AssertionError("Unexpected transport write"); }
            @Override public void writeNoCache(com.hypixel.hytale.protocol.ToClientPacket packet) { throw new AssertionError("Unexpected transport write"); }
        };
        var viewer = new EntityTrackerSystems.EntityViewer(64, receiver);
        viewer.visible.add(core);
        visible.visibleTo.put(actor, viewer);
        var tracker = new EntityTrackerSystems.EffectControllerSystem(EntityTrackerSystems.Visible.getComponentType(),
                EffectControllerComponent.getComponentType());
        try {
            for (boolean newObserver : List.of(false, true)) {
                viewer.updates.clear();
                if (newObserver) visible.newlyVisibleTo.put(actor, viewer);
                store.forEachChunk(tracker.getQuery(), (entities, buffer) -> {
                    for (int i = 0; i < entities.size(); i++) {
                        if (core.equals(entities.getReferenceTo(i))) tracker.tick(0f, i, entities, store, buffer);
                    }
                });
                var update = viewer.updates.get(core);
                check(update != null && Arrays.stream(update.toUpdatesArray())
                        .filter(com.hypixel.hytale.protocol.EntityEffectsUpdate.class::isInstance)
                        .map(com.hypixel.hytale.protocol.EntityEffectsUpdate.class::cast)
                        .flatMap(component -> Arrays.stream(component.entityEffectUpdates))
                        .anyMatch(change -> change.id == EntityEffect.getAssetMap().getIndex(BeaconCrystals.tintId(color))
                                && change.type == com.hypixel.hytale.protocol.EffectOp.Add && change.infinite),
                        "Native tracker omitted beam tint for " + (newObserver ? "new" : "existing") + " observer");
            }
        } finally {
            visible.visibleTo.remove(actor);
            visible.newlyVisibleTo.remove(actor);
        }
        results.add("Beam color replication: native tracker queues the infinite tint for existing and new observers");
    }

    private void checkActiveBeam(World world, Store<EntityStore> store) {
        checkBeamCrystals(world, store);
        Ref<EntityStore> core = beamLayer(store, "Beacon_Beam_Core");
        Ref<EntityStore> halo = beamLayer(store, "Beacon_Beam_Halo");
        check(Math.abs(store.getComponent(core, TransformComponent.getComponentType()).getPosition().y - (32 + 27.0/32)) < 0.000001, "Beam does not emerge from the blue core");
        var coreModel = store.getComponent(core, ModelComponent.getComponentType()).getModel();
        check(coreModel.getBoundingBox().max.y == 4096, "Beam bounds do not cover the distant extension");
        check(coreModel.getParticles() == null || coreModel.getParticles().length == 0, "Beam core should not have attached particles");
        var haloParticles = store.getComponent(halo, ModelComponent.getComponentType()).getModel().getParticles();
        check(haloParticles == null || haloParticles.length == 0, "Decorative beam should not have attached particle controllers");
        results.add("Beam origin=" + store.getComponent(core, TransformComponent.getComponentType()).getPosition().y + "; height=4096; no attached particle emitters");
        check("Beam_Flow".equals(store.getComponent(core, ActiveAnimationComponent.getComponentType()).getActiveAnimations()[AnimationSlot.Status.ordinal()]), "Beam flow animation is not active in the status slot");
        float yaw = store.getComponent(core, TransformComponent.getComponentType()).getRotation().yaw();
        checkWorldIsolation(world, () -> after(world, () -> rotated(world, core, halo, yaw)));
    }

    /** Abre otro mundo con un Beacon en las mismas coordenadas y lo cierra sin tocar el primero. */
    private void checkWorldIsolation(World original, Runnable continuation) {
        Store<EntityStore> originalStore = original.getEntityStore().getStore();
        var registered = registeredSystem(BeaconStatusSystem.class);
        var registration = registered.instance().getResourceRegistrations().stream()
                .filter(resource -> resource.typeClass() == BeaconWorldState.class).findFirst().orElseThrow();
        var resourceType = registration.resourceType();
        var originalState = originalStore.getResource(resourceType);
        var originalCore = beamLayer(originalStore, "Beacon_Beam_Core");
        var originalHalo = beamLayer(originalStore, "Beacon_Beam_Halo");
        check(registration.id() == null && registration.codec() == null, "Beacon runtime state became persistent");
        var cloned = ((BeaconWorldState) originalState).clone();
        check(cloned.levels.isEmpty() && cloned.jumps.isEmpty(), "Cloned Beacon state inherited levels or jump ownership");
        cloned.beams.update(Set.of(), originalStore);
        check(originalCore.isValid() && originalHalo.isValid(), "Cloned state retained the original beam references");
        createIsolationWorld()
                .thenAccept(other -> other.execute(() -> {
                    try {
                        Store<EntityStore> otherStore = other.getEntityStore().getStore();
                        check(otherStore.getResource(resourceType) != originalState, "Worlds share the Beacon resource");
                        registered.instance().delayedTick(1f, registered.index(), otherStore);
                        check(beamCount(otherStore) == 0, "Empty world inherited a beam");
                        var position = new BeaconStructures.Position(31, 32, 31);
                        layers(other, position, 1, 1, "Iron_Block");
                        put(other, position.x(), position.y(), position.z(), "Beacon_Block");
                        var chunks = other.getChunkStore();
                        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
                            chunks.getStore().tryRemoveComponent(chunks.getChunkSectionReferenceAtBlock(x * 32, y * 32, z * 32),
                                    ChunkStore.REGISTRY.getNonTickingComponentType());
                        }
                        registered.instance().delayedTick(1f, registered.index(), otherStore);
                        check(beamCount(otherStore) == 2, "Second world did not create its own beam pair");
                        var otherCore = beamLayer(otherStore, "Beacon_Beam_Core");
                        var otherHalo = beamLayer(otherStore, "Beacon_Beam_Halo");
                        check(!otherCore.equals(originalCore) && !otherHalo.equals(originalHalo), "Worlds share beam references");
                        // El cierre espera al hilo del mundo; nunca lo bloqueamos desde su propia tarea.
                        CompletableFuture.runAsync(() -> check(Universe.get().removeWorld(other.getName()), "Isolation world removal was cancelled"))
                                .whenComplete((ignored, error) -> {
                                    if (error != null) { finish(error); return; }
                                    original.execute(() -> {
                                        try {
                                            check(!otherCore.isValid() && !otherHalo.isValid(), "Closed world kept live beam references");
                                            check(originalCore.isValid() && originalHalo.isValid() && beamCount(originalStore) == 2,
                                                    "Updating or closing another world removed the original beam");
                                            results.add("World isolation: runtime resource has no persistence codec; clone starts empty; equal coordinates create distinct beam pairs; closing the second world preserves the first pair");
                                            continuation.run();
                                        } catch (Throwable failure) { finish(failure); }
                                    });
                                });
                    } catch (Throwable error) { finish(error); }
                }))
                .exceptionally(error -> { finish(error); return null; });
    }

    /** Carga las secciones de un mundo vacío antes de probar su estado independiente. */
    private CompletableFuture<World> createIsolationWorld() {
        WorldConfig config = new WorldConfig();
        config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setCanUnloadChunks(false);
        String name = "beacon_isolation_" + System.nanoTime();
        return Universe.get().makeWorld(name, Path.of(name), config)
                .thenCompose(other -> {
                    List<CompletableFuture<?>> sections = new ArrayList<>();
                    for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
                        sections.add(other.getChunkStore().getChunkSectionReferenceAsync(x, y, z));
                    }
                    return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new)).thenApply(ignored -> other);
                });
    }

    private void rotated(World world, Ref<EntityStore> core, Ref<EntityStore> halo, float previousYaw) {
        try {
            check(Math.abs(BeaconBeamRotationSystem.beamYaw(0f, 1f) - Math.PI / 4) <= .000001,
                    "Beam rotation changed its one-second angle");
            check(Math.abs(BeaconBeamRotationSystem.beamYaw(0f, 8f)) <= .000001,
                    "Beam rotation no longer completes a turn in eight seconds");
            results.add("Beam rotation: original one-second angle and eight-second period preserved");
            Store<EntityStore> store = world.getEntityStore().getStore();
            check(core.isValid() && halo.isValid() && beamCount(store) == 2, "Beam layers were replaced during rotation");
            TransformComponent transform = store.getComponent(core, TransformComponent.getComponentType());
            float yaw = transform.getRotation().yaw();
            check(Math.abs(yaw - previousYaw) > 0.2f, "Core is not rotating on native server ticks");
            // No client keeps Visible alive here; invoke the real packet builder on the current rotation.
            store.ensureComponent(core, EntityTrackerSystems.Visible.getComponentType());
            TransformSystems.EntityTrackerUpdate tracker = new TransformSystems.EntityTrackerUpdate();
            store.forEachChunk(tracker.getQuery(), (entities, buffer) -> {
                for (int i = 0; i < entities.size(); i++) if (core.equals(entities.getReferenceTo(i))) tracker.tick(0f, i, entities, store, buffer);
            });
            check(Math.abs(transform.getSentTransform().bodyOrientation.yaw - yaw) < 0.001f, "Native tracker did not replicate the core rotation");
            check(store.getComponent(halo, TransformComponent.getComponentType()).getRotation().yaw() == 0f, "Outer halo should remain stationary");
            results.add("Beam core yaw: " + previousYaw + " -> " + yaw + "; replicated yaw=" + transform.getSentTransform().bodyOrientation.yaw);
            refreshPowers(world);
            put(world, 30, 31, 30, "Empty");
            after(world, () -> inactive(world));
        } catch (Throwable error) { finish(error); }
    }

    private Ref<EntityStore> beamLayer(Store<EntityStore> store, String id) {
        List<Ref<EntityStore>> layers = new ArrayList<>();
        store.forEachChunk(ModelComponent.getComponentType(), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) {
                if (id.equals(entities.getComponent(i, ModelComponent.getComponentType()).getModel().getModelAssetId())) layers.add(entities.getReferenceTo(i));
            }
        });
        check(layers.size() == 1, "Expected one beam layer: " + id);
        return layers.getFirst();
    }

    private void compareHits(World world, String tool, int normalExpected, int fastExpected) {
        int normal = breakCobalt(world, tool, false);
        int fast = breakCobalt(world, tool, true);
        results.add(tool + ": " + normal + " -> " + fast + " hits");
        check(normal == normalExpected && fast == fastExpected, "Native cobalt hit counts: " + results);
    }

    private int breakCobalt(World world, String tool, boolean boost) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        String cobalt = BlockType.getAssetMap().getAssetMap().keySet().stream().filter(id -> id.startsWith("Ore_Cobalt_")).findFirst().orElseThrow();
        put(world, 34,33,31,cobalt);
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(34,33,31);
        forwardDamage = boost;
        ItemStack pickaxe = new ItemStack(tool,1);
        for (int hits=1;hits<30;hits++) {
            boolean broken = BlockHarvestUtils.performBlockDamage(actor,actor,new Vector3i(34,33,31),pickaxe,null,null,false,1f,4|1024|2048,false,section,store,chunks.getStore());
            if (hits == 1) {
                float health = chunks.getStore().getComponent(section,BlockHealthSection.getComponentType()).getHealth(34,33,31);
                results.add(tool + " " + boost + " first hit health=" + health);
            }
            if (broken) { forwardDamage=false; return hits; }
        }
        throw new AssertionError("Native cobalt did not break");
    }

    private class ProbeDamage extends EntityEventSystem<EntityStore,DamageBlockEvent> {
        ProbeDamage() { super(DamageBlockEvent.class); }
        @Override public Query<EntityStore> getQuery() { return TransformComponent.getComponentType(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> entities, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
            if (!forwardDamage || !entities.getReferenceTo(index).equals(actor)) return;
            registeredSystem(BeaconMiningSystem.class).instance().handle(index, entities, store, buffer, event);
        }
    }

    private void inactive(World world) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            check(BeaconStructures.findActive(world.getChunkStore()).isEmpty(), "Broken base remained active");
            check(beamCount(store) == 0, "Native beam did not disappear after breaking a corner");
            check(damage(store, "Tool_Pickaxe_Crude") == 12f, "Residual mining bonus disappeared immediately after base removal");
            var effects = store.getComponent(actor, EffectControllerComponent.getComponentType());
            var haste = effects.getActiveEffects().get(EntityEffect.getAssetMap().getIndex(BeaconPower.HASTE.effectId(1)));
            store.forEachChunk(TransformComponent.getComponentType(), (entities, buffer) -> {
                for (int i = 0; i < entities.size(); i++) if (actor.equals(entities.getReferenceTo(i)))
                    haste.tick(buffer, actor, EntityEffect.getAssetMap().getAsset(BeaconPower.HASTE.effectId(1)), store.getComponent(actor, EntityStatMap.getComponentType()), 11f);
            });
            check(damage(store, "Tool_Pickaxe_Crude") == 10f, "Expired haste still changed mining damage");
            results.add("Native haste: 11-second residual duration survives base removal and stops after expiry");
            var position = new BeaconStructures.Position(31,32,31);
            var previousBlock = BeaconSelection.blockEntity(world.getChunkStore(), position, false);
            put(world,31,32,31,"Empty");
            put(world,31,32,31,"Beacon_Block");
            check(previousBlock != null && !previousBlock.isValid(), "Breaking the beacon kept its paid block entity");
            check(!BeaconOpenInteraction.validTarget(actor, store, position, previousBlock), "Page accepted a broken Beacon reference");
            check(BeaconSelection.get(world.getChunkStore(), position) == null, "Replacement beacon inherited the old payment");
            var replacement = BeaconSelection.blockEntity(world.getChunkStore(), position, true);
            check(replacement != null && BeaconOpenInteraction.validTarget(actor, store, position, replacement), "Page rejected the replacement Beacon reference");
            BeaconAttackSpeedSystem.updatePrimary(actor,store,"Beacon_Haste_Pickaxe_Pickaxe_Attack");
            Holder<EntityStore> removed = EntityStore.REGISTRY.newHolder();
            store.removeEntity(actor,removed,RemoveReason.REMOVE);
            check(removed.getComponent(Interactions.getComponentType()).getInteractionId(InteractionType.Primary)==null, "Temporary speed override survived entity removal");
            check(!BeaconOpenInteraction.validTarget(actor, store, position, replacement), "Page accepted a removed actor reference");
            results.add("Page target lifecycle: broken block and removed actor rejected; replacement block has its own valid reference");
            checkReloadedSelection(world, position);
        } catch (Throwable error) { finish(error); }
    }

    /** Guarda y relee la selección desde el almacenamiento nativo tras cerrar el mundo desechable. */
    private void checkReloadedSelection(World world, BeaconStructures.Position position) {
        var block = BeaconSelection.blockEntity(world.getChunkStore(), position, true);
        check(block != null, "Persistent block entity missing before world close");
        BeaconSelection.save(block, BeaconPower.HASTE, "UPGRADE");
        Path path = world.getSavePath();
        String name = world.getName();
        // removeWorld espera al guardado; debe ejecutarse fuera del hilo que necesita detener.
        CompletableFuture.runAsync(() -> check(Universe.get().removeWorld(name), "World removal was cancelled"))
                .thenCompose(ignored -> Universe.get().getWorldConfigProvider().load(path, name))
                .thenCompose(config -> Universe.get().makeWorld(name, path, config))
                .thenCompose(reloaded -> reloaded.getChunkStore().getChunkSectionReferenceAsync(0, 1, 0).thenApply(section -> reloaded))
                .thenAccept(reloaded -> reloaded.execute(() -> {
                    try {
                        check(reloaded != world && reloaded.getChunkStore() != world.getChunkStore(), "World storage was not recreated");
                        var chunks = reloaded.getChunkStore();
                        var section = chunks.getChunkSectionReferenceAtBlock(position.x(), position.y(), position.z());
                        check(section != null && section.isValid(), "Saved section did not load");
                        // Hytale materializa los componentes guardados al activar la sección cargada.
                        chunks.getStore().tryRemoveComponent(section, ChunkStore.REGISTRY.getNonTickingComponentType());
                        var saved = BeaconSelection.get(reloaded.getChunkStore(), position);
                        check(saved != null && saved.matches(BeaconPower.HASTE, "UPGRADE"), "Paid selection lost after world close/reload");
                        check(saved.strength(BeaconPower.HASTE, 4) == 2 && saved.strength(BeaconPower.HASTE, 3) == 1,
                                "Reloaded selection lost upgrade/downgrade behavior");
                        results.add("Persistence: world closed and recreated from native storage; Primary=HASTE, Secondary=UPGRADE and paid behavior survived");
                        checksPassed(reloaded);
                    } catch (Throwable error) { finish(error); }
                }))
                .exceptionally(error -> { finish(error); return null; });
    }

    private void refreshPowers(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        registeredSystem(BeaconStatusSystem.class).instance()
                .refreshPowers(actor, store.getComponent(actor, TransformComponent.getComponentType()).getPosition(), store, store);
    }

    private float damage(Store<EntityStore> store, String itemId) {
        BeaconMiningSystem mining = registeredSystem(BeaconMiningSystem.class).instance();
        BlockType ore = BlockType.getAssetMap().getAsset("Ore_Iron_Stone");
        check(ore != null, "Native ore block fixture missing");
        DamageBlockEvent event = new DamageBlockEvent(new ItemStack(itemId,1), new Vector3i(34,33,31), ore, 1f, 10f);
        store.forEachChunk(Query.and(EffectControllerComponent.getComponentType(), TransformComponent.getComponentType()), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) if (entities.getReferenceTo(i).equals(actor)) mining.handle(i, entities, store, buffer, event);
        });
        return event.getDamage();
    }

    /** Adaptación al registro interno de 0.7.4; se consulta solo desde el hilo del mundo de la fixture. */
    protected final <T extends ISystem<EntityStore>> RegisteredSystem<T> registeredSystem(Class<T> type) {
        var systems = EntityStore.REGISTRY._internal_getData();
        for (int i = 0; i < systems.getSystemSize(); i++) {
            var system = systems.getSystem(i);
            if (type.isInstance(system)) return new RegisteredSystem<>(i, type.cast(system));
        }
        throw new AssertionError("Native system not registered: " + type.getSimpleName());
    }

    protected record RegisteredSystem<T>(int index, T instance) {}

    /** La fixture funcional termina aquí; la variante de profiling continúa con sus escenarios. */
    protected void checksPassed(World world) { finish(null); }

    private int beamCount(Store<EntityStore> store) {
        int[] count = {0};
        store.forEachChunk(ModelComponent.getComponentType(), (entities, buffer) -> {
            for(int i=0;i<entities.size();i++) if(entities.getComponent(i,ModelComponent.getComponentType()).getModel().getModelAssetId().startsWith("Beacon_Beam_")) count[0]++;
        });
        return count[0];
    }

    private void put(World world, int x, int y, int z, String type) {
        ChunkStore chunks = world.getChunkStore();
        int id = "Empty".equals(type) ? 0 : BlockType.getAssetMap().getIndex(type);
        check(id >= 0, "Missing block: " + type);
        check(BlockOperations.setBlock(chunks, chunks.getChunkSectionReferenceAtBlock(x,y,z), x,y,z,id,BlockType.getAssetMap().getAsset(id),0,0,0), "Block placement failed: " + type);
    }

    private void after(World world, Runnable action) {
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            try { world.execute(action); } catch (Throwable error) { finish(error); }
        });
    }

    private void check(boolean success, String message) { if (!success) throw new AssertionError(message); }

    protected final void finish(Throwable error) {
        if (!finished.compareAndSet(false, true)) return;
        try {
            if (error != null) error.printStackTrace();
            Files.writeString(Path.of("smoke-result.txt"), (error == null ? "PASS" : "FAIL: " + error) + "\n" + String.join("\n", results));
        } catch (Exception writeError) { writeError.printStackTrace(); }
        HytaleServer.get().shutdownServer();
    }
}
