package dev.hytalemodding.beacon;

import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;

/** Entrada exclusiva de profiling: ejecuta las verificaciones y después la carga medida. */
public final class BeaconProfilePlugin extends BeaconSmokePlugin {
    public BeaconProfilePlugin(JavaPluginInit init) { super(init, 240); }

    /** Cierra la grabación de arranque antes de preparar los actores de la fixture. */
    @Override
    protected void begin() {
        BeaconPerformanceProbe.stopStartupRecording();
        super.begin();
    }

    /** Inicia las mediciones en el mundo desechable después de verificar su persistencia. */
    @Override
    protected void checksPassed(World world) {
        var registered = registeredSystem(BeaconStatusSystem.class);
        BeaconPerformanceProbe.run(world, registered.instance(), registered.index(), this::finish);
    }
}
