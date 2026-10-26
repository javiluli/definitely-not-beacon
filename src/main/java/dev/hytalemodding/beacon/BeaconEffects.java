package dev.hytalemodding.beacon;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import org.bson.*;

import java.util.ArrayList;
import java.util.List;

/** Traduce el balance puro de {@link BeaconPower} a los efectos nativos de Hytale. */
public final class BeaconEffects {
    private BeaconEffects() {}

    /** Intensidad efectiva, incluido el tiempo residual al abandonar un Beacon. */
    static int rank(EffectControllerComponent effects, BeaconPower power) {
        if (effects == null) return 0;
        for (int rank = power == BeaconPower.REGENERATION ? 1 : 2; rank >= 1; rank--) {
            int index = EntityEffect.getAssetMap().getIndex(power.effectId(rank));
            var active = index < 0 ? null : effects.getActiveEffects().get(index);
            if (active != null && (active.isInfinite() || active.getRemainingDuration() > 0)) return rank;
        }
        return 0;
    }

    /**
     * Publica una vez los valores de Java sobre las plantillas visuales del paquete.
     * @throws IllegalStateException si falta una plantilla o falla la publicación
     */
    public static void load() {
        String pack = EntityEffect.getAssetMap().getAssetPack(BeaconPower.SPEED.effectId(1));
        List<EntityEffect> effects = new ArrayList<>();
        for (BeaconPower power : BeaconPower.values()) for (int rank = 1; rank <= (power == BeaconPower.REGENERATION ? 1 : 2); rank++) {
            String id = power.effectId(rank);
            EntityEffect template = EntityEffect.getAssetMap().getAsset(id);
            if (template == null) throw new IllegalStateException("Missing Beacon effect template: " + id);
            BsonDocument data = EntityEffect.CODEC.encode(template, new ExtraInfo()).asDocument();
            data.remove("Id");
            data.remove("Parent");
            data.put("Duration", new BsonDouble(BeaconPower.durationSeconds(4)));
            if (power == BeaconPower.SPEED) {
                data.put("ApplicationEffects", new BsonDocument("HorizontalSpeedMultiplier", new BsonDouble(power.multiplier(rank))));
            } else if (power == BeaconPower.RESISTANCE) {
                // RawStatModifiers cambia el máximo; StatModifiers curaría periódicamente.
                data.put("RawStatModifiers", new BsonDocument("Health", new BsonArray(List.of(
                        new BsonDocument("Amount", new BsonDouble(BeaconPower.resistanceHealth(rank)))
                                .append("CalculationType", new BsonString("Additive"))))));
            } else if (power == BeaconPower.REGENERATION) {
                data.put("StatModifiers", new BsonDocument("Health", new BsonDouble(power.bonus(rank))));
                data.put("ValueType", new BsonString("Absolute"));
                data.put("DamageCalculatorCooldown", new BsonDouble(BeaconPower.REGENERATION_INTERVAL_SECONDS));
            }
            effects.add(EntityEffect.getAssetStore().decode(pack, id, data));
        }
        var result = EntityEffect.getAssetStore().loadAssets(pack, effects);
        if (result.hasFailed()) {
            throw new IllegalStateException("Could not publish Beacon effects: failed keys="
                    + result.getFailedToLoadKeys() + "; failed paths=" + result.getFailedToLoadPaths()
                    + "; check native asset logs for child failures");
        }
        loadSprintStamina(pack);
    }

    /** Ajusta solo el gasto al esprintar con condiciones nativas por jugador, conservando los demás regeneradores. */
    private static void loadSprintStamina(String pack) {
        var assets = EntityStatType.getAssetStore();
        var stamina = EntityStatType.getAssetMap().getAsset("Stamina");
        if (stamina == null) throw new IllegalStateException("Missing native Stamina stat");
        BsonDocument data = EntityStatType.CODEC.encode(stamina, new ExtraInfo()).asDocument();
        data.remove("Id");
        data.remove("Parent");
        boolean found = false;
        for (BsonValue value : data.getArray("Regenerating")) {
            var regen = value.asDocument();
            if (regen.getNumber("Amount").doubleValue() >= 0 || regen.getArray("Conditions", new BsonArray()).stream().noneMatch(condition ->
                    "Sprinting".equals(condition.asDocument().getString("Id").getValue())
                            && !condition.asDocument().getBoolean("Inverse", BsonBoolean.FALSE).getValue())) continue;
            var modifiers = regen.getArray("Modifiers", new BsonArray());
            for (int rank = 1; rank <= 2; rank++) {
                var conditions = new BsonArray(List.of(new BsonDocument("Id", new BsonString("HasEffect"))
                        .append("EffectId", new BsonString(BeaconPower.SPEED.effectId(rank)))));
                // Si coinciden ambas intensidades, II sustituye I en vez de multiplicar sus ahorros.
                if (rank == 1) conditions.add(new BsonDocument("Id", new BsonString("HasEffect"))
                        .append("EffectId", new BsonString(BeaconPower.SPEED.effectId(2))).append("Inverse", BsonBoolean.TRUE));
                modifiers.add(new BsonDocument("Conditions", conditions)
                        .append("Amount", new BsonDouble(1f - BeaconPower.SPEED.bonus(rank))));
            }
            regen.put("Modifiers", modifiers);
            found = true;
        }
        if (!found) throw new IllegalStateException("Native sprint stamina drain was not found");
        if (assets.loadAssets(pack, List.of(assets.decode(pack, "Stamina", data))).hasFailed()) {
            throw new IllegalStateException("Could not publish Beacon sprint stamina modifiers");
        }
    }
}
