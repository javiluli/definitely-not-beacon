package dev.hytalemodding.beacon;

import com.hypixel.hytale.server.core.Message;

/** Compone mensajes nativos traducibles usando el mismo balance que aplica el servidor. */
final class BeaconText {
    private BeaconText() {}

    /**
     * Deja la traducción al cliente, incluidos los mensajes anidados del nombre y la descripción.
     * @param rank intensidad I o II; Regeneración solo admite I
     * @param active añade el indicador de mejora pagada
     * @return tooltip con cifras obtenidas de {@link BeaconPower}
     */
    static Message powerTooltip(BeaconPower power, int rank, boolean active) {
        String key = "beacon.power." + power.name().replace("_", "");
        Message description = Message.translation(key + ".description")
                .param("amount", Math.round(power.bonus(rank) * (power == BeaconPower.REGENERATION ? 1 : 100)));
        switch (power) {
            case HASTE -> description.param("attack", Math.round(BeaconPower.hasteAttackBonus(rank) * 100));
            case RESISTANCE -> description.param("health", Math.round(BeaconPower.resistanceHealth(rank)));
            case JUMP_BOOST -> description.param("fall", Math.round(BeaconPower.JUMP_FALL_REDUCTION_PER_RANK * rank * 100));
            case REGENERATION -> description.param("interval", BeaconPower.REGENERATION_INTERVAL_SECONDS);
            default -> { }
        }
        return Message.translation(active ? "beacon.ui.powerActive" : "beacon.ui.power")
                .param("name", Message.translation(key + ".name"))
                .param("rank", rank == 1 ? "I" : "II")
                .param("description", description);
    }
}
