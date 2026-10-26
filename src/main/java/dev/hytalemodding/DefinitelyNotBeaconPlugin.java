package dev.hytalemodding;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.beacon.BeaconAttackSpeedSystem;
import dev.hytalemodding.beacon.BeaconBeamRotationSystem;
import dev.hytalemodding.beacon.BeaconBeamModels;
import dev.hytalemodding.beacon.BeaconCrystals;
import dev.hytalemodding.beacon.BeaconEffects;
import dev.hytalemodding.beacon.BeaconMiningSystem;
import dev.hytalemodding.beacon.BeaconOpenInteraction;
import dev.hytalemodding.beacon.BeaconPower;
import dev.hytalemodding.beacon.BeaconResistanceSystem;
import dev.hytalemodding.beacon.BeaconSelection;
import dev.hytalemodding.beacon.BeaconStatusSystem;
import dev.hytalemodding.beacon.BeaconStrengthSystem;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/** Registra Definitely Not Beacon y publica sus mejoras cuando los assets nativos están cargados. */
public class DefinitelyNotBeaconPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.get("Definitely Not Beacon");

    /** Se crea en setup y recibe sus variantes en start, después de cargar los assets nativos. */
    private BeaconAttackSpeedSystem attackSpeed;

    /**
     * Construye el plugin sin registrar sistemas ni consultar assets todavía.
     * @param init contexto de creación del plugin
     */
    public DefinitelyNotBeaconPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    /** Registra la selección, la apertura y los sistemas ECS antes de cargar los mundos. */
    @Override
    protected void setup() {
        BeaconSelection.register(this);
        getCodecRegistry(Interaction.CODEC).register("Beacon_Open", BeaconOpenInteraction.class, BeaconOpenInteraction.CODEC);
        getEntityStoreRegistry().registerSystem(new BeaconStatusSystem());
        getEntityStoreRegistry().registerSystem(new BeaconBeamRotationSystem());
        getEntityStoreRegistry().registerSystem(new BeaconMiningSystem());
        getEntityStoreRegistry().registerSystem(new BeaconResistanceSystem());
        getEntityStoreRegistry().registerSystem(new BeaconStrengthSystem());
        attackSpeed = new BeaconAttackSpeedSystem();
        getEntityStoreRegistry().registerSystem(attackSpeed);
        getEntityStoreRegistry().registerSystem(new BeaconAttackSpeedSystem.Cleanup());
    }

    /** Publica balance y ataques después de cargar sus plantillas nativas. */
    @Override
    protected void start() {
        BeaconBeamModels.load();
        BeaconCrystals.loadPalette();
        BeaconEffects.load();
        attackSpeed.loadAssets();
        ModelAsset halo = ModelAsset.getAssetMap().getAsset("Beacon_Beam_Halo");
        LOGGER.at(Level.INFO).log("Definitely Not Beacon " + getManifest().getVersion() + " ready: beam height="
                + (halo == null ? "missing model" : halo.getBoundingBox().height())
                + "; tiers=1-4; haste mining bonus=" + BeaconPower.HASTE.bonus(1)
                + "; haste attack bonus I/II=" + BeaconPower.hasteAttackBonus(1) + "/" + BeaconPower.hasteAttackBonus(2));
    }
}
