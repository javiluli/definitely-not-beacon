package dev.hytalemodding.beacon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reproduce el haz experimental solo para los espectadores de Beacons activos.
 * Conserva una emisión por jugador y Beacon durante siete segundos; no genera assets en ticks.
 */
final class BeaconParticles {
    static final float HEIGHT = 40f;
    static final float WIDTH = 0.65f;
    static final float BRIGHTNESS = 0.94f;
    static final float BASE_SCALE = 0.85f;
    static final float BASE_ROTATION = 0.5f;
    static final double VIEW_DISTANCE = 150d;
    static final int REFRESH_TICKS = 140;
    static final float EFFECT_DURATION = 8f;

    /** Cada mundo posee su propio mapa de espectadores y el último color enviado. */
    private final Map<BeaconStructures.Position, Map<UUID, Emission>> sent = new HashMap<>();

    private record Emission(long tick, int rgb) {}

    /** Solo se habilita cuando tanto el haz como la base recoloreable se cargaron. */
    static boolean ready() {
        return BeaconFxProfiles.available() && BeaconFxAssets.available();
    }

    /** Revisa cada segundo la actividad y los espectadores; no genera haces duplicados por tick. */
    void update(Set<BeaconStructures.Position> active, Store<EntityStore> store) {
        sent.keySet().removeIf(position -> !active.contains(position));
        if (active.isEmpty()) return;

        World world = store.getExternalData().getWorld();
        long tick = world.getTick();
        var profile = BeaconFxProfiles.select(HEIGHT, WIDTH, BRIGHTNESS);
        float actualHeight = profile.heightAt(WIDTH);
        String base = BeaconFxAssets.tintedBase(BASE_ROTATION);
        for (var position : active) {
            Vector3d origin = new Vector3d(position.x() + 0.5, position.y() + 0.1, position.z() + 0.5);
            Map<UUID, Emission> byPlayer = sent.computeIfAbsent(position, ignored -> new HashMap<>());
            Set<UUID> currentViewers = new HashSet<>();
            // El color se consulta una sola vez por renovación y Beacon, no por espectador.
            Integer rgb = null;
            for (PlayerRef player : world.getPlayerRefs()) {
                Ref<EntityStore> reference = player.getReference();
                if (reference == null || !reference.isValid()) continue;
                TransformComponent transform = store.getComponent(reference, TransformComponent.getComponentType());
                if (transform == null || !visible(origin, transform.getPosition())) continue;
                UUID id = player.getUuid();
                currentViewers.add(id);
                Emission previous = byPlayer.get(id);
                if (previous != null && !shouldRefresh(tick, previous.tick())) continue;
                if (rgb == null) rgb = tint(position, store);
                Color color = rgb < 0 ? null : new Color((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) (int) rgb);
                var recipients = List.of(reference);
                ParticleUtil.spawnParticleEffect(base, origin.x, origin.y, origin.z,
                        0f, 0f, 0f, BASE_SCALE, color, null, recipients, store, EFFECT_DURATION);
                Vector3d beamOrigin = new Vector3d(origin.x,
                        BeaconFxCommand.centerY(origin.y - 0.05, actualHeight,
                                BeaconFxCommand.automaticOffset(actualHeight)), origin.z);
                ParticleUtil.spawnParticleEffect(profile.assetId(), beamOrigin.x, beamOrigin.y, beamOrigin.z,
                        0f, 0f, 0f, WIDTH, color, null, recipients, store, EFFECT_DURATION);
                byPlayer.put(id, new Emission(tick, rgb));
            }
            // Al salir del alcance deja de renovar la emisión; un retorno genera el efecto de inmediato.
            byPlayer.keySet().retainAll(currentViewers);
        }
    }

    /** Evita duplicados durante el intervalo de vida de las partículas. */
    static boolean shouldRefresh(long tick, long previousTick) {
        return tick < previousTick || tick - previousTick >= REFRESH_TICKS;
    }

    /** Distancia horizontal; un haz alto sigue viéndose desde alturas distintas. */
    static boolean visible(Vector3d origin, Vector3d viewer) {
        double dx = origin.x - viewer.x;
        double dz = origin.z - viewer.z;
        return dx * dx + dz * dz <= VIEW_DISTANCE * VIEW_DISTANCE;
    }

    /** Devuelve -1 para blanco nativo o el último color resultante de los cristales. */
    private static int tint(BeaconStructures.Position position, Store<EntityStore> store) {
        var spans = BeaconCrystals.trace(store.getExternalData().getWorld().getChunkStore(), position);
        for (int i = spans.size() - 1; i >= 0; i--) {
            int color = spans.get(i).color();
            if (color >= 0) return BeaconCrystals.luminousColor(color);
        }
        return -1;
    }
}
