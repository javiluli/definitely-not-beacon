package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.ItemSlotFilter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resuelve los inventarios del jugador y gestiona el depósito, las transferencias y la devolución nativa. */
final class BeaconPaymentWindow extends ContainerWindow {
    /** Impide aceptar movimientos y repetir la devolución después del cierre. */
    private boolean closed;

    BeaconPaymentWindow() {
        super(new SimpleItemContainer((short) 1));
        getData().addProperty("blockItemId", "Beacon_Block");
        getItemContainer().setSlotFilter(FilterActionType.ADD, (short) 0,
                (ItemSlotFilter) item -> !closed && item != null && BeaconRules.isIngot(item.getId()));
    }

    /** Obtiene barra rápida y almacenamiento para devolver objetos desde el hilo del mundo. */
    static ItemContainer inventory(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        return InventoryComponent.getCombined(accessor, ref,
                InventoryComponent.Hotbar.getComponentType(), InventoryComponent.Storage.getComponentType());
    }

    /**
     * Resuelve una cuadrícula de esta página desde el hilo del mundo.
     * @param grid nombre recibido de la UI: Storage, Hotbar o Payment; puede ser nulo
     * @return contenedor del jugador o depósito; {@code null} para nombres desconocidos o ausentes
     */
    @Nullable
    ItemContainer containerForGrid(Ref<EntityStore> ref, Store<EntityStore> store, @Nullable String grid) {
        if ("Storage".equals(grid)) return store.getComponent(ref, InventoryComponent.Storage.getComponentType()).getInventory();
        if ("Hotbar".equals(grid)) return store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()).getInventory();
        return "Payment".equals(grid) ? getItemContainer() : null;
    }

    /**
     * Limita el origen del arrastre a los inventarios del jugador y al depósito de esta ventana.
     * @param section identificador nativo de sección recibido del cliente; puede ser nulo
     * @return contenedor propio; {@code null} para secciones ausentes o ajenas
     */
    @Nullable
    ItemContainer containerForSection(Ref<EntityStore> ref, Store<EntityStore> store, @Nullable Integer section) {
        if (section == null) return null;
        if (section == getId()) return containerForGrid(ref, store, "Payment");
        if (section == InventoryComponent.STORAGE_SECTION_ID) return containerForGrid(ref, store, "Storage");
        if (section == InventoryComponent.HOTBAR_SECTION_ID) return containerForGrid(ref, store, "Hotbar");
        return null;
    }

    /**
     * Valida el arrastre contra las existencias actuales y delega movimiento, fusión y cambio en Hytale.
     * @param from contenedor de origen resuelto para el jugador; puede ser nulo
     * @param fromSlot índice de origen, desde cero
     * @param to contenedor de destino resuelto para el jugador; puede ser nulo
     * @param toSlot índice de destino, desde cero
     * @param quantity unidades solicitadas, sin superar las existencias actuales
     * @param itemId identificador esperado para rechazar arrastres con datos obsoletos
     * @return si la transacción nativa se completó; {@code false} si se rechaza o el depósito está cerrado
     */
    boolean transfer(ItemContainer from, int fromSlot, ItemContainer to, int toSlot, int quantity, String itemId) {
        if (closed || from == null || to == null || quantity <= 0
                || fromSlot < 0 || fromSlot >= from.getCapacity() || toSlot < 0 || toSlot >= to.getCapacity()
                || from == to && fromSlot == toSlot) return false;
        ItemStack stack = from.getItemStack((short) fromSlot);
        if (ItemStack.isEmpty(stack) || quantity > stack.getQuantity() || !stack.getItemId().equals(itemId)) return false;
        return from.moveItemStackFromSlotToSlot((short) fromSlot, quantity, to, (short) toSlot).succeeded();
    }

    ItemStack payment() { return getItemContainer().getItemStack((short) 0); }

    /**
     * Consume una unidad depositada y conserva sus metadatos para compensar un guardado fallido.
     * @return lingote cobrado, o {@code null} si está cerrado, no hay un lingote o falla la transacción
     */
    ItemStack charge() {
        ItemStack stack = payment();
        if (closed || ItemStack.isEmpty(stack) || !BeaconRules.isIngot(stack.getItemId())) return null;
        var transaction = getItemContainer().removeItemStackFromSlot((short) 0, stack, 1);
        return transaction.succeeded() ? stack.withQuantity(1) : null;
    }

    /** Retira el depósito sin cobrar; un inventario lleno deja el sobrante junto al jugador. */
    void refund(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        ItemStack remainder = getItemContainer().removeItemStackFromSlot((short) 0).getOutput();
        if (!ItemStack.isEmpty(remainder)) {
            SimpleItemContainer.addOrDropItemStack(accessor, ref, inventory(ref, accessor), remainder);
        }
    }

    /** Incluye Escape, desconexión y cierre de ventanas; un inventario lleno deja el sobrante a los pies. */
    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (closed) return;
        closed = true;
        refund(ref, accessor);
    }
}
