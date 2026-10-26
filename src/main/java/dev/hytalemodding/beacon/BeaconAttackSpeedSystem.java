package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSavingSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;

/**
 * Selecciona ataques principales según el balance central de Prisa I o II.
 * Actualiza el ataque antes del sistema nativo de interacciones y restaura el original
 * al caducar el efecto o cambiar de objeto. Las acciones secundarias no se modifican.
 */
public final class BeaconAttackSpeedSystem extends EntityTickingSystem<EntityStore> {
    private final BeaconHasteAssets assets = new BeaconHasteAssets(1);
    private final BeaconHasteAssets strongerAssets = new BeaconHasteAssets(2);
    private final Query<EntityStore> players = Query.and(Player.getComponentType(), TransformComponent.getComponentType());

    /** Genera las variantes una vez, después de cargar los assets nativos del servidor. */
    public void loadAssets() {
        assets.load();
        strongerAssets.load();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return players;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, InteractionSystems.TickInteractionManagerSystem.class));
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> entities,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> player = entities.getReferenceTo(index);
        ItemStack held = InventoryComponent.getItemInHand(buffer, player);
        int strength = BeaconEffects.rank(buffer.getComponent(player, EffectControllerComponent.getComponentType()), BeaconPower.HASTE);
        String desired = strength > 0 && held != null
                ? (strength == 2 ? strongerAssets : assets).primary(held.getItemId()) : null;
        updatePrimary(player, buffer, desired);
    }

    /**
     * Aplica o retira la sustitución temporal de este mod sin sobrescribir las de otros plugins.
     * @param player entidad cuyo ataque se actualiza
     * @param accessor acceso al almacén desde el hilo del mundo o su búfer de comandos
     * @param desired identificador del ataque acelerado, o {@code null} para restaurar el normal
     */
    static void updatePrimary(Ref<EntityStore> player, ComponentAccessor<EntityStore> accessor, String desired) {
        Interactions interactions = accessor.getComponent(player, Interactions.getComponentType());
        String current = interactions == null ? null : interactions.getInteractionId(InteractionType.Primary);
        if (current != null && !current.startsWith(BeaconHasteAssets.PREFIX)) {
            return;
        }
        if (Objects.equals(current, desired)) {
            return;
        }
        if (interactions == null) {
            interactions = accessor.ensureAndGetComponent(player, Interactions.getComponentType());
        }
        if (desired == null) {
            interactions.removeInteractionId(InteractionType.Primary);
        } else {
            interactions.setInteractionId(InteractionType.Primary, desired);
        }
    }

    /** Retira sustituciones temporales antes del guardado y al reincorporar una entidad. */
    public static final class Cleanup extends HolderSystem<EntityStore> {
        @Override
        public Query<EntityStore> getQuery() {
            return Interactions.getComponentType();
        }

        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.BEFORE, PlayerSavingSystems.EntityRemovedSystem.class));
        }

        @Override
        public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
            clear(holder);
        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {
            clear(holder);
        }

        private void clear(Holder<EntityStore> holder) {
            Interactions interactions = holder.getComponent(Interactions.getComponentType());
            String primary = interactions.getInteractionId(InteractionType.Primary);
            if (primary != null && primary.startsWith(BeaconHasteAssets.PREFIX)) {
                interactions.removeInteractionId(InteractionType.Primary);
            }
        }
    }
}
