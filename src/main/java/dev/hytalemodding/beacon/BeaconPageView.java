package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/** Construye los comandos visuales de selección e inventario usando los contenedores del depósito. */
final class BeaconPageView {
    private final BeaconPaymentWindow deposit;

    BeaconPageView(BeaconPaymentWindow deposit) {
        this.deposit = deposit;
    }

    /** Enlaza las cuadrículas con sus secciones nativas y ajusta el panel al inventario real. */
    void build(UICommandBuilder commands, Ref<EntityStore> ref, Store<EntityStore> store) {
        commands.append("Pages/Beacon.ui");
        commands.set("#BeaconTitle.TextSpans", Message.translation("beacon.items.Beacon_Block.name"));
        commands.set("#RegenerationIcon.AssetPath", EntityEffect.getAssetMap().getAsset(BeaconPower.REGENERATION.effectId(1)).getStatusEffectIcon());
        commands.set("#Storage.InventorySectionId", InventoryComponent.STORAGE_SECTION_ID);
        commands.set("#Hotbar.InventorySectionId", InventoryComponent.HOTBAR_SECTION_ID);
        commands.set("#Payment.InventorySectionId", deposit.getId());
        int storageRows = (deposit.containerForGrid(ref, store, "Storage").getCapacity() + 8) / 9;
        int hotbarRows = (deposit.containerForGrid(ref, store, "Hotbar").getCapacity() + 8) / 9;
        Anchor inventoryAnchor = new Anchor();
        inventoryAnchor.setWidth(Value.of(715));
        inventoryAnchor.setHeight(Value.of(68 + (storageRows + hotbarRows) * 76 + 8));
        commands.setObject("#InventoryPanel.Anchor", inventoryAnchor);
    }

    /**
     * Refleja la elección provisional y distingue las mejoras pagadas de las aún pendientes.
     * @param message aviso traducible; {@code null} limpia y oculta el aviso anterior
     */
    void update(UICommandBuilder commands, Store<EntityStore> store, Ref<EntityStore> ref, int tier,
                @Nullable BeaconSelection saved, BeaconPower primary, String secondary, @Nullable Message message) {
        boolean activated = saved != null && saved.matches(primary, secondary) && BeaconRules.validSelection(tier, primary, secondary);
        commands.set("#Status.TextSpans", message == null ? Message.empty() : message);
        commands.set("#Status.Visible", message != null);
        updateInventories(commands, store, ref);
        commands.set("#OK.Disabled", !BeaconRules.validSelection(tier, primary, secondary)
                || ItemStack.isEmpty(deposit.payment()) || activated);
        updatePowerButtons(commands, tier, saved, primary, secondary);
    }

    /** Conserva estilos, desbloqueos y tooltips sin cambiar la selección del jugador. */
    private void updatePowerButtons(UICommandBuilder commands, int tier, @Nullable BeaconSelection saved,
                                    BeaconPower primary, String secondary) {
        for (BeaconPower power : BeaconPower.values()) {
            if (power == BeaconPower.REGENERATION) continue;
            String id = power.name().replace("_", "");
            commands.set("#Upgrade" + id + ".Visible", primary == power);
            commands.set("#" + id + ".Disabled", tier < power.tier);
            boolean paid = saved != null && saved.strength(power, tier) > 0;
            commands.set("#" + id + ".Style", Value.ref("Pages/Beacon.ui", paid ? "ActivePowerStyle" : primary == power ? "SelectedPowerStyle" : "PowerStyle"));
            commands.set("#" + id + ".TooltipTextSpans", BeaconText.powerTooltip(power, 1, paid));
        }
        for (String option : new String[]{BeaconPower.REGENERATION.name(), BeaconRules.SECONDARY_UPGRADE}) {
            commands.set("#" + option + ".Disabled", tier < 4);
            boolean paid = saved != null && (option.equals(BeaconPower.REGENERATION.name())
                    ? saved.strength(BeaconPower.REGENERATION, tier) > 0 : saved.strength(primary, tier) == 2);
            commands.set("#" + option + ".Style", Value.ref("Pages/Beacon.ui", paid ? "ActivePowerStyle" : secondary.equals(option) ? "SelectedPowerStyle" : "PowerStyle"));
            commands.set("#" + option + ".TooltipTextSpans", option.equals(BeaconPower.REGENERATION.name())
                    ? BeaconText.powerTooltip(BeaconPower.REGENERATION, 1, false) : BeaconText.powerTooltip(primary, 2, false));
        }
    }

    /** Publica ranuras activables para que Hytale gestione los gestos de arrastre. */
    private void updateInventories(UICommandBuilder commands, Store<EntityStore> store, Ref<EntityStore> ref) {
        for (String grid : new String[]{"Storage", "Hotbar", "Payment"}) {
            ItemContainer inventory = deposit.containerForGrid(ref, store, grid);
            ItemGridSlot[] slots = new ItemGridSlot[inventory.getCapacity()];
            int activeSlot = grid.equals("Hotbar") ? store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()).getActiveSlot() : -1;
            for (short slot = 0; slot < slots.length; slot++) {
                slots[slot] = new ItemGridSlot(inventory.getItemStack(slot));
                slots[slot].setActivatable(true);
                if (slot == activeSlot) slots[slot].setOverlay(Value.ref("Pages/Beacon.ui", "SelectedSlot"));
            }
            commands.set("#" + grid + ".Slots", slots);
        }
    }
}
