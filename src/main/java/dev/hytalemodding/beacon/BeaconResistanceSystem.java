package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/** Reduce daño físico y de proyectiles y conserva la protección de caída de Salto tras la armadura. */
public final class BeaconResistanceSystem extends DamageEventSystem {
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), EffectControllerComponent.getComponentType());
    }

    @Override
    public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getFilterDamageGroup(); }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.ArmorDamageReduction.class));
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> entities, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull Damage damage) {
        DamageCause cause = DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        if (damage.isCancelled() || damage.getAmount() <= 0 || cause == null || cause.doesBypassResistances()) return;
        // La resistencia declarativa nativa omite causas derivadas o duplica su herencia con la armadura.
        EffectControllerComponent effects = entities.getComponent(index, EffectControllerComponent.getComponentType());
        if (effects == null) return;
        DamageCause physical = DamageCause.getAssetMap().getAsset("Physical");
        DamageCause projectile = DamageCause.getAssetMap().getAsset("Projectile");
        DamageCause category = physical != null && cause.isOrInherits(physical) ? physical
                : projectile != null && cause.isOrInherits(projectile) ? projectile : null;
        float multiplier = category == null ? 1f : resistanceMultiplier(entities.getReferenceTo(index), buffer, effects, cause, category);
        DamageCause fall = DamageCause.getAssetMap().getAsset("Fall");
        if (fall != null && cause.isOrInherits(fall)) {
            int jump = BeaconEffects.rank(effects, BeaconPower.JUMP_BOOST);
            multiplier *= 1f - BeaconPower.JUMP_FALL_REDUCTION_PER_RANK * jump;
        }
        damage.setAmount(damage.getAmount() * multiplier);
    }

    /** Suma el bono al porcentaje nativo de su categoría sin repetir la reducción de armadura ya aplicada. */
    private static float resistanceMultiplier(Ref<EntityStore> ref, CommandBuffer<EntityStore> buffer,
                                             EffectControllerComponent effects, DamageCause cause, DamageCause category) {
        float bonus = BeaconPower.RESISTANCE.bonus(BeaconEffects.rank(effects, BeaconPower.RESISTANCE));
        if (bonus == 0) return 1f;
        var armor = buffer.getComponent(ref, InventoryComponent.Armor.getComponentType());
        var modifiers = DamageSystems.ArmorDamageReduction.getResistanceModifiers(buffer.getExternalData().getWorld(),
                armor == null ? EmptyItemContainer.INSTANCE : armor.getInventory(), ItemUtils.canApplyItemStackPenalties(ref, buffer), effects);
        var entry = modifiers.get(cause);
        var categoryEntry = modifiers.get(category);
        float remaining = 1f;
        for (int depth = 0; entry != null && depth < 8; depth++) {
            if (entry == categoryEntry) {
                remaining = Math.max(0f, 1f - entry.multiplierModifier);
                break;
            }
            entry = entry.inheritedParentId == null ? null : modifiers.get(entry.inheritedParentId);
        }
        // Ejemplo: 9 % de armadura + 5 % de Beacon pasa de 0,91 a 0,86, sin recalcular el daño original.
        return remaining > 0 ? Math.max(0f, remaining - bonus) / remaining : 0f;
    }
}
