package dev.hytalemodding.beacon;

/**
 * Catálogo y único punto de ajuste del balance de Beacon para Hytale.
 *
 * <p>Los bonos se expresan como fracciones: {@code .20f} significa un 20 %,
 * salvo Regeneración, que indica puntos de vida por pulso.
 * La intensidad 0 desactiva la mejora; I y II usan los valores explícitos de cada
 * entrada. La intensidad II requiere la pirámide completa y sustituye Regeneración.</p>
 *
 * <ul>
 * <li>Prisa aumenta por separado el progreso por golpe y la cadencia. Se combinan:
 * aproximadamente 1.20 × 1.40 = 1.68 y 1.40 × 1.60 = 2.24 de progreso por segundo,
 * sujeto a golpes enteros, recargas y restricciones del material.</li>
 * <li>Fuerza escala el daño físico cuerpo a cuerpo; no añade puntos fijos que
 * favorecerían desproporcionadamente a las armas rápidas. Excluye proyectiles.</li>
 * <li>Salto modifica el impulso vertical actual, no una altura fija. Con gravedad
 * constante, un 15/30 % de impulso implica aproximadamente un 32/69 % más de altura.
 * No promete alturas de Minecraft: las físicas y los ajustes del personaje difieren.</li>
 * <li>Resistencia suma protección física y de proyectiles a la armadura y añade
 * salud máxima con el mismo modificador aditivo nativo que el equipo.</li>
 * <li>Regeneración usa puntos de vida fijos, como el tótem de curación;
 * su intervalo se expresa en segundos.</li>
 * </ul>
 *
 * <p>{@link BeaconEffects#load()} aplica estos valores a los efectos nativos al iniciar
 * el servidor. Los JSON de recursos solo contienen presentación. Reinicia el
 * servidor tras ajustar esta clase; regenera la referencia con {@code gradlew javadoc}.</p>
 */
public enum BeaconPower {
    /** Movimiento horizontal y reducción del gasto de stamina al esprintar, con el mismo porcentaje. */
    SPEED(1, .40f, .60f),
    /** Progreso de picado por golpe con pico; la cadencia se configura debajo. */
    HASTE(1, .20f, .40f),
    /** Protección física y de proyectiles sumada a la armadura; también aporta salud máxima. */
    RESISTANCE(2, .05f, .08f),
    /** Aumento del impulso vertical sobre el valor actual del personaje. */
    JUMP_BOOST(2, .15f, .30f),
    /** Daño físico cuerpo a cuerpo; excluye proyectiles, magia y daño ambiental. */
    STRENGTH(3, .40f, .80f),
    /** Puntos de vida recuperados en cada pulso; no existe Regeneración II. */
    REGENERATION(4, 2f, 0f);

    /** Reducción del daño por caída por intensidad de Salto: 20/40 %. */
    public static final float JUMP_FALL_REDUCTION_PER_RANK = .20f;
    /** Intervalo entre pulsos de Regeneración, sin reiniciar su temporizador al renovar. */
    public static final float REGENERATION_INTERVAL_SECONDS = 1f;

    /** Primer nivel de pirámide que desbloquea este poder. */
    public final int tier;
    /** Bono de intensidad I: fracción del valor base o vida por pulso en Regeneración. */
    public final float bonusI;
    /** Bono de intensidad II; cero en Regeneración porque no admite II. */
    public final float bonusII;

    BeaconPower(int tier, float bonusI, float bonusII) {
        this.tier = tier;
        this.bonusI = bonusI;
        this.bonusII = bonusII;
    }

    /**
     * Obtiene el bono como fracción del valor base, o vida por pulso en Regeneración.
     * @param rank intensidad 0, I o II; Regeneración no admite II
     * @return cero al desactivar, o el bono de la intensidad indicada
     * @throws IllegalArgumentException si este poder no admite la intensidad
     */
    public float bonus(int rank) {
        if (rank == 0) return 0f;
        if (rank == 1) return bonusI;
        if (rank == 2 && this != REGENERATION) return bonusII;
        throw new IllegalArgumentException("Invalid " + this + " rank: " + rank);
    }

    /** @return multiplicador del valor base; Resistencia suma su bono a la protección nativa */
    public float multiplier(int rank) { return 1f + bonus(rank); }

    /** Cadencia adicional de Prisa: 40/60 %; cero cuando no está activa. */
    public static float hasteAttackBonus(int rank) {
        return switch (rank) {
            case 0 -> 0f;
            case 1 -> .40f;
            case 2 -> .60f;
            default -> throw new IllegalArgumentException("Invalid HASTE rank: " + rank);
        };
    }

    /** Salud máxima adicional de Resistencia: 9/14 puntos, sin curación instantánea. */
    public static float resistanceHealth(int rank) {
        return switch (rank) {
            case 0 -> 0f;
            case 1 -> 9f;
            case 2 -> 14f;
            default -> throw new IllegalArgumentException("Invalid RESISTANCE rank: " + rank);
        };
    }

    /** @return radio esférico de 60/90/120/150 bloques; cero para una base inactiva */
    public static int radius(int pyramidTier) { return pyramidTier >= 1 && pyramidTier <= 4 ? 30 + 30 * pyramidTier : 0; }

    /** @return duración renovable de 11/13/15/17 segundos; cero si no hay base válida */
    public static int durationSeconds(int pyramidTier) { return pyramidTier >= 1 && pyramidTier <= 4 ? 9 + 2 * pyramidTier : 0; }

    /** @return identificador estable del efecto nativo; se conserva entre versiones del mod */
    public String effectId(int rank) { return "Beacon_" + name() + "_" + rank; }

}
