package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/** Aplica Fuerza al daño físico cuerpo a cuerpo antes de la armadura; excluye proyectiles y magia. */
public final class BeaconStrengthSystem extends DamageEventSystem {
    @Override
    public Query<EntityStore> getQuery() { return Query.any(); }

    @Override
    public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getFilterDamageGroup(); }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ArmorDamageReduction.class),
                new SystemDependency<>(Order.AFTER, DamageSystems.ScaleOutgoingDamageFromEntityEffects.class));
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> entities, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0 || damage.getSource() instanceof Damage.ProjectileSource
                || !(damage.getSource() instanceof Damage.EntitySource source) || source.getRef() == null || !source.getRef().isValid()) return;
        DamageCause cause = DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        DamageCause physical = DamageCause.getAssetMap().getAsset("Physical");
        if (cause == null || physical == null || !cause.isOrInherits(physical)) return;
        int rank = BeaconEffects.rank(buffer.getComponent(source.getRef(), EffectControllerComponent.getComponentType()), BeaconPower.STRENGTH);
        damage.setAmount(damage.getAmount() * BeaconPower.STRENGTH.multiplier(rank));
    }
}
