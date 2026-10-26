package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.Instant;
import java.util.List;

/** Comprueba el ahorro de sprint usando el regenerador y las condiciones reales del servidor. */
final class BeaconStaminaChecks {
    private BeaconStaminaChecks() {}

    static void run(Store<EntityStore> store, Ref<EntityStore> actor, EntityStatType original, List<String> results) {
        var current = EntityStatType.getAssetMap().getAsset("Stamina");
        int sprint = checkPreservedStat(original, current);
        var effects = store.getComponent(actor, EffectControllerComponent.getComponentType());
        check(BeaconEffects.rank(effects, BeaconPower.SPEED) == 0, "Stamina fixture expects no previous Speed effect");
        var previousMovement = store.getComponent(actor, MovementStatesComponent.getComponentType());
        var movement = new MovementStatesComponent();
        store.putComponent(actor, MovementStatesComponent.getComponentType(), movement);
        var stats = new EntityStatMap();
        stats.update();
        var stamina = stats.get(DefaultEntityStatTypes.getStamina());
        try {
            movement.getMovementStates().sprinting = true;
            float baseline = drain(store, actor, original.getRegenerating()[sprint], stamina);
            near(baseline, -.1f, "Native sprint drain changed");
            near(drain(store, actor, current.getRegenerating()[sprint], stamina), baseline, "Unbuffed sprint changed");
            for (int rank : List.of(1, 1, 2)) {
                effects.removeEffect(actor, EntityEffect.getAssetMap().getIndex(BeaconPower.SPEED.effectId(3 - rank)), store);
                effects.addEffect(actor, EntityEffect.getAssetMap().getAsset(BeaconPower.SPEED.effectId(rank)), store);
                near(drain(store, actor, current.getRegenerating()[sprint], stamina), rank == 1 ? -.06f : -.04f, "Wrong sprint cost at rank " + rank);
            }
            effects.addEffect(actor, EntityEffect.getAssetMap().getAsset(BeaconPower.SPEED.effectId(1)), store);
            near(drain(store, actor, current.getRegenerating()[sprint], stamina), -.04f, "Overlapping Speed intensities multiplied savings");
            effects.removeEffect(actor, EntityEffect.getAssetMap().getIndex(BeaconPower.SPEED.effectId(2)), store);
            near(drain(store, actor, current.getRegenerating()[sprint], stamina), -.06f, "Downgrade retained Speed II savings");
            movement.getMovementStates().sprinting = false;
            near(drain(store, actor, current.getRegenerating()[sprint], stamina), 0f, "Speed changes idle stamina");
            movement.getMovementStates().gliding = true;
            near(drain(store, actor, current.getRegenerating()[4], stamina), -.1f, "Speed reduced native gliding cost");
            movement.getMovementStates().gliding = false;
            movement.getMovementStates().sprinting = true;
            effects.removeEffect(actor, EntityEffect.getAssetMap().getIndex(BeaconPower.SPEED.effectId(1)), store);
            near(drain(store, actor, current.getRegenerating()[sprint], stamina), baseline, "Removing Speed retained stamina savings");
        } finally {
            for (int rank = 1; rank <= 2; rank++) effects.removeEffect(actor, EntityEffect.getAssetMap().getIndex(BeaconPower.SPEED.effectId(rank)), store);
            if (previousMovement == null) store.tryRemoveComponent(actor, MovementStatesComponent.getComponentType());
            else store.putComponent(actor, MovementStatesComponent.getComponentType(), previousMovement);
        }
        results.add("Sprint stamina: native -0.1 becomes -0.06/-0.04; refresh, overlap, downgrade and removal; idle, gliding and all other native stat fields preserved");
    }

    /** Solo se permite añadir los dos modificadores al drenaje condicionado por sprint. */
    private static int checkPreservedStat(EntityStatType original, EntityStatType current) {
        var before = EntityStatType.CODEC.encode(original, new ExtraInfo()).asDocument();
        var after = EntityStatType.CODEC.encode(current, new ExtraInfo()).asDocument();
        var oldValues = before.getArray("Regenerating");
        var newValues = after.getArray("Regenerating");
        check(oldValues.size() == newValues.size(), "Beacon replaced native stamina regenerators");
        int sprint = -1;
        for (int i = 0; i < oldValues.size(); i++) {
            var oldValue = oldValues.get(i).asDocument();
            var newValue = newValues.get(i).asDocument();
            if (oldValue.equals(newValue)) continue;
            check(sprint < 0 && oldValue.getNumber("Amount").doubleValue() < 0
                    && oldValue.getArray("Conditions").getFirst().asDocument().getString("Id").getValue().equals("Sprinting"),
                    "Beacon modified a non-sprint regenerator");
            int oldModifiers = oldValue.containsKey("Modifiers") ? oldValue.getArray("Modifiers").size() : 0;
            check(newValue.getArray("Modifiers").size() == oldModifiers + 2, "Wrong number of sprint modifiers");
            for (int m = 0; m < oldModifiers; m++) check(oldValue.getArray("Modifiers").get(m).equals(newValue.getArray("Modifiers").get(m)), "Existing stamina modifier changed");
            if (oldValue.containsKey("Modifiers")) newValue.put("Modifiers", oldValue.getArray("Modifiers"));
            else newValue.remove("Modifiers");
            sprint = i;
        }
        check(sprint >= 0 && before.equals(after), "Native stamina fields changed outside sprint modifiers");
        return sprint;
    }

    private static float drain(Store<EntityStore> store, Ref<EntityStore> actor, EntityStatType.Regenerating regen,
                               com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue stamina) {
        return new RegeneratingValue(regen).regenerate(store, actor, Instant.EPOCH, .1f, stamina, stamina.get());
    }

    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .00001f, message + ": " + actual); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
