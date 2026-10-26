/**
 * Beacon de cuatro niveles con pirámides de hierro, seis mejoras y una interfaz
 * unificada de selección, inventario y pago. Los alcances esféricos van de 60 a 150 bloques.
 * {@link dev.hytalemodding.DefinitelyNotBeaconPlugin} reúne sus registros y publica los assets
 * después de la carga nativa.
 * Las recetas nativas fabrican el hierro en la mesa del constructor y el Beacon
 * en la del arcanista; el mod no modifica las recompensas del Gobliterator.
 *
 * <p>{@link dev.hytalemodding.beacon.BeaconStructures} descubre bloques en secciones
 * cargadas, incluidas bases que cruzan sus límites. {@link dev.hytalemodding.beacon.BeaconRules}
 * contiene las reglas puras que cubren las pruebas JUnit.</p>
 *
 * <p>{@link dev.hytalemodding.beacon.BeaconPower} concentra los valores de balance,
 * radios y duraciones. {@link dev.hytalemodding.beacon.BeaconEffects} los publica
 * en Hytale; las mejoras duran de 11 a 17 segundos tras la última renovación.
 * {@link dev.hytalemodding.beacon.BeaconStrengthSystem} limita Fuerza al combate
 * físico cuerpo a cuerpo y {@link dev.hytalemodding.beacon.BeaconResistanceSystem}
 * aplica Resistencia y la protección de caída de Salto.</p>
 *
 * <p>{@link dev.hytalemodding.beacon.BeaconStatusSystem} descubre bases y renueva
 * los efectos cada segundo. {@link dev.hytalemodding.beacon.BeaconWorldState} conserva
 * niveles, impulsos y un gestor de haces por mundo; al clonarse empieza vacío.
 * {@link dev.hytalemodding.beacon.BeaconBeam} crea, retira y renueva las capas temporales.
 * {@link dev.hytalemodding.beacon.BeaconBeamRotationSystem} rota solo el núcleo antes
 * de replicar su orientación. Las entidades del haz no se guardan con el mundo.</p>
 *
 * <p>{@link dev.hytalemodding.beacon.BeaconMiningSystem} modifica el daño que Hytale
 * ya ha calculado para el pico. {@link dev.hytalemodding.beacon.BeaconAttackSpeedSystem}
 * selecciona variantes de ataques creadas por {@link dev.hytalemodding.beacon.BeaconHasteAssets},
 * conservando los assets originales y la identidad de los tiempos de recarga.</p>
 *
 * <p>{@link dev.hytalemodding.beacon.BeaconPage} edita una selección provisional y
 * {@link dev.hytalemodding.beacon.BeaconPaymentWindow} devuelve los objetos al cerrar.
 * {@link dev.hytalemodding.beacon.BeaconPageView} prepara los comandos visuales;
 * {@link dev.hytalemodding.beacon.BeaconText} compone mensajes para los catálogos
 * {@code beacon.lang} de inglés y español, sin duplicar las cifras de balance.
 * {@link dev.hytalemodding.beacon.BeaconSelection} guarda la selección confirmada en
 * el bloque. Esta versión no comprueba obstáculos hacia el cielo.</p>
 */
package dev.hytalemodding.beacon;
