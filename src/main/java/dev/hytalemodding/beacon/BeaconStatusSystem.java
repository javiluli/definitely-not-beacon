package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Actualiza bases y mejoras cada segundo, y los colores del haz con un intervalo de 50 ms.
 * {@link BeaconWorldState} conserva la caché temporal y {@link BeaconSelection} la elección pagada.
 *
 * @see BeaconStructures
 * @see BeaconBeam
 */
public final class BeaconStatusSystem extends DelayedSystem<EntityStore> {
    private final ResourceType<EntityStore, BeaconWorldState> stateType = registerResource(BeaconWorldState.class, BeaconWorldState::new);
    private final Query<EntityStore> players = Query.and(Player.getComponentType(), TransformComponent.getComponentType());

    /** Revisa cambios visuales cada 50 ms; bases y poderes conservan su intervalo de un segundo. */
    public BeaconStatusSystem() {
        super(0.05f);
    }

    /** Separa la revisión visual rápida del descubrimiento y los poderes, conservando el reloj por mundo. */
    @Override
    public void delayedTick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        BeaconWorldState state = store.getResource(stateType);
        state.powerElapsed += dt;
        if (state.powerElapsed >= 1f) {
            state.powerElapsed %= 1f;
            state.levels = Map.copyOf(BeaconStructures.findActive(store.getExternalData().getWorld().getChunkStore()));
            updatePlayers(state, store);
            state.beams.update(state.levels.keySet(), store);
        } else {
            state.beams.updateColors(store);
        }
    }

    /** Renueva efectos y después impulso; forEachChunk consume su buffer antes de reconciliar los haces. */
    private void updatePlayers(BeaconWorldState state, Store<EntityStore> store) {
        state.jumps.keySet().removeIf(ref -> !ref.isValid());
        store.forEachChunk(players, (entities, buffer) -> {
            for (int i = 0; i < entities.size(); i++) {
                TransformComponent transform = entities.getComponent(i, TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }
                Ref<EntityStore> player = entities.getReferenceTo(i);
                refreshPowers(player, transform.getPosition(), store, buffer);
                EffectControllerComponent effects = buffer.getComponent(player, EffectControllerComponent.getComponentType());
                updateJump(state, player, store, BeaconEffects.rank(effects, BeaconPower.JUMP_BOOST));
            }
        });
    }

    /**
     * Combina las fuentes en alcance por intensidad y duración, y renueva los efectos elegidos.
     * Sin fuente conserva el residual; una fuente de rango I sustituye de inmediato al rango II.
     */
    void refreshPowers(Ref<EntityStore> player, Vector3d position, Store<EntityStore> store, ComponentAccessor<EntityStore> accessor) {
        EffectControllerComponent effects = accessor.ensureAndGetComponent(player, EffectControllerComponent.getComponentType());
        for (BeaconPower power : BeaconPower.values()) {
            int strength = 0, duration = 0;
            ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
            for (var entry : store.getResource(stateType).levels.entrySet()) {
                var beacon = entry.getKey();
                if (!BeaconRules.inRange(position.x, position.y, position.z, beacon.x(), beacon.y(), beacon.z(), entry.getValue())) continue;
                BeaconSelection selection = BeaconSelection.get(chunks, beacon);
                int candidate = selection == null ? 0 : selection.strength(power, entry.getValue());
                int candidateDuration = BeaconPower.durationSeconds(entry.getValue());
                if (BeaconRules.shouldReplacePower(strength, duration, candidate, candidateDuration)) {
                    strength = candidate;
                    duration = candidateDuration;
                }
            }
            if (strength == 0) continue;
            applyPower(player, power, strength, duration, effects, accessor);
        }
    }

    /** Renueva el rango elegido con la duración en segundos y retira el otro rango, sin sumar bonos. */
    private void applyPower(Ref<EntityStore> player, BeaconPower power, int strength, int duration,
                            EffectControllerComponent effects, ComponentAccessor<EntityStore> accessor) {
        for (int rank = 1; rank <= (power == BeaconPower.REGENERATION ? 1 : 2); rank++) {
            String id = power.effectId(rank);
            int index = EntityEffect.getAssetMap().getIndex(id);
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
            if (rank == strength && effect != null) effects.addEffect(player, effect, duration, OverlapBehavior.OVERWRITE, accessor);
            else if (index >= 0 && effects.hasEffect(index)) effects.removeEffect(player, index, accessor);
        }
    }

    /**
     * Conserva la base propia del impulso; un valor externo distinto pasa a ser la nueva base.
     * Hytale 0.7.4 no expone el impulso como campo de EntityEffect.
     */
    private void updateJump(BeaconWorldState state, Ref<EntityStore> player, Store<EntityStore> store, int strength) {
        MovementManager movement = store.getComponent(player, MovementManager.getComponentType());
        PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
        if (movement == null || playerRef == null) return;
        float current = movement.getSettings().jumpForce;
        BeaconWorldState.Jump previous = state.jumps.get(player);
        float base = previous != null && current == previous.applied() ? previous.base() : current;
        float desired = base * BeaconPower.JUMP_BOOST.multiplier(strength);
        if (current != desired) {
            movement.getSettings().jumpForce = desired;
            movement.update(playerRef.getPacketHandler());
        }
        if (strength > 0) state.jumps.put(player, new BeaconWorldState.Jump(base, desired));
        else state.jumps.remove(player);
    }

}
