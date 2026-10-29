# Recursos y efectos visuales

Definitely Not Beacon distribuye los recursos versionados en `src/main/resources`. Las herramientas de autoría escriben candidatos dentro de `build/`; compilar o iniciar el servidor no ejecuta esos generadores ni publica sus resultados.

## Fuentes y resultados

| Componente | Fuente mantenida | Resultado reproducible |
| --- | --- | --- |
| Bloque, atlas e icono del Beacon | `art/beacon/reference/beacon.json`, `glass.png`, `obsidian.png`, `beacon.png` | `Common/Blocks/Beacon/Beacon.blockymodel`, `Beacon_Texture.png` e `Common/Icons/Items/Beacon_Block_Icon.png` |
| Geometría y flujo del haz | `tools/assets/generate-beam-assets.ps1` | Dos modelos completos, 14 piezas del núcleo en `Common/VFX/Beacon/Sections/` y `Beacon_Beam_Flow.blockyanim` |
| Textura del haz | `public/Beacon_Beam_(texture)_JE2.png` | Copia exacta en `Common/VFX/Beacon/Beacon_Beam.png` |
| Cinco iconos propios de poderes | `public/beacon_effect_icons/*.png` | Copias exactas en `Common/UI/Custom/Beacon/` |
| Icono de la lista de mods | `public/icon.png` | `icon-256.png` en la raíz del JAR, ajustado a 256×256 con proporciones y margen |

Son veintisiete salidas: dieciocho modelos/animaciones JSON y nueve PNG. El icono de Mods se redimensiona con vecino más próximo y se centra sin recortar; el margen conserva el color de su esquina superior izquierda. El conversor del bloque conserva `fullbright` en el nodo `Inner beacon texture`, al que se vincula el pulso. El resto del bloque mantiene su sombreado original.

Regeneración referencia directamente `UI/StatusEffects/AddHealth/Tiny.png` de Hytale 0.7.4, usado por `Healing_Totem_Heal`. El efecto lo declara como `StatusEffectIcon`; el panel usa un `AssetImage` cuya propiedad `AssetPath` recibe esa misma ruta. No se copia ni se empaqueta ese icono. El validador estático reconoce esta dependencia vanilla y la fixture comprueba su existencia en `Common` y el comando enviado al panel.

Los nueve PNG de inventario en `Common/UI/Custom/Beacon/Hytale/`, la textura de hierro y su icono se mantienen como imágenes finales. No se ha demostrado una transformación reproducible desde `Assets.zip` o las referencias de hierro hasta esos once archivos. Se conservan íntegros; el pipeline no los reemplaza. Las demás imágenes de `public/` sirven de referencia visual.

Los JSON de servidor, los idiomas, la página `.ui`, las partículas y el ModelVFX del halo se editan directamente. `BeaconPower` sigue definiendo el balance. Las publicaciones en memoria de `BeaconEffects`, `BeaconHasteAssets`, `BeaconCrystals` y `BeaconBeamModels` forman parte del funcionamiento del mod y quedan fuera del pipeline de arte.

## Generar y comprobar

Usa Windows y PowerShell 7.4 o posterior; el renderizador del atlas e icono utiliza `System.Drawing`. El pipeline se ha verificado con PowerShell 7.6.5.

Desde la raíz del repositorio:

```powershell
./tools/assets/generate-beacon-assets.ps1
./tools/testing/test-beacon-assets.ps1
```

`tools/testing/test-beacon-pipeline.ps1` ensaya generación idéntica entre procesos y rechazo de modelos modificados, texturas ausentes, límites incorrectos e IDs duplicados. Solo modifica copias bajo `build/`.

El primer comando genera en `build/generated-assets/beacon/`. El segundo regenera candidatos y comprueba equivalencia JSON, hashes de PNG, referencias de UI y de `Common`, IDs duplicados, canales de animación y altura del haz frente a su `HitBox`.

Para revisar otra generación:

```powershell
./tools/assets/generate-beacon-assets.ps1 -OutputRoot build/assets-review
./tools/testing/test-beacon-assets.ps1 -CandidateRoot build/assets-review
```

`-ResourceRoot` permite comprobar una copia de los recursos, por ejemplo para ensayar un fallo sin editar los publicados. Los generadores rechazan destinos fuera de `build/`. Los JSON se escriben con orden estable; su comparación ignora el orden de propiedades y los espacios. Los PNG se comparan byte a byte; otro codificador puede requerir revisar la equivalencia visual.

Las entradas anteriores siguen disponibles:

- `tools/generate-beam-assets.ps1` delega en el generador del haz y ahora escribe candidatos en `build/`.
- `tools/assets/legacy/build-beacon-assets.ps1` delega en el conversor vigente, con salida en `build/generated-assets/legacy-beacon/`.

Se mantienen para conservar las rutas documentadas. Para trabajo nuevo, usa la entrada conjunta `tools/assets/generate-beacon-assets.ps1`.

Tras cambiar una fuente, revisa las diferencias y copia únicamente los candidatos aprobados a sus rutas de `Common`. Ejecuta `gradlew build javadoc` y `tools/test-beacon-native.ps1`; reinicia el servidor completo para probarlos. El build empaqueta lo versionado y su guarda excluye las herramientas y los candidatos.

## Rutas y contratos de Hytale 0.7.4

Los registros del servidor recorren subcarpetas y obtienen el ID del nombre del archivo. Los recursos de servidor del Beacon están en:

```text
Server/Item/Items/Beacon/
Server/Models/Beacon/
Server/Entity/Effects/Beacon/
Server/Entity/ModelVFX/Beacon/
Server/Particles/Beacon/
Server/Languages/{en-US,es-ES}/beacon.lang
```

El item de hierro se publica desde `Items/Beacon/Iron_Block.json`; sus PNG conservan las rutas de `Beacon_Iron_Block` porque no definen el ID del objeto. Un modelo y un ModelVFX pueden llamarse `Beacon_Beam_Halo`: son registros distintos. Dentro de un mismo tipo, repetir el nombre en dos carpetas crea una colisión.

Las referencias de `Common` usan rutas, por eso se mantienen sus ubicaciones actuales. Los modelos del haz y sus texturas siguen las raíces documentadas en [Models](https://docs.hytale.com/assets/models/); los iconos usan las rutas admitidas por [Items](https://docs.hytale.com/assets/item/items/). Ambas referencias consultadas indican Release 0.7.4. La carga recursiva y la obtención del ID se contrastaron con `AssetStore.loadAssetsFromDirectory` y `decodeFilePathKey` del servidor local de esa versión.

El núcleo gira desde Java y el halo permanece fijo. La animación desplaza las UV; los modelos del haz usan 64 unidades por bloque y los del bloque, 32. El generador conserva anchuras, extremos abiertos y altura total de 4096 bloques. Las piezas del núcleo tienen alturas potencia de dos, desde 1/32 hasta 256 bloques; forman los tramos entre cristales dentro de la altura de mundo de 0.7.4. Conservan todos los destinos de animación y ocultan los nodos situados fuera de cada pieza.

En `start`, `BeaconBeamModels` prepara 126 variantes desplazadas de las piezas y las 319 alturas posibles de la extensión superior como recursos `Common` en memoria. Conserva sus bytes y los registra en el índice enviado al conectar; no genera archivos ni modelos al colocar cristales. El desplazamiento de geometría permite anclar todas las entidades en la sección activa del Beacon sin alterar la altura visual. La extensión superior mantiene su extremo en 4096 bloques desde el origen del haz. Conserva la plantilla, textura y animación originales; sin cristales se reutiliza el modelo completo empaquetado.

Los colores de cristales reutilizan esa misma textura: `BeaconCrystals` registra un [ModelVFX](https://docs.hytale.com/assets/entity/modelvfx/) `Beacon_Beam_Tint_RRGGBB` con `SwitchTo=PostColor`, aplicado al núcleo mediante un [EntityEffect](https://docs.hytale.com/assets/entity/effects/) del mismo ID. `PostColorOpacity=0.85` conserva parte del detalle y del flujo de la textura. Usa un reloj de un segundo en bucle, siguiendo los efectos persistentes nativos, con ambos extremos del progreso en 1 y sin borde de resaltado. La duración positiva mantiene el reloj aunque el color sea constante. `EntityTopTint` y `EntityBottomTint` no sirven para este haz blanco: el shader del cliente 0.7.4 mezcla esos tintes aclarando el color y el blanco permanece blanco. La paleta nativa y sus transiciones desde blanco se preparan en `start`; los RGB nuevos se publican una vez de forma asíncrona y permanecen hasta reiniciar; muchas mezclas distintas pueden aumentar ese registro. El halo conserva su único ModelVFX de transparencia `Beacon_Beam_Glow`, sin sustituirlo por el de color.

El ID identifica el RGB del tramo (promedio acumulado o paso de transición); `PostColor` usa ese tono normalizado con su canal dominante en 255. El bloom del cliente aplica una potencia a cada canal: copiar colores oscuros de textura lo atenúa mucho. La normalización conserva tono e intensidad máxima, además del `fullbright`. Sin cristales de color no se aplica ModelVFX de tinte y se conserva el aspecto original.

El JSON del Beacon declara `Supporting.Up` de tipo `Full`. Los diez cristales pequeños nativos se utilizan sin transformar sus assets ni añadir soporte superior; la colocación sigue las reglas vanilla. Las capas del haz usan el componente nativo `Intangible`, manteniendo geometría y replicación.

El asset nativo [Beam](https://docs.hytale.com/assets/entity/beams/) de 0.7.4 declara una textura. Su componente `AttachedBeam` y el paquete `BeamInstance` no exponen RGB ni giro del núcleo por instancia. No sustituyen directamente el modelo giratorio con `PostColor` conservando su aspecto; los colores y efectos actuales usan `ModelVFX` y `EntityEffect` nativos.

## Laboratorio experimental de partículas (rama `experiment/beacon-particles`)

El laboratorio `/beaconfx` no reemplaza todavía el haz principal del Beacon. La base se
publica en memoria desde `BeaconFxAssets`, copiando los emisores nativos de
`Drop_Legendary`, retirando sus colores fijados y preparando cuatro velocidades de
rotación. `BeaconFxProfiles` publica 29 relaciones alto/ancho y tres opacidades
(87 perfiles); cada perfil utiliza la textura vanilla `Glow.png` y un único emisor,
sin generar PNG nuevos. Se registran durante `start()`, nunca al ejecutar un
comando ni en un tick de mundo.

Para comparar visualmente:

```text
/beaconfx polish purple
/beaconfx custom 40 0.65 purple 0.94 0.85 0.5
/beaconfx customalign 40 0.65 purple 0.94 0.85 0.5 -5
```

Los parámetros son altura solicitada, anchura, color, brillo (0.5–1),
escala global de la base (0.5–1.5) y velocidad de giro solicitada (0.25–1).
`customalign` añade un offset Y. La altura y el brillo reales se aproximan al
perfil precalculado; el giro se aproxima a las velocidades registradas 0.35,
0.5, 0.75 o 1. La base solo dispone de escala global, no de tamaño X/Z independiente.
`/beaconfx tune` y sus JSON anteriores se conservan como referencia segura
hasta que se pruebe en el cliente la publicación dinámica. Si no se cargan perfiles,
el laboratorio lo informa y no afecta a las mecánicas originales. Compila con
`./gradlew build` y reinicia completamente Hytale para actualizar assets.

## Comprobación con cliente

La fixture nativa verifica carga, límites, animación registrada, rotación replicada, tintes y ciclo de las capas. El parser y el renderizado del cliente requieren una prueba real. Tras un cambio visual, comprueba bloque colocado y sostenido, iconos, pulso, giro del núcleo, halo fijo, flujo de textura y retirada/reparación de la base. Para cristales, comprueba colocación sobre el Beacon, rechazo del apilado sin otro apoyo, color individual, mezcla con apoyos laterales y retirada, además de abrir con F apuntando al Beacon desde arriba. Revisa también los gestos de inventario si cambias sus texturas o la página `.ui`.
