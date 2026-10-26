package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Estado temporal de un mundo, registrado sin codec y usado solo desde su hilo.
 * Las elecciones pagadas pertenecen a {@link BeaconSelection}; aquí no se guarda progreso del jugador.
 */
final class BeaconWorldState implements Resource<EntityStore> {
    /** Último descubrimiento completo de bases; se sustituye cada segundo. */
    Map<BeaconStructures.Position, Integer> levels = Map.of();
    /** Impulso base y último valor aplicado para no acumular el multiplicador de Salto. */
    final Map<Ref<EntityStore>, Jump> jumps = new HashMap<>();
    final BeaconBeam beams = new BeaconBeam();
    /** Cada mundo conserva su propio reloj; el primer tick descubre las bases inmediatamente. */
    float powerElapsed = 1f;

    record Jump(float base, float applied) {}

    /** Un clon empieza vacío: las referencias de entidades y los impulsos pertenecen al mundo original. */
    @Override
    public BeaconWorldState clone() {
        return new BeaconWorldState();
    }
}
