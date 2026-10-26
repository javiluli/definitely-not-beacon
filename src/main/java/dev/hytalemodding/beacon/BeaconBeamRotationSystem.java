package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/** Rota solo el núcleo del haz antes de replicar su orientación; deja fijo el halo. */
public final class BeaconBeamRotationSystem extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> query = Query.and(ModelComponent.getComponentType(), TransformComponent.getComponentType(),
            DespawnComponent.getComponentType(), EntityStore.REGISTRY.getNonSerializedComponentType());

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class));
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> entities,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        if ("Beacon_Beam_Core".equals(entities.getComponent(index, ModelComponent.getComponentType()).getModel().getModelAssetId())) {
            Rotation3f rotation = entities.getComponent(index, TransformComponent.getComponentType()).getRotation();
            rotation.setYaw(beamYaw(rotation.yaw(), dt));
        }
    }

    /**
     * Avanza el giro del núcleo a una vuelta cada ocho segundos.
     * @param yaw ángulo actual en radianes
     * @param dt tiempo transcurrido en segundos
     * @return ángulo resultante, módulo dos pi
     */
    static float beamYaw(float yaw, float dt) {
        return (float) ((yaw + dt * Math.PI / 4) % (2 * Math.PI));
    }
}
