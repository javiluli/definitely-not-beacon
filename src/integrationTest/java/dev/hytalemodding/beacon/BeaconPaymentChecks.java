package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Comprueba acceso, inventarios, pagos y eventos de página con un actor sin cliente. */
final class BeaconPaymentChecks {
    private final Ref<EntityStore> actor;
    private final List<String> results;

    BeaconPaymentChecks(Ref<EntityStore> actor, List<String> results) {
        this.actor = actor;
        this.results = results;
    }

    /** Comprueba que la UI solo resuelve los inventarios del actor y su propio depósito. */
    void checkInventorySections(Store<EntityStore> store) {
        BeaconPaymentWindow window = new BeaconPaymentWindow();
        window.setId(7);
        var storage = store.getComponent(actor, InventoryComponent.Storage.getComponentType()).getInventory();
        var hotbar = store.getComponent(actor, InventoryComponent.Hotbar.getComponentType()).getInventory();
        check(window.containerForGrid(actor, store, "Storage") == storage
                && window.containerForSection(actor, store, InventoryComponent.STORAGE_SECTION_ID) == storage,
                "Storage grid or section resolved another container");
        check(window.containerForGrid(actor, store, "Hotbar") == hotbar
                && window.containerForSection(actor, store, InventoryComponent.HOTBAR_SECTION_ID) == hotbar,
                "Hotbar grid or section resolved another container");
        check(window.containerForGrid(actor, store, "Payment") == window.getItemContainer()
                && window.containerForSection(actor, store, window.getId()) == window.getItemContainer(),
                "Payment grid or section resolved another deposit");
        check(window.containerForSection(actor, store, null) == null && window.containerForSection(actor, store, 8) == null,
                "Missing or foreign inventory section was accepted");
        check(window.containerForGrid(actor, store, null) == null && window.containerForGrid(actor, store, "Unknown") == null,
                "Missing or unknown inventory grid was accepted");
        results.add("Inventory sections: storage, hotbar and own deposit resolve correctly; missing, foreign and unknown inputs are rejected");
    }

    /** Prueba el pago y la devolución con las mismas pilas que mueve la UI nativa. */
    void checkPayment(Store<EntityStore> store) {
        var hotbar = store.getComponent(actor, InventoryComponent.Hotbar.getComponentType()).getInventory();
        var storage = store.getComponent(actor, InventoryComponent.Storage.getComponentType()).getInventory();
        BeaconPaymentWindow window = new BeaconPaymentWindow();
        var payment = window.getItemContainer();
        check(!ItemStack.isEmpty(payment.addItemStack(new ItemStack("Ore_Iron", 1)).getRemainder()), "Payment accepted ore");
        var metadata = BsonDocument.parse("{\"BeaconTest\":\"retained\"}");
        hotbar.setItemStackForSlot((short)0, new ItemStack("Ingredient_Bar_Iron", 5, metadata));
        hotbar.setItemStackForSlot((short)1, new ItemStack("Ore_Iron", 1));
        check(!window.transfer(hotbar, -1, payment, 0, 1, "Ingredient_Bar_Iron"), "Negative source accepted");
        check(!window.transfer(hotbar, 0, payment, 1, 1, "Ingredient_Bar_Iron"), "Invalid target accepted");
        check(!window.transfer(hotbar, 0, payment, 0, 6, "Ingredient_Bar_Iron"), "Excess quantity accepted");
        check(!window.transfer(hotbar, 0, payment, 0, 0, "Ingredient_Bar_Iron"), "Zero quantity accepted");
        check(!window.transfer(hotbar, 0, payment, 0, 1, "Ingredient_Bar_Gold"), "Stale item accepted");
        check(!window.transfer(hotbar, 1, payment, 0, 1, "Ore_Iron"), "Ore drag accepted");
        check(window.charge() == null, "Empty deposit charged a payment");
        check(window.transfer(hotbar, 0, payment, 0, 5, "Ingredient_Bar_Iron"), "Stack drag failed");
        check(ItemStack.isEmpty(hotbar.getItemStack((short)0)) && window.payment().getQuantity() == 5, "Drag duplicated a stack");
        check(window.payment().getMetadata().equals(metadata), "Drag lost metadata");
        check(!window.transfer(hotbar, 1, payment, 0, 1, "Ore_Iron") && window.payment().getQuantity() == 5, "Invalid swap changed payment");
        check(window.transfer(payment, 0, storage, 4, 2, "Ingredient_Bar_Iron"), "Partial withdrawal failed");
        check(window.payment().getQuantity() == 3 && storage.getItemStack((short)4).getQuantity() == 2, "Partial withdrawal lost items");
        ItemStack paid = window.charge();
        check(paid != null && paid.getQuantity() == 1 && paid.getMetadata().equals(metadata) && window.payment().getQuantity() == 2, "Charge did not consume exactly one original ingot");
        window.onClose0(actor, store);
        window.onClose0(actor, store);
        int remaining = 0;
        for (var container : List.of(hotbar, storage)) for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && stack.getItemId().equals("Ingredient_Bar_Iron")) remaining += stack.getQuantity();
        }
        check(remaining == 4 && ItemStack.isEmpty(window.payment()), "Close did not refund the unused ingots exactly once");
        check(window.charge() == null && !window.transfer(storage, 4, payment, 0, 1, "Ingredient_Bar_Iron"), "Closed page accepted an action");
        BeaconPaymentWindow inventoryWindow = new BeaconPaymentWindow();
        check(inventoryWindow.transfer(hotbar, 1, storage, 2, 1, "Ore_Iron"), "Normal inventory rearrangement failed");
        check(inventoryWindow.transfer(storage, 2, hotbar, 1, 1, "Ore_Iron"), "Normal inventory reverse move failed");
        inventoryWindow.onClose0(actor, store);
        checkFullInventoryRefund(store, hotbar, storage);
        results.add("Native drag: stacks, partial withdrawal, metadata, inventory rearrangement; invalid/stale quantities and ore rejected");
        results.add("Payment: consumes one ingot, refunds the rest exactly once, rejects closed-page actions; full inventory drops all 3 ingots");
    }

    private void checkFullInventoryRefund(Store<EntityStore> store, ItemContainer hotbar, ItemContainer storage) {
        for (var container : List.of(hotbar, storage)) for (short slot = 0; slot < container.getCapacity(); slot++)
            container.setItemStackForSlot(slot, new ItemStack("Ingredient_Bar_Iron", 64));
        BeaconPaymentWindow full = new BeaconPaymentWindow();
        full.getItemContainer().addItemStack(new ItemStack("Ingredient_Bar_Cobalt", 3));
        full.onClose0(actor, store);
        int[] dropped = {0};
        store.forEachChunk(ItemComponent.getComponentType(), (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) {
                ItemStack stack = entities.getComponent(i, ItemComponent.getComponentType()).getItemStack();
                if (stack.getItemId().equals("Ingredient_Bar_Cobalt")) dropped[0] += stack.getQuantity();
            }
        });
        check(dropped[0] == 3, "Full inventory lost refunded ingots");
        for (var container : List.of(hotbar, storage)) for (short slot = 0; slot < container.getCapacity(); slot++) container.removeItemStackFromSlot(slot);
    }

    /** Construye la página pagada y comprueba sus comandos, eventos y cierre sin cliente. */
    void checkPage(Store<EntityStore> store, Ref<ChunkStore> block, BeaconStructures.Position position) {
        checkTarget(store, block, position);
        BeaconPaymentWindow deposit = new BeaconPaymentWindow();
        deposit.getItemContainer().setItemStackForSlot((short) 0, new ItemStack("Ingredient_Bar_Iron", 2));
        BeaconPage page = new BeaconPage(null, position, block, deposit);
        check(page.getLifetime() == CustomPageLifetime.CanDismissOrCloseThroughInteraction,
                "Beacon page must allow native interaction-key dismissal");
        var commands = new UICommandBuilder();
        var events = new UIEventBuilder();
        page.build(actor, commands, events, store);
        checkPageCommands(store, deposit, commands);
        deposit.getItemContainer().removeItemStackFromSlot((short) 0);
        checkPageEvents(events);
        checkPageDismissal(store, deposit, page);
        check(BeaconSelection.get(store.getExternalData().getWorld().getChunkStore(), position)
                .matches(BeaconPower.HASTE, BeaconRules.SECONDARY_NONE), "Closed-page events changed the paid selection");
        results.add("Beacon page: native interaction-key dismissal enabled; no Cancel action; three drag grids and numeric payloads decode; dismissal refunds exactly once");
        results.add("Page commands: native capacities, activatable payment with two ingots, paid selection cannot be charged twice; tier one secondaries stay locked; tier four pending choice and translated status preserved");
        results.add("Closed page: delayed OK and DROP JSON events leave the refunded inventory and paid selection unchanged");
    }

    /** Ejerce la guarda de apertura y acciones; restaura la posición del actor antes de seguir. */
    private void checkTarget(Store<EntityStore> store, Ref<ChunkStore> block, BeaconStructures.Position position) {
        var transform = store.getComponent(actor, TransformComponent.getComponentType());
        Vector3d original = new Vector3d(transform.getPosition());
        Vector3d center = new Vector3d(position.x() + .5, position.y() + .5, position.z() + .5);
        try {
            for (Vector3d direction : List.of(new Vector3d(1, 0, 0), new Vector3d(-1, 0, 0),
                    new Vector3d(0, 1, 0), new Vector3d(0, -1, 0), new Vector3d(0, 0, 1), new Vector3d(0, 0, -1))) {
                transform.setPosition(new Vector3d(direction).mul(8).add(center));
                check(BeaconOpenInteraction.validTarget(actor, store, position, block), "Page rejected its eight-block distance boundary");
                transform.setPosition(new Vector3d(direction).mul(8.001).add(center));
                check(!BeaconOpenInteraction.validTarget(actor, store, position, block), "Page accepted an actor beyond eight blocks");
            }
            transform.setPosition(new Vector3d(center).add(6, 0, 6));
            check(!BeaconOpenInteraction.validTarget(actor, store, position, block), "Page distance became a cube instead of a sphere");
            transform.setPosition(center);
            var otherPosition = new BeaconStructures.Position(position.x() + 1, position.y(), position.z());
            check(!BeaconOpenInteraction.validTarget(actor, store, otherPosition, block), "Page accepted another block position");
        } finally {
            transform.setPosition(original);
        }
        results.add("Page target: eight-block boundary included on all six axes; outside sphere and mismatched block position rejected");
    }

    private void checkPageCommands(Store<EntityStore> store, BeaconPaymentWindow deposit, UICommandBuilder commands) {
        check(uiValue(commands, "#RegenerationIcon.AssetPath").asString().getValue().equals("UI/StatusEffects/AddHealth/Tiny.png"),
                "Regeneration panel does not use the native totem AssetImage path");
        check(Arrays.stream(commands.getCommands()).anyMatch(command -> "#BeaconTitle.TextSpans".equals(command.selector)
                && command.data.contains("beacon.items.Beacon_Block.name")), "Beacon title lost its translation key");
        var hasteTooltip = Message.CODEC.decode(uiValue(commands, "#HASTE.TooltipTextSpans"), new ExtraInfo());
        check(BeaconTextChecks.localized(hasteTooltip.getFormattedMessage(), "es-ES")
                .equals("Prisa I: +20% de minado por golpe; +40% de cadencia de ataque · Activado"),
                "Page tooltip lost its translation, balance or paid indicator");
        check(Arrays.stream(commands.getCommands()).anyMatch(command -> "#HASTE.Style".equals(command.selector)
                && command.data.contains("ActivePowerStyle")), "Paid power did not receive the green active style");
        check(Arrays.stream(commands.getCommands()).noneMatch(command -> command.selector != null
                && command.selector.startsWith("#Selection")), "Removed selection summary still receives UI commands");
        check(uiValue(commands, "#Storage.Slots").asArray().size() == 36
                && uiValue(commands, "#Hotbar.Slots").asArray().size() == 9
                && uiValue(commands, "#Payment.Slots").asArray().size() == 1, "Page inventory capacities changed");
        check(uiValue(commands, "#Payment.Slots").asArray().get(0).asDocument().getBoolean("IsActivatable").getValue(),
                "Payment slot no longer accepts native drag gestures");
        var shownPayment = ItemStack.CODEC.decode(uiValue(commands, "#Payment.Slots").asArray().get(0).asDocument().get("ItemStack"), new ExtraInfo());
        check(shownPayment.getQuantity() == 2 && "Ingredient_Bar_Iron".equals(shownPayment.getItemId()), "Page changed the shown payment");
        check(uiValue(commands, "#OK.Disabled").asBoolean().getValue(), "Already paid selection became payable with a deposit");
        check(uiValue(commands, "#REGENERATION.Disabled").asBoolean().getValue()
                && uiValue(commands, "#UPGRADE.Disabled").asBoolean().getValue(), "Tier one unlocked a secondary power");
        var pending = new UICommandBuilder();
        new BeaconPageView(deposit).update(pending, store, actor, 4, null,
                BeaconPower.JUMP_BOOST, BeaconRules.SECONDARY_UPGRADE,
                Message.translation("beacon.ui.paymentRequired"));
        check(!uiValue(pending, "#OK.Disabled").asBoolean().getValue()
                && !uiValue(pending, "#UPGRADE.Disabled").asBoolean().getValue(), "Valid pending choice became disabled");
        check(uiValue(pending, "#UPGRADE.Style").asDocument().toJson().contains("SelectedPowerStyle"),
                "Unpaid secondary lost its pending style");
        var status = Message.CODEC.decode(uiValue(pending, "#Status.TextSpans"), new ExtraInfo());
        check(uiValue(pending, "#Status.Visible").asBoolean().getValue()
                && BeaconTextChecks.localized(status.getFormattedMessage(), "en-US").equals("Place an ingot in the slot before accepting."),
                "Page status is not visible or translatable");
        var clearedStatus = Message.CODEC.decode(uiValue(commands, "#Status.TextSpans"), new ExtraInfo());
        check(Message.empty().getFormattedMessage().equals(clearedStatus.getFormattedMessage())
                && !uiValue(commands, "#Status.Visible").asBoolean().getValue(), "Page retained an old status");
    }

    private void checkPageEvents(UIEventBuilder events) {
        Set<String> actions = new HashSet<>();
        Set<String> grids = new HashSet<>();
        for (var event : events.getEvents()) {
            var action = BeaconPage.Action.CODEC.decode(BsonDocument.parse(event.data), new ExtraInfo());
            actions.add(action.value);
            if ("DROP".equals(action.value)) {
                check(event.selector.equals("#" + action.grid), "Drop targets the wrong grid");
                check(!event.locksInterface, "Drop must not lock the native drag lifecycle");
                grids.add(action.grid);
            }
        }
        check(actions.containsAll(Set.of("OK", "DROP", "SORT", "HASTE", "REGENERATION", "UPGRADE")), "Missing unified UI actions");
        check(!actions.contains("CANCEL") && !actions.contains("NONE") && !actions.contains("DEPOSIT") && !actions.contains("PAYMENT"),
                "Removed Cancel, automatic payment or secondary action still bound");
        check(grids.equals(Set.of("Storage", "Hotbar", "Payment")), "Missing drag target");
        var drop = BeaconPage.Action.CODEC.decode(BsonDocument.parse("""
                {"Action":"DROP","Grid":"Payment","SlotIndex":0,"SourceInventorySectionId":-1,
                 "SourceSlotId":2,"ItemStackQuantity":5,"ItemStackId":"Ingredient_Bar_Iron"}
                """), new ExtraInfo());
        check(drop.sourceSection == -1 && drop.sourceSlot == 2 && drop.slot == 0 && drop.quantity == 5, "Native integer drag payload did not decode");
    }

    private void checkPageDismissal(Store<EntityStore> store, BeaconPaymentWindow deposit, BeaconPage page) {
        var hotbar = store.getComponent(actor, InventoryComponent.Hotbar.getComponentType()).getInventory();
        var inventory = BeaconPaymentWindow.inventory(actor, store);
        hotbar.setItemStackForSlot((short) 0, new ItemStack("Ingredient_Bar_Iron", 3));
        check(deposit.transfer(inventory, 0, deposit.getItemContainer(), 0, 1, "Ingredient_Bar_Iron"), "Could not prepare page dismissal");
        page.onDismiss(actor, store);
        page.onDismiss(actor, store);
        page.handleDataEvent(actor, store, "{\"Action\":\"OK\"}");
        page.handleDataEvent(actor, store, """
                {"Action":"DROP","Grid":"Payment","SlotIndex":0,"SourceInventorySectionId":-1,
                 "SourceSlotId":0,"ItemStackQuantity":1,"ItemStackId":"Ingredient_Bar_Iron"}
                """);
        check(hotbar.getItemStack((short) 0).getQuantity() == 3 && ItemStack.isEmpty(deposit.payment()), "Page dismissal did not refund exactly once");
        check(!deposit.transfer(inventory, 0, deposit.getItemContainer(), 0, 1, "Ingredient_Bar_Iron"), "Page dismissal did not close the deposit");
        hotbar.removeItemStackFromSlot((short) 0);
    }

    // El builder serializa el valor asignado en la clave "0" de cada comando.
    private BsonValue uiValue(UICommandBuilder commands, String selector) {
        var command = Arrays.stream(commands.getCommands()).filter(value -> selector.equals(value.selector)).findFirst().orElseThrow();
        return BsonDocument.parse(command.data).get("0");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
