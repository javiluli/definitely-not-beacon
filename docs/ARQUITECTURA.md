# Arquitectura

Definitely Not Beacon es un módulo Gradle y un JAR de producción. `DefinitelyNotBeaconPlugin` registra e inicia directamente la mecánica; `dev.hytalemodding.beacon` contiene sus reglas, sistemas y presentación. La especificación funcional está en [ESPECIFICACION.md](ESPECIFICACION.md).

## Mapa de dependencias

~~~mermaid
flowchart TD
    Plugin[DefinitelyNotBeaconPlugin] --> Systems[Sistemas y registros del Beacon]
    Plugin --> Assets[Efectos y variantes de Prisa]
    Page[BeaconPage] --> View[BeaconPageView y BeaconText]
    Page --> Payment[BeaconPaymentWindow]
    Page --> Selection[BeaconSelection]
    Status[BeaconStatusSystem] --> State[BeaconWorldState]
    State --> Beam[BeaconBeam]
    Systems --> Status
~~~

`DefinitelyNotBeaconPlugin.setup` reúne los registros nativos y `DefinitelyNotBeaconPlugin.start` publica los assets funcionales. Las clases del Beacon conservan las reglas y operaciones de juego; las recetas se cargan directamente desde los JSON de item.

## Directorios

~~~text
src/main/java/dev/hytalemodding/
  DefinitelyNotBeaconPlugin.java
  beacon/                       mecánica completa
src/main/resources/
  manifest.json
  Common/                       rutas usadas por el cliente
  Server/Item/Items/Beacon/
  Server/Models/Beacon/
  Server/Entity/Effects/Beacon/
  Server/Entity/ModelVFX/Beacon/
  Server/Particles/Beacon/
  Server/Languages/{en-US,es-ES}/beacon.lang
src/test/java/                  pruebas Jupiter sin servidor
src/integrationTest/java/       fixture nativa funcional
src/profiling/java/             entrada y sonda de medición
tools/
  test-beacon-native.ps1
  testing/                      validaciones y ensayos del pipeline
  diagnostics/                  comprobación de compatibilidad
  profiling/                    lector JFR
  assets/                       generadores; legacy conserva una entrada anterior
art/beacon/reference/           entradas originales del conversor
public/                         fuentes y referencias visuales
docs/                           documentación vigente del mod
.agents/skills/                  skills instaladas
~~~

La fixture y profiling conservan el paquete Java del Beacon para acceder a helpers internos. Sus conjuntos de fuentes solo dependen de producción y, en profiling, de la fixture. `src/main` no depende de ellos. Las rutas de assets se detallan en [RECURSOS.md](RECURSOS.md).

La entrada del manifiesto es `dev.hytalemodding.DefinitelyNotBeaconPlugin`. Los identificadores de assets, el registro `BeaconSelection` y sus campos `Primary` y `Secondary` son contratos de juego; no dependen del nombre del paquete Java.

## Responsabilidades

| Componente | Responsabilidad |
| --- | --- |
| `DefinitelyNotBeaconPlugin` | Registrar componentes, interacción y sistemas; publicar los assets funcionales en su fase |
| `BeaconPower` y `BeaconRules` | Balance y reglas puras, sin acceso a mundos o inventarios |
| `BeaconStructures` | Descubrir y validar bases en secciones cargadas |
| `BeaconSelection` | Codec, lectura y guardado de la elección del bloque |
| `BeaconOpenInteraction` | Validar la apertura y enlazar página con ventana nativa |
| `BeaconPage` | Interpretar eventos, editar la elección provisional y coordinar el pago |
| `BeaconPageView` y `BeaconText` | Construir comandos de UI y mensajes traducibles; no cobran ni guardan |
| `BeaconPaymentWindow` | Resolver los contenedores del jugador, filtrar el depósito, validar transferencias, cobrar y devolver objetos |
| `BeaconStatusSystem` y `BeaconWorldState` | Coordinar la actualización y conservar estado temporal por mundo |
| `BeaconBeam` y `BeaconBeamRotationSystem` | Mantener halo y tramos del núcleo, reutilizar sus entidades y conservar su fase de giro antes de replicarla |
| `BeaconCrystals` | Trazar cristales a cualquier altura, mezclar colores y transiciones y publicar tintes, sin modificar bloques nativos |
| `BeaconBeamModels` | Preparar en `start` las piezas empaquetadas y las extensiones superiores desde la plantilla del núcleo |
| `BeaconEffects` | Publicar balance sobre plantillas de efectos ya cargadas |
| `BeaconMiningSystem`, `BeaconStrengthSystem` y `BeaconResistanceSystem` | Adaptar picado y daño en el punto correspondiente del motor |
| `BeaconAttackSpeedSystem` y su `Cleanup` | Aplicar variantes de Prisa y retirar sustituciones temporales propias |
| `BeaconHasteAssets` | Transformar el grafo de interacciones y animaciones conservando sus contratos |

Pago, VFX e idiomas forman parte de la misma mecánica. Sus clases colaboran mediante llamadas y dependencias explícitas dentro del paquete del Beacon. La fabricación usa recetas nativas en los assets, sin sistemas Java ni modificaciones del botín.

## Ciclo de vida y registros

El constructor recibe el contexto nativo. Los sistemas se crean en `setup` para consultar los tipos del motor cuando sus registros ya están disponibles. La instancia de ataques se conserva para publicar sus variantes en `start`.

`DefinitelyNotBeaconPlugin.setup` registra, en este orden:

1. Componente `BeaconSelection` e interacción `Beacon_Open`.
2. `BeaconStatusSystem` y `BeaconBeamRotationSystem`.
3. `BeaconMiningSystem`, `BeaconResistanceSystem` y `BeaconStrengthSystem`.
4. `BeaconAttackSpeedSystem` y su `Cleanup`.

El orden de registro no sustituye las dependencias ECS. `start` prepara las piezas del núcleo, publica efectos y genera variantes de ataques cuando ya están cargadas sus plantillas. Estas transformaciones en memoria son necesarias para jugar; los generadores de arte son herramientas distintas.

Los errores de registro y publicación se propagan al ciclo de vida nativo. Hytale gestiona el fallo del plugin y la limpieza de sus registros. El Beacon mantiene su estado por mundo y la temporalidad de sus capas; no necesita un cierre propio que recorra mundos desde otro hilo. Las actualizaciones requieren detener y arrancar de nuevo el servidor completo.

El puente utiliza el ciclo de [JavaPlugin](https://docs.hytale.com/api/com/hypixel/hytale/server/core/plugin/JavaPlugin). Los contratos de orden y limpieza se contrastan con las fuentes del servidor 0.7.4; una actualización exige verificarlos de nuevo.

## Hilos, referencias y estado

- Usa `Store` y los componentes desde el hilo propietario del mundo. Los callbacks asíncronos deben volver a `world.execute` antes de acceder a ellos.
- Respeta el `CommandBuffer` recibido en los recorridos ECS y las referencias inválidas. `forEachChunk` consume el buffer antes de que el coordinador reconcilie entidades del haz.
- `BeaconWorldState` es un recurso sin codec: niveles, base de impulso y gestor de haces son temporales y separados por mundo. Su clon empieza vacío.
- `BeaconSelection` es el dato persistido. No copies referencias de entidades a singletons o a otro mundo.

`BeaconCrystals` prepara la paleta nativa y su transición desde blanco en `start`. Las mezclas nuevas se publican con futuros compartidos fuera del hilo del mundo: `World.tick` mantiene el bloqueo de lectura de assets y no permite adquirir allí el de escritura. `BeaconBeam` solicita juntos los colores de un trazado y aplica sus ModelVFX cuando están listos, conservando el halo y reutilizando piezas por orden. La caché contiene assets, sin referencias a mundos.

La revisión visual compara identidad y contadores locales de las secciones para omitir columnas intactas. En 0.7.4, `BlockSection.set` invalida la luz local e incrementa ese contador. Una lectura completa cada segundo cubre su posible desbordamiento de 16 bits. El trazado deseado se compara con el aplicado antes de consultar tintes o modificar componentes; una publicación pendiente se revisa aunque los bloques ya no cambien. El reloj de poderes/bases (1 s) pertenece a `BeaconWorldState`, separado del intervalo visual de 50 ms.

Todas las entidades del haz se anclan en la sección activa del Beacon; sus modelos desplazan la geometría hasta la altura visual del tramo. La extensión que sigue al último cristal usa una sola entidad anclada en el origen del haz. `UpdateLocationSystems` retira entidades en secciones inexistentes o inactivas, incluso si están cargadas: anclar piezas a cristales altos provocaba recreaciones periódicas. No se activan secciones superiores para mantener los VFX. Los sistemas nativos de `ModelComponent` crean y actualizan el `BoundingBox`; un trazado intacto conserva entidades, modelos y animaciones.

El orden relativo del daño es **efectos salientes → Fuerza → armadura → Resistencia**. La rotación se ejecuta antes de `TransformSystems.EntityTrackerUpdate`; la sustitución de ataques, antes de `InteractionSystems.TickInteractionManagerSystem`. El cleanup de ataques conserva su posición anterior al guardado del jugador.

`BeaconEffects` publica movimiento horizontal, salud máxima aditiva de Resistencia (`RawStatModifiers`) y pulsos de salud fija de Regeneración (`StatModifiers`). Hytale aplica y retira el máximo mediante `StatModifiersManager`, junto con el equipo; el Beacon no mantiene una segunda caché de salud. `BeaconResistanceSystem` consulta `ArmorDamageReduction.getResistanceModifiers` y suma protección física o de proyectiles compensando la reducción ya aplicada. Conserva el orden nativo del daño y la protección de caída de Salto.

El ahorro de sprint se publica en `start` desde `BeaconEffects`: conserva el asset `Stamina` cargado y añade `RegeneratingModifier` con condiciones `HasEffect` al drenaje condicionado por `Sprinting`. El regenerador nativo resuelve el jugador y la intensidad; no hay reembolsos de stamina ni un sistema de ticks adicional. La condición de I excluye II para evitar multiplicar bonos.

## Pago y UI

La página conserva el orden `validar → comprobar si ya está pagado → cobrar → guardar → devolver sobrante`. Un fallo de guardado intenta compensar el cobro con el objeto original. `BeaconPaymentWindow.containerForGrid` resuelve las cuadrículas y `containerForSection` limita los orígenes del cliente a los inventarios del jugador y a su propio depósito. La página y la vista consultan estos métodos; la vista construye los comandos visuales.

`validSelection` valida compras nuevas; `strength` interpreta elecciones pagadas, incluso al degradar la base. Sustituir uno por otro cambia el comportamiento. Los detalles históricos del codec y sus límites están en [ESPECIFICACION.md](ESPECIFICACION.md).

`BeaconPageView` adapta inventarios y estado a [UICommandBuilder](https://docs.hytale.com/api/com/hypixel/hytale/server/core/ui/builder/UICommandBuilder). `BeaconText` usa `Message` con parámetros numéricos y mensajes anidados; no hay un traductor propio. Las convenciones del documento del cliente están en [INTERFAZ.md](INTERFAZ.md).

## Trabajar sobre esta arquitectura

Las convenciones permanentes de Java, Javadoc y revisión de métodos están en
[AGENTS.md](../AGENTS.md#cambios-y-java). Los procedimientos para ampliar o mantener
la mecánica se seleccionan desde [DESARROLLO.md](DESARROLLO.md#skills-locales).
El balance sigue en `BeaconPower` y los idiomas en sus catálogos completos.
