package dev.hytalemodding.beacon;

/** Reglas puras de base, poderes, alcance, progreso de picado y tiempos de ataque. */
final class BeaconRules {
    static final String SECONDARY_NONE = "NONE";
    static final String SECONDARY_UPGRADE = "UPGRADE";

    private BeaconRules() {}

    /** Consulta una pirámide completa, desde 3×3 a 9×9; no acepta niveles con huecos. */
    static int pyramidLevel(IronAtOffset iron) {
        for (int depth = 1; depth <= 4; depth++) {
            for (int x = -depth; x <= depth; x++) {
                for (int z = -depth; z <= depth; z++) {
                    if (!iron.test(x, depth, z)) return depth - 1;
                }
            }
        }
        return 4;
    }

    /** Consulta el hierro a un desplazamiento horizontal y una profundidad positiva bajo el Beacon. */
    @FunctionalInterface
    interface IronAtOffset { boolean test(int x, int depth, int z); }

    /**
     * Valida una elección nueva antes de cobrar; no invalida una elección pagada al bajar de nivel.
     * @param tier nivel actual de la pirámide
     * @param primary poder principal; {@code null} y Regeneración no son opciones principales
     * @param secondary {@code NONE}, {@code UPGRADE} o el nombre de Regeneración
     * @return si el nivel permite pagar la elección; rechaza secundarios desconocidos o nulos
     */
    static boolean validSelection(int tier, BeaconPower primary, String secondary) {
        if (primary == null || primary == BeaconPower.REGENERATION || tier < primary.tier) return false;
        if (SECONDARY_NONE.equals(secondary)) return true;
        return tier == 4 && (SECONDARY_UPGRADE.equals(secondary) || BeaconPower.REGENERATION.name().equals(secondary));
    }

    static boolean isIngot(String itemId) {
        return itemId != null && itemId.startsWith("Ingredient_Bar_");
    }

    /**
     * Calcula una elección pagada sin normalizar los datos persistidos.
     * Un secundario desconocido conserva el principal I; Regeneración depende solo del secundario y nivel.
     * @param tier nivel actual; degradar y reparar suspende y restaura la mejora sin otro pago
     * @param primary nombre persistido del principal, que puede ser desconocido o nulo
     * @param secondary opción persistida, que puede ser desconocida o nula
     * @param power poder cuya intensidad se consulta
     * @return intensidad 0, I o II según el comportamiento histórico, sin validar un nuevo cobro
     */
    static int strength(int tier, String primary, String secondary, BeaconPower power) {
        if (tier < 1) return 0;
        if (power == BeaconPower.REGENERATION) return tier == 4 && BeaconPower.REGENERATION.name().equals(secondary) ? 1 : 0;
        if (!power.name().equals(primary) || tier < power.tier) return 0;
        return tier == 4 && SECONDARY_UPGRADE.equals(secondary) ? 2 : 1;
    }

    /**
     * Prefiere mayor intensidad y, a igualdad positiva, mayor duración en segundos.
     *
     * @return si el candidato sustituye la intensidad y duración elegidas; cero no gana un empate
     */
    static boolean shouldReplacePower(int strength, int duration, int candidateStrength, int candidateDuration) {
        return candidateStrength > strength
                || candidateStrength == strength && candidateStrength > 0 && candidateDuration > duration;
    }

    /** Comprueba la distancia al centro del bloque del Beacon, sin calcular una raíz cuadrada. */
    static boolean inRange(double playerX, double playerY, double playerZ, int x, int y, int z, int tier) {
        double dx = playerX - (x + 0.5);
        double dy = playerY - (y + 0.5);
        double dz = playerZ - (z + 0.5);
        int range = BeaconPower.radius(tier);
        return tier > 0 && dx * dx + dy * dy + dz * dz <= range * range;
    }

    /** Aplica el bono central de Prisa por golpe; los golpes necesarios siguen siendo enteros. */
    static float miningDamage(float damage, int strength) {
        return damage * BeaconPower.HASTE.multiplier(strength);
    }

    /** Reconoce la familia nativa de animación de picos y acepta identificadores nulos. */
    static boolean isPickaxe(String animations) {
        return "Pickaxe".equals(animations);
    }

    /** Multiplicador usado tanto en animaciones como en los tiempos de interacción. */
    static float attackSpeed(int strength) {
        return 1f + BeaconPower.hasteAttackBonus(strength);
    }
}
