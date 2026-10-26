# Interfaz del Beacon

Esta guía conserva los detalles del panel y sus contratos con el cliente. Las
reglas de juego están en [ESPECIFICACION.md](ESPECIFICACION.md), los límites entre clases en
[ARQUITECTURA.md](ARQUITECTURA.md), las comprobaciones en
[DESARROLLO.md](DESARROLLO.md) y las fuentes visuales en [RECURSOS.md](RECURSOS.md).

## Diseño e interacción

La misma página contiene los poderes, el pago y el inventario. El botón
**ACEPTAR** está junto a la ranura; no hay botón Cancelar, instrucciones de clic,
botón «Solo principal» ni resumen superior de nivel y alcance. La tecla de
interacción (F por defecto) abre y cierra el panel; Escape también lo cierra.

Los poderes usan botones de 48 unidades con iconos de 32 y filas de 56, dejando
separación vertical. Selección y hover cambian el estilo completo del mismo
botón, sin superponer contornos. El borde verde muestra el poder ya pagado; azul
indica una elección pendiente. Aceptar mantiene abierto el panel y actualiza el
verde inmediatamente; repetir la selección actual no cobra. Pulsar otra vez el
secundario elegido lo desmarca. No hay resumen central de la selección. Los
tooltips leen el balance de `BeaconPower` y usan el estilo nativo con ajuste de
línea; las reducciones se escriben con el guion compatible con la fuente del cliente.

El inventario usa `Common.ui.@Container`: cabecera pequeña, marco y fondos de
Hytale. Las cuadrículas `ItemGrid` conservan casillas de 74 unidades, cantidades,
metadatos, tooltips y durabilidad. El botón de ordenar usa `SortType.TYPE` sobre
el almacenamiento; la barra rápida mantiene su orden y destaca la casilla activa.

La fila de pago está centrada como conjunto. Un lingote nativo a la izquierda del depósito indica el pago, con 28 unidades
libres a cada lado de un separador vertical y otras 28 hasta Aceptar. El botón
nativo pequeño mide 140 × 32. Los anclajes
explícitos mantienen los huecos sin depender de grupos espaciadores vacíos.
Aceptar usa los fondos primarios actuales y las etiquetas pequeñas de Hytale;
el estilo heredado `SmallDefaultTextButtonStyle` apunta a texturas ausentes en
esta versión del juego y no debe usarse.
Solo una línea horizontal separa poderes y pago;
no hay divisor debajo del pago. Arrastra objetos para moverlos, apilarlos o
intercambiarlos. La ranura del Beacon
solo admite `Ingredient_Bar_*`. Aceptar consume una unidad y devuelve el resto;
Cerrar con la tecla de interacción, Escape o desconectarse devuelve todo. Un inventario lleno deja el
sobrante como objeto junto al jugador. No se mueve nada al pulsar una casilla.

## Implementación

- `BeaconOpenInteraction` abre `openCustomPageWithWindows`; el gestor nativo
  mantiene el ciclo de vida del depósito, también al desconectar.
- `BeaconPage` usa `CanDismissOrCloseThroughInteraction` para cerrar con la tecla
  de interacción configurada, sin capturar F manualmente; `onDismiss` devuelve el depósito una sola vez.
- `BeaconPage` interpreta los eventos, mantiene la elección provisional y
  coordina validación, cobro y guardado. No construye las cuadrículas.
- `BeaconPageView` asigna `InventorySectionId` a almacenamiento, barra rápida y
  depósito. Cada `ItemGridSlot` debe tener `IsActivatable: true` para interactuar.
- `BeaconText` compone mensajes nativos con parámetros tomados de `BeaconPower`.
  El cliente traduce los catálogos `Server/Languages/en-US/beacon.lang` y
  `es-ES/beacon.lang`; cambiar una cifra de balance no exige duplicarla en ellos.
  Los `Message` se envían mediante `TextSpans` y `TooltipTextSpans`, como en las
  páginas nativas de Hytale. `Text` y `TooltipText` esperan cadenas: un mensaje
  vacío en `Text` puede desconectar al cliente y los parámetros `Message` anidados no se resuelven ahí.
- `Dropped` envía automáticamente campos numéricos: `SlotIndex`,
  `SourceInventorySectionId`, `SourceSlotId` e `ItemStackQuantity`, además de
  `ItemStackId`. El codec usa `Codec.INTEGER` para esos índices y cantidades.
  La acción y el destino se fijan al enlazar cada cuadrícula; no se bloquea la
  interfaz durante el evento de arrastre.
- `BeaconPaymentWindow` resuelve las cuadrículas de la página y acepta únicamente
  las secciones de este jugador y de su depósito. `containerForGrid` y
  `containerForSection` reúnen esa resolución para el controlador y la vista.
  `transfer` valida límites, cantidad y objeto actual antes
  de llamar a `moveItemStackFromSlotToSlot`. El filtro nativo impide introducir
  minerales o intercambiar un lingote por un objeto no admitido.
- El movimiento no retira objetos al iniciar el arrastre. Cancelarlo deja el
  inventario intacto. Tras cada acción se publican las existencias reales.
- Antes de pagar se comprueban base, nivel, identidad del bloque y distancia
  máxima de ocho bloques. Si el guardado lanza `RuntimeException`, se intenta
  devolver el objeto original antes de propagar el error.
  El cierre y la devolución son idempotentes.

## Recursos y comprobación

`Common/UI/Custom/Pages/Beacon.ui` define el panel. Los cinco iconos propios de poderes
están en `Common/UI/Custom/Beacon/`; Regeneración usa un `AssetImage` con la ruta
nativa `UI/StatusEffects/AddHealth/Tiny.png`, tomada del `StatusEffectIcon` de su
efecto. `AssetPath` se resuelve desde `Common`; `TexturePath`, desde el archivo
`.ui`. No copies el icono vanilla al mod. `Beacon/Hytale/` contiene las texturas
empaquetadas de las cuadrículas. Se conservan como imágenes finales; su
procedencia y reproducción se detallan en [RECURSOS.md](RECURSOS.md).
Conserva los PNG `@2x`, pero referencia sus rutas
sin ese sufijo: el cliente resuelve la escala automáticamente. No uses
`Anchor.MaxHeight` ni cambios parciales de `Anchor.Height`; modifica el `Anchor`
completo desde Java.

Los comandos, requisitos y resultados esperados están en
[DESARROLLO.md](DESARROLLO.md#verificaciones-automáticas).

La prueba nativa comprueba pilas, cantidades parciales, metadatos, reorganización,
movimientos inválidos, filtro y cobro de una unidad con devolución del sobrante.
También verifica codec de arrastre, persistencia, efectos, picado y limpieza del
rayo. Gestos, hover, selección y acabado visual requieren comprobar el cliente.

La API de referencia es
[UICommandBuilder](https://docs.hytale.com/api/com/hypixel/hytale/server/core/ui/builder/UICommandBuilder).
Los contratos de arrastre y codec se contrastan con el servidor y los assets
0.7.4 y con la fixture; el acabado visual sigue requiriendo cliente.
