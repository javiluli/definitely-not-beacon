package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SortType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/** Coordina los eventos de la página, la selección provisional y su activación pagada. */
final class BeaconPage extends InteractiveCustomUIPage<BeaconPage.Action> {
    private static final HytaleLogger LOGGER = HytaleLogger.get("Definitely Not Beacon");
    private final BeaconStructures.Position position;
    private final Ref<ChunkStore> block;
    private final BeaconPaymentWindow deposit;
    private final BeaconPageView view;
    private BeaconPower primary = BeaconPower.HASTE;
    private String secondary = BeaconRules.SECONDARY_NONE;
    private boolean dismissed;

    BeaconPage(PlayerRef player, BeaconStructures.Position position, Ref<ChunkStore> block, BeaconPaymentWindow deposit) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Action.CODEC);
        this.position = position;
        this.block = block;
        this.deposit = deposit;
        this.view = new BeaconPageView(deposit);
        BeaconSelection saved = BeaconSelection.get(block.getStore().getExternalData(), position);
        if (saved != null) {
            for (BeaconPower power : BeaconPower.values()) if (power.name().equals(saved.primary)) primary = power;
            secondary = saved.secondary;
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        view.build(commands, ref, store);
        for (BeaconPower power : BeaconPower.values()) if (power != BeaconPower.REGENERATION) bind(events, power.name());
        for (String action : new String[]{BeaconPower.REGENERATION.name(), BeaconRules.SECONDARY_UPGRADE, "OK", "SORT"}) bind(events, action);
        for (String grid : new String[]{"Storage", "Hotbar", "Payment"}) {
            events.addEventBinding(CustomUIEventBindingType.Dropped, "#" + grid,
                    EventData.of("Action", "DROP").append("Grid", grid), false);
        }
        update(commands, store, ref, null);
    }

    private void bind(UIEventBuilder events, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#" + action.replace("_", ""), EventData.of("Action", action));
    }

    /** Ignora eventos tras el cierre y valida el Beacon antes de mover objetos o cambiar la selección. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Action event) {
        if (dismissed) return;
        if (!BeaconOpenInteraction.validTarget(ref, store, position, block)) { close(); return; }
        if ("SORT".equals(event.value)) {
            deposit.containerForGrid(ref, store, "Storage").sortItems(SortType.TYPE);
            refresh(store, ref, null);
            return;
        }
        if ("DROP".equals(event.value)) {
            handleDrop(ref, store, event);
            return;
        }
        int tier = BeaconStructures.level(store.getExternalData().getWorld().getChunkStore(), position);
        if ("OK".equals(event.value)) {
            activateSelection(ref, store, tier);
            return;
        }
        if (tier == 4 && (BeaconPower.REGENERATION.name().equals(event.value) || BeaconRules.SECONDARY_UPGRADE.equals(event.value))) {
            secondary = secondary.equals(event.value) ? BeaconRules.SECONDARY_NONE : event.value;
        } else {
            for (BeaconPower power : BeaconPower.values()) {
                if (power != BeaconPower.REGENERATION && power.name().equals(event.value) && tier >= power.tier) primary = power;
            }
        }
        refresh(store, ref, null);
    }

    private void handleDrop(Ref<EntityStore> ref, Store<EntityStore> store, Action event) {
        ItemContainer source = deposit.containerForSection(ref, store, event.sourceSection);
        boolean moved = event.slot != null && event.sourceSlot != null && event.quantity != null
                && deposit.transfer(source, event.sourceSlot, deposit.containerForGrid(ref, store, event.grid), event.slot, event.quantity, event.itemId);
        refresh(store, ref, moved ? null : Message.translation("beacon.ui.invalidMove"));
    }

    /**
     * Activa una elección nueva tras validar nivel y pago; una elección ya pagada no vuelve a cobrarse.
     *
     * @param tier nivel actual de la pirámide
     * @throws RuntimeException si falla el guardado, tras intentar devolver el lingote cobrado
     */
    private void activateSelection(Ref<EntityStore> ref, Store<EntityStore> store, int tier) {
        if (!BeaconRules.validSelection(tier, primary, secondary)) {
            refresh(store, ref, Message.translation("beacon.ui.invalidSelection"));
            return;
        }
        BeaconSelection current = BeaconSelection.get(store.getExternalData().getWorld().getChunkStore(), position);
        if (current != null && current.matches(primary, secondary)) {
            refresh(store, ref, Message.translation("beacon.ui.alreadyActive"));
            return;
        }
        ItemStack paid = deposit.charge();
        if (paid == null) {
            refresh(store, ref, Message.translation("beacon.ui.paymentRequired"));
            return;
        }
        try {
            BeaconSelection.save(block, primary, secondary);
        } catch (RuntimeException error) {
            SimpleItemContainer.addOrDropItemStack(store, ref, BeaconPaymentWindow.inventory(ref, store), paid);
            throw error;
        }
        LOGGER.at(Level.INFO).log("Beacon activated at %s: primary=%s, secondary=%s, payment=%s", position, primary, secondary, paid.getItemId());
        deposit.refund(ref, store);
        refresh(store, ref, null);
    }

    private void refresh(Store<EntityStore> store, Ref<EntityStore> ref, @Nullable Message message) {
        UICommandBuilder commands = new UICommandBuilder();
        update(commands, store, ref, message);
        sendUpdate(commands);
    }

    /** Adapta la elección provisional al nivel y prepara la UI, sin modificar la selección pagada. */
    private void update(UICommandBuilder commands, Store<EntityStore> store, Ref<EntityStore> ref, @Nullable Message message) {
        int tier = BeaconStructures.level(store.getExternalData().getWorld().getChunkStore(), position);
        BeaconSelection saved = BeaconSelection.get(store.getExternalData().getWorld().getChunkStore(), position);
        if (tier < 4) secondary = BeaconRules.SECONDARY_NONE;
        view.update(commands, store, ref, tier, saved, primary, secondary, message);
    }

    /** Devuelve el depósito una sola vez, tanto si su ventana sigue registrada como si ya fue retirada. */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (dismissed) return;
        dismissed = true;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && deposit.getId() > 0 && player.getWindowManager().getWindow(deposit.getId()) == deposit) {
            deposit.close(ref, store);
        } else deposit.onClose0(ref, store);
    }

    /** Acciones de botones y arrastre; los índices y cantidades nativos se reciben como enteros anulables. */
    public static final class Action {
        public static final BuilderCodec<Action> CODEC = BuilderCodec.builder(Action.class, Action::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (a, v) -> a.value = v, a -> a.value).add()
                .append(new KeyedCodec<>("Grid", Codec.STRING), (a, v) -> a.grid = v, a -> a.grid).add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (a, v) -> a.slot = v, a -> a.slot).add()
                .append(new KeyedCodec<>("SourceInventorySectionId", Codec.INTEGER), (a, v) -> a.sourceSection = v, a -> a.sourceSection).add()
                .append(new KeyedCodec<>("SourceSlotId", Codec.INTEGER), (a, v) -> a.sourceSlot = v, a -> a.sourceSlot).add()
                .append(new KeyedCodec<>("ItemStackQuantity", Codec.INTEGER), (a, v) -> a.quantity = v, a -> a.quantity).add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (a, v) -> a.itemId = v, a -> a.itemId).add().build();
        String value;
        String grid;
        Integer slot;
        Integer sourceSection;
        Integer sourceSlot;
        Integer quantity;
        String itemId;
    }
}

