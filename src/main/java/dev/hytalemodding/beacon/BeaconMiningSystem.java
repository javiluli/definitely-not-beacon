package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Aumenta el progreso de picado de los picos mientras Prisa esté activa, incluido su tiempo residual.
 * Reutiliza el daño nativo para conservar las restricciones de material y calidad;
 * ignora eventos cancelados, daño no positivo y herramientas que no sean picos.
 */
public final class BeaconMiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
    private final Query<EntityStore> query = Query.and(Player.getComponentType(), TransformComponent.getComponentType());
    /** Configura la escucha del progreso de picado nativo. */
    public BeaconMiningSystem() {
        super(DamageBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> entities, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull DamageBlockEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }
        ItemStack held = event.getItemInHand();
        if (held == null || held.getItem() == null || held.getItem().getTool() == null
                || !BeaconRules.isPickaxe(held.getItem().getPlayerAnimationsId())) {
            return;
        }
        TransformComponent transform = entities.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        var effects = commandBuffer.getComponent(entities.getReferenceTo(index), EffectControllerComponent.getComponentType());
        event.setDamage(BeaconRules.miningDamage(event.getDamage(), BeaconEffects.rank(effects, BeaconPower.HASTE)));
    }
}
