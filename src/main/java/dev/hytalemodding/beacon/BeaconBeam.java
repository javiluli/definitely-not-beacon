package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static dev.hytalemodding.beacon.BeaconBeamModels.BLOCK_UNITS;
import static dev.hytalemodding.beacon.BeaconBeamModels.HEIGHT_UNITS;
import static dev.hytalemodding.beacon.BeaconBeamModels.ORIGIN_UNITS;

/** Mantiene el halo y los tramos del núcleo de un mundo; todas las entidades se usan desde su hilo. */
final class BeaconBeam {
    private static final HytaleLogger LOGGER = HytaleLogger.get("Definitely Not Beacon");
    private final Map<BeaconStructures.Position, Beam> layersByBeacon = new HashMap<>();

    private record Core(Ref<EntityStore> ref, BeaconCrystals.Span span) {}

    private static final class Beam {
        final List<Core> cores = new ArrayList<>();
        final Ref<EntityStore> halo;
        final BlockSection[] sections = new BlockSection[ChunkUtil.HEIGHT / ChunkUtil.SIZE];
        final short[] changes = new short[sections.length];
        List<BeaconCrystals.Span> applied = List.of(new BeaconCrystals.Span(0, HEIGHT_UNITS, -1));
        List<BeaconCrystals.Span> desired = applied;

        Beam(Ref<EntityStore> core, Ref<EntityStore> halo) {
            cores.add(new Core(core, applied.getFirst()));
            this.halo = halo;
        }

        boolean valid() {
            if (!halo.isValid()) return false;
            for (int i = 0; i < cores.size(); i++) if (!cores.get(i).ref().isValid()) return false;
            return true;
        }

        void remove(Store<EntityStore> store) {
            for (Core core : cores) if (core.ref().isValid()) store.removeEntity(core.ref(), RemoveReason.REMOVE);
            if (halo.isValid()) store.removeEntity(halo, RemoveReason.REMOVE);
        }
    }

    /** Revisa activación y referencias, renueva la vida a tres segundos y fuerza una lectura de respaldo cada segundo. */
    void update(Set<BeaconStructures.Position> activePositions, Store<EntityStore> store) {
        var iterator = layersByBeacon.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!activePositions.contains(entry.getKey()) || !entry.getValue().valid()) {
                entry.getValue().remove(store);
                iterator.remove();
            }
        }
        ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
        TimeResource time = store.getResource(TimeResource.getResourceType());
        for (var position : activePositions) {
            Beam beam = layersByBeacon.get(position);
            if (beam == null) {
                var section = chunks.getChunkSectionReferenceAtBlock(position.x(), position.y(), position.z());
                if (section == null || !section.isValid()
                        || chunks.getStore().getArchetype(section).contains(ChunkStore.REGISTRY.getNonTickingComponentType())) continue;
                Vector3d start = origin(position);
                Ref<EntityStore> core = spawn(store, start, BeaconBeamModels.core(0, HEIGHT_UNITS, position.y()), 0);
                ModelAsset haloAsset = ModelAsset.getAssetMap().getAsset("Beacon_Beam_Halo");
                Ref<EntityStore> halo = haloAsset == null ? null : spawn(store, start, Model.createUnitScaleModel(haloAsset), 0);
                if (core == null || halo == null) {
                    if (core != null) store.removeEntity(core, RemoveReason.REMOVE);
                    if (halo != null) store.removeEntity(halo, RemoveReason.REMOVE);
                    continue;
                }
                beam = new Beam(core, halo);
                layersByBeacon.put(position, beam);
            }
            for (Core core : beam.cores) renew(core.ref(), store, time);
            renew(beam.halo, store, time);
            refresh(position, beam, chunks, store, true);
        }
    }

    /** Revisa a 20 Hz las columnas de los haces existentes, sin redescubrir bases ni actualizar poderes. */
    void updateColors(Store<EntityStore> store) {
        ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
        for (var entry : layersByBeacon.entrySet()) {
            if (entry.getValue().valid()) refresh(entry.getKey(), entry.getValue(), chunks, store, false);
        }
    }

    private static void renew(Ref<EntityStore> ref, Store<EntityStore> store, TimeResource time) {
        DespawnComponent despawn = store.getComponent(ref, DespawnComponent.getComponentType());
        if (despawn != null) despawn.setDespawnTo(time.getNow(), 3f);
    }

    private static Vector3d origin(BeaconStructures.Position position) {
        return new Vector3d(position.x() + 0.5, position.y() + ORIGIN_UNITS / (double) BLOCK_UNITS, position.z() + 0.5);
    }

    private static void refresh(BeaconStructures.Position position, Beam beam, ChunkStore chunks,
                                Store<EntityStore> store, boolean force) {
        boolean changed = false;
        for (int y = ChunkUtil.indexSection(position.y()); y < beam.sections.length; y++) {
            BlockSection section = BeaconCrystals.section(chunks, position, y);
            short revision = section == null ? 0 : section.getLocalChangeCounter();
            changed |= section != beam.sections[y] || revision != beam.changes[y];
            beam.sections[y] = section;
            beam.changes[y] = revision;
        }
        // set() invalida la luz local; el contador permite saltar columnas intactas. El respaldo evita su wrap de 16 bits.
        if (changed || force) beam.desired = BeaconCrystals.trace(chunks, position);
        if (beam.desired.equals(beam.applied)) {
            beam.desired = beam.applied;
            return;
        }
        boolean ready = true;
        for (var span : beam.desired) {
            if (span.color() >= 0 && BeaconCrystals.tint(span.color()).getNow(null) == null) ready = false;
        }
        if (!ready) return; // Solicita todos los colores juntos; nunca espera el bloqueo de escritura desde el mundo.
        beam.applied = List.of(); // Si una creación falla, el trazado parcial debe volver a reconciliarse.
        if (apply(position, beam, store)) beam.applied = beam.desired;
    }

    /** Reutiliza las entidades existentes por orden; solo crea o retira piezas cuando cambia el trazado. */
    private static boolean apply(BeaconStructures.Position position, Beam beam, Store<EntityStore> store) {
        float yaw = store.getComponent(beam.cores.getFirst().ref(), TransformComponent.getComponentType()).getRotation().yaw();
        for (int i = 0; i < beam.desired.size(); i++) {
            var span = beam.desired.get(i);
            Core previous = i < beam.cores.size() ? beam.cores.get(i) : null;
            if (previous != null && span.equals(previous.span())) continue;
            Model model = BeaconBeamModels.core(span.start(), span.end(), position.y());
            // El dibujo mantiene su altura; el anclaje queda en la sección activa del Beacon.
            Vector3d start = origin(position).add(0, span.start() / (double) BLOCK_UNITS - model.getBoundingBox().min.y, 0);
            Ref<EntityStore> ref = previous == null ? spawn(store, start, model, yaw) : previous.ref();
            if (ref == null) return false;
            if (previous != null) {
                if (span.start() != previous.span().start() || span.end() != previous.span().end()) {
                    store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
                    store.getComponent(ref, TransformComponent.getComponentType()).setPosition(start);
                }
                if (previous.span().color() >= 0 && previous.span().color() != span.color()) {
                    int id = EntityEffect.getAssetMap().getIndex(BeaconCrystals.tintId(previous.span().color()));
                    store.getComponent(ref, EffectControllerComponent.getComponentType()).removeEffect(ref, id, store);
                }
            }
            if (span.color() >= 0 && (previous == null || previous.span().color() != span.color())) {
                var effect = BeaconCrystals.tint(span.color()).getNow(null);
                store.getComponent(ref, EffectControllerComponent.getComponentType()).addEffect(ref, effect, store);
            }
            Core core = new Core(ref, span);
            if (previous == null) beam.cores.add(core); else beam.cores.set(i, core);
        }
        while (beam.cores.size() > beam.desired.size()) {
            var core = beam.cores.removeLast();
            if (core.ref().isValid()) store.removeEntity(core.ref(), RemoveReason.REMOVE);
        }
        return true;
    }

    /** Crea una pieza intangible y temporal con la fase de giro del núcleo y su animación de flujo. */
    private static Ref<EntityStore> spawn(Store<EntityStore> store, Vector3d start, Model model, float yaw) {
        if (model == null) return null;
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(start, new Rotation3f(0, yaw, 0)));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        ActiveAnimationComponent animation = new ActiveAnimationComponent();
        animation.setPlayingAnimation(AnimationSlot.Status, "Beam_Flow");
        holder.addComponent(ActiveAnimationComponent.getComponentType(), animation);
        holder.addComponent(EffectControllerComponent.getComponentType(), new EffectControllerComponent());
        holder.addComponent(DespawnComponent.getComponentType(), DespawnComponent.despawnInSeconds(
                store.getResource(TimeResource.getResourceType()), 3f));
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null) {
            LOGGER.at(Level.WARNING).atMostEvery(30, TimeUnit.SECONDS).log("Could not create Beacon beam piece at %s", start);
            return null;
        }
        AnimationUtils.playAnimation(ref, AnimationSlot.Status, "Beam_Flow", store);
        if ("Beacon_Beam_Halo".equals(model.getModelAssetId())) {
            var glow = EntityEffect.getAssetMap().getAsset("Beacon_Beam_Glow");
            if (glow != null) store.getComponent(ref, EffectControllerComponent.getComponentType()).addEffect(ref, glow, store);
        }
        return ref;
    }
}
