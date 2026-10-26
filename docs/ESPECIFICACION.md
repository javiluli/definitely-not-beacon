# Especificación de Definitely Not Beacon

Este documento define el comportamiento actual. La organización del código está en [ARQUITECTURA.md](ARQUITECTURA.md), los comandos en [DESARROLLO.md](DESARROLLO.md), la presentación del panel en [INTERFAZ.md](INTERFAZ.md) y el pipeline visual en [RECURSOS.md](RECURSOS.md).

## Alcance y compatibilidad

El producto se centra en el Beacon. Su fabricación, la pirámide, el pago, las seis mejoras, el haz y el pulso son partes de la misma mecánica. Actualmente no hay funcionalidades opcionales, comando `/beacon`, bienvenida ni `beacon_config.json`.

El mod se llama **Definitely Not Beacon**, versión pública **BETA-0.1**. Usa Java 25 y Hytale **0.7.4**, patchline `release`. El manifiesto declara `=0.7.4` e incluye el paquete de assets. Su `Name` y nombre visible es `Definitely Not Beacon`; la identidad nativa es `HytaleModding:Definitely Not Beacon` y la entrada, `dev.hytalemodding.DefinitelyNotBeaconPlugin`. El JAR conserva el identificador sin espacios `DefinitelyNotBeacon`. `gradle.properties` mantiene la etiqueta pública (`release_label`) y su versión SemVer para Hytale (`version = 0.1.0-beta`); no implica soporte de otras versiones del servidor.

Durante la beta, cualquier cambio incompatible requiere identificar su alcance y migración. Los nombres `Beacon_*` identifican la mecánica, no la marca del mod; el hierro del mod se identifica como `Iron_Block`.

## Obtención y bloques

Los dos objetos se fabrican mediante las recetas nativas declaradas en sus JSON de item:

| Resultado | Mesa y categoría | Materiales |
| --- | --- | --- |
| 1 `Iron_Block` | Constructor (`Builders`, `StructuralCrafting`), bloques (`Bricks`) | 10 `Ingredient_Bar_Iron` |
| 1 `Beacon_Block` | Arcanista (`Arcanebench`, `Crafting`), Diversos (`Arcane_Misc`) | 20 `Ingredient_Crystal_Cyan`, 20 `Rock_Basalt_Brick`, 8 `Ingredient_Bar_Thorium`, 10 `Ingredient_Life_Essence`, 1 `Ingredient_Gobliterator_Core` |

El mod no añade Beacon ni bloques de hierro al botín del Gobliterator ni modifica su tabla nativa. El núcleo sigue siendo un material nativo y se consume en la receta del Beacon. Ambos objetos admiten pilas de 100 unidades.

`Iron_Block` sustituye directamente a `Beacon_Iron_Block`, sin alias ni migración por decisión del propietario. Los bloques y objetos de hierro guardados con el ID anterior necesitan recrearse; la selección persistida del Beacon mantiene su codec.

Los bloques son `Beacon_Block` y `Iron_Block`. La pirámide requiere este segundo bloque: el hierro nativo no cuenta. El Beacon tiene calidad Legendary y las partículas nativas de caída correspondientes; el hierro conserva su calidad habitual.

## Pirámide y alcance

Las capas deben ser sólidas, centradas y consecutivas, empezando por la capa inmediatamente inferior al Beacon.

| Nivel | Capas | Hierro acumulado | Radio esférico | Duración renovable | Desbloquea |
| --- | --- | --- | --- | --- | --- |
| I | 3×3 | 9 | 60 bloques | 11 s | Velocidad y Prisa |
| II | +5×5 | 34 | 90 bloques | 13 s | Resistencia y Salto |
| III | +7×7 | 83 | 120 bloques | 15 s | Fuerza |
| IV | +9×9 | 164 | 150 bloques | 17 s | Regeneración I o principal II |

El centro del alcance es el centro del bloque y su borde está incluido. Se inspeccionan solo secciones cargadas; las bases pueden cruzar sus límites. No se comprueban obstáculos hacia el cielo.

`BeaconStatusSystem` reconstruye las bases y renueva poderes cada segundo. Entre esas revisiones comprueba los colores del haz con un intervalo de 50 ms, sin repetir el descubrimiento ni el reparto de mejoras. El haz indica una base válida aunque todavía no haya una selección pagada; las mejoras requieren pago.

## Selección y pago

La acción `Beacon_Open` abre una página con poderes, depósito real, almacenamiento y barra rápida. La elección provisional empieza en Prisa sin secundario, o recupera la selección guardada. La apertura requiere jugador, ventana disponible, referencia válida y distancia máxima de ocho bloques. Las acciones vuelven a validar distancia e identidad de la entidad del bloque.

La mejora principal puede ser Velocidad, Prisa, Resistencia, Salto o Fuerza, según el nivel. Solo en IV se permite Regeneración I o elevar el principal a II. Pulsar otra vez el secundario lo desmarca.

1. El jugador arrastra objetos entre su almacenamiento, barra y depósito. El servidor valida origen, destino, índices, cantidad e ID contra las existencias actuales.
2. El depósito acepta IDs con prefijo `Ingredient_Bar_`. Es una regla por identificador, no por material o etiqueta.
3. Aceptar valida el nivel y la selección. Si coincide con la configuración pagada, no cobra otra vez.
4. Para una configuración nueva consume una unidad depositada, guarda la selección y devuelve el sobrante. El panel permanece abierto.
5. Si el guardado lanza `RuntimeException`, intenta devolver el objeto cobrado, conservando sus metadatos, y propaga el error.

La tecla de interacción (F por defecto) abre y cierra la página; no hay botón Cancelar. Cerrar mediante esa tecla, Escape o desconexión devuelve el depósito sin confirmar la elección provisional. La devolución es idempotente; si el inventario no admite el sobrante, se deja como objeto junto al jugador. Arrastrar o seleccionar un poder no activa por sí mismo ninguna mejora.

El verde identifica mejoras pagadas; el azul, elecciones pendientes. Ordenar afecta al almacenamiento y conserva la barra rápida. Los detalles de controles y dimensiones pertenecen a [INTERFAZ.md](INTERFAZ.md).

## Mejoras

`BeaconPower` es el punto de ajuste del balance. Los porcentajes siguientes describen sus valores actuales; los tooltips se construyen con esos mismos valores.

| Poder | Intensidad I | Intensidad II |
| --- | --- | --- |
| Velocidad | +40 % de movimiento horizontal; −40 % de gasto de stamina al esprintar | +60 % de movimiento; −60 % de gasto |
| Prisa | +20 % de progreso por golpe con pico y +40 % de cadencia | +40 % de progreso y +60 % de cadencia |
| Resistencia | +5 % de protección física y de proyectiles; +9 de salud máxima | +8 % y +14 de salud máxima |
| Salto | +15 % de impulso vertical y −20 % de daño por caída | +30 % y −40 % |
| Fuerza | +40 % de daño físico cuerpo a cuerpo | +80 % |
| Regeneración | 2 puntos de vida cada segundo | No existe |

Prisa reconoce la familia de animación `Pickaxe` para el progreso de picado. Su cadencia usa variantes de interacciones y animaciones publicadas al arrancar, sin modificar globalmente los originales y conservando la identidad de cooldowns. Los resultados por segundo dependen de golpes enteros, material y tiempos del juego.

Velocidad conserva el drenaje nativo de sprint y lo multiplica por 0,6/0,4 mientras su intensidad esté activa, incluido el residual. No altera capacidad de stamina, regeneración, planeo ni costes de ataques; sin Velocidad se usa el gasto normal. Si coinciden ambas intensidades, solo se aplica II.

Fuerza excluye proyectiles y causas no físicas. Resistencia protege únicamente causas físicas o de proyectiles, incluidas sus derivadas, y respeta las que omiten resistencias. Suma su porcentaje a la protección nativa de esa categoría: 9 % de armadura y 5 % del Beacon dan 14 %. El cálculo conserva el filtro posterior a armadura y compensa lo ya reducido, sin duplicarlo ni generar daño negativo. El bono de salud máxima usa el modificador aditivo nativo del equipo: no cura al activarse y se retira al caducar. Salto modifica impulso, no una altura fija; conserva una base y el último valor aplicado para evitar acumular su propio multiplicador.

Regeneración usa el mismo cambio nativo de salud fija que `Healing_Totem_Heal`, reducido de 5 a 2 puntos por segundo. Renovar el efecto conserva su reloj y no permite exceder la salud máxima. El panel y el estado activo utilizan el icono nativo del tótem; no se añaden su área, partículas ni sonidos.

En solapamientos gana la intensidad mayor y, a igual intensidad positiva, la duración mayor; los bonos no se suman. Si una fuente efectiva baja de II a I, el descenso es inmediato. Si ya no hay ninguna fuente, queda el efecto residual hasta caducar. Bajar de IV a III suspende el secundario y deja el principal I cuando su nivel lo permite; reparar restaura la selección pagada sin otro cobro.

## Persistencia y compatibilidad de datos

`BeaconSelection` se guarda como componente del bloque, con campos `Primary` y `Secondary`. Sus valores normales son los nombres de `BeaconPower` y `NONE`, `UPGRADE` o `REGENERATION`. Los campos ausentes toman `Primary=""` y `Secondary="NONE"`. El guardado marca el bloque para escritura posterior; no promete una escritura síncrona en disco.

Romper el Beacon elimina su selección. Romper una capa no borra lo pagado. El estado temporal de niveles, impulsos y haces pertenece a cada mundo y se reconstruye; las entidades visuales no se serializan.

El codec conserva cadenas desconocidas y nulos explícitos. La validación de una compra nueva y la lectura de una selección guardada son contratos distintos: un secundario desconocido conserva el principal I; Regeneración guardada depende del secundario y del nivel. La comparación exacta de una selección con principal coincidente y secundario nulo puede lanzar `NullPointerException`. Es un límite conocido cubierto por la fixture; no existe reparación automática de esos datos.

## Presentación e idiomas

El bloque mantiene su representación nativa en mano y luz `#567` de radio 1. El núcleo azul es `fullbright` y tiene un pulso declarativo. El haz conserva núcleo giratorio, halo fijo, flujo de textura y altura de 4096 bloques. Sin cambios de color usa dos entidades; con cristales, el núcleo se divide en tramos que comparten la fase de giro. Sus vidas de tres segundos se renuevan cada segundo; las referencias inválidas provocan su reconstrucción.

El haz recorre su columna vertical hasta el límite del mundo en secciones cargadas. Detecta cristales pequeños `Rock_Crystal_*_Small` colocados sobre el Beacon o lateralmente a cualquier altura, incluso separados por aire y otros bloques. Cada cambio empieza en el bloque del cristal; el tramo inferior conserva su color anterior.

La transición ocupa ocho pasos dentro de ese bloque. Por encima se aplica el promedio RGB acumulado de los cristales encontrados: azul y rojo producen morado a partir del segundo. Se usa el color de textura de Hytale y, si falta, el de partículas. El canal dominante se intensifica a 255 conservando el tono. Los cristales blancos solos conservan el haz original y participan en mezclas con otros colores; el halo mantiene su transparencia.

Retirar un cristal recalcula los tramos superiores y retirar todos restaura las dos entidades originales. No cambian niveles, mejoras, pagos ni persistencia; el trazado se reconstruye desde los bloques.

El Beacon admite soporte superior para colocar un cristal. Los cristales conservan sus reglas vanilla: no se añade soporte para poner un cristal sobre otro. Para formar una columna de varios colores se necesitan apoyos válidos, por ejemplo laterales. Las entidades del haz son intangibles para permitir apuntar a la cara superior del Beacon, colocar bloques y abrir su panel con la tecla de interacción.

El panel, los nombres y los avisos usan los catálogos `Server/Languages/en-US/beacon.lang` y `es-ES/beacon.lang`. Los mensajes nativos dejan la traducción al cliente. Los nombres estáticos de efectos no repiten porcentajes. Los recursos y las limitaciones de reproducción están en [RECURSOS.md](RECURSOS.md).

## Límites de verificación

La fixture nativa usa actores sin cliente. Comprueba reglas y handlers reales, pero no certifica el recorrido completo de un jugador conectado, su movimiento o el renderizado. Las pruebas con cliente, amigos y otros mods se registran aparte siguiendo [DESARROLLO.md](DESARROLLO.md). El acceso al cielo y otras ampliaciones requieren una decisión funcional posterior.
