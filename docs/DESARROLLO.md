# Desarrollo de Definitely Not Beacon

Guía de compilación, pruebas y mantenimiento. Las normas permanentes y las
autorizaciones documentales están en [AGENTS.md](../AGENTS.md).

| Documento | Consultar para |
| --- | --- |
| [ESPECIFICACION.md](ESPECIFICACION.md) | Reglas de juego, recetas, balance y persistencia |
| [ARQUITECTURA.md](ARQUITECTURA.md) | Componentes Java, dependencias y contratos del motor |
| [RECURSOS.md](RECURSOS.md) | Fuentes visuales, generación y VFX |
| [INTERFAZ.md](INTERFAZ.md) | Página nativa, eventos, inventario y traducciones |

## Entorno

- Java 25 y el wrapper Gradle 9.5.1 del repositorio.
- `com.azuredoom.hytale-tools` 1.0.51.
- Servidor y `Assets.zip` de Hytale 0.7.4, patchline `release`.
- Windows y PowerShell 7.4 o posterior para scripts; el renderizador usa `System.Drawing`.

La compatibilidad declarada es `=0.7.4`. `latest` es una ubicación de instalación,
no una garantía de versión: el lanzador nativo comprueba qué servidor encuentra.

## Compilar y ejecutar

Desde la raíz del repositorio, en PowerShell:

```powershell
.\gradlew.bat build javadoc
.\gradlew.bat setupHytaleDev
.\gradlew.bat runServer
```

En Unix se usa `bash ./gradlew`, como en CI; no requiere que el wrapper tenga
permiso de ejecución. El JAR público es
`build/libs/DefinitelyNotBeacon-ht0.7.4-0.1.0-beta.jar`.

| Propiedad de `gradle.properties` | Uso |
| --- | --- |
| `mod_name` | Nombre público y `Name` del manifiesto: Definitely Not Beacon |
| `mod_id` | Prefijo del JAR, sin espacios: DefinitelyNotBeacon |
| `release_label` | Etiqueta pública BETA-0.1 |
| `version` | Versión SemVer del mod: 0.1.0-beta |
| `hytale_version` | Versión del juego, dependencia del servidor y segmento `ht0.7.4` del JAR |

El segmento del juego no altera `Version` del manifiesto. Las publicaciones en
memoria obtienen su pack de los assets propios cargados mediante `getAssetPack`.

Tras cambiar metadatos, ejecuta `updatePluginManifest`. Para verificar sin
regenerar el manifiesto fuente:

```powershell
.\gradlew.bat build javadoc compileIntegrationTestJava compileProfilingJava -x createManifestIfMissing -x updatePluginManifest
```

Usa `--offline` con dependencias ya resueltas y `--gradle-user-home <ruta>` para
otra caché. `hytaleJvmDoctor` ayuda con la JVM. Actualizar el mod requiere parar
el servidor y los clientes, retirar el JAR anterior, copiar el nuevo y arrancar.
No se admite recarga del plugin en caliente. En el mundo se activa **Definitely Not Beacon**.

## Verificaciones automáticas

| Comando o tarea | Alcance |
| --- | --- |
| `compileJava` | Compila producción |
| `test` | Reglas puras y catálogos de traducción con Jupiter |
| `build` | Tests, empaquetado y `verifyProductionJar`; excluye herramientas, fixtures y profiling |
| `javadoc` | Referencia UTF-8 en `build/docs/javadoc/index.html` |
| `compileIntegrationTestJava` / `compileProfilingJava` | Compatibilidad de las fuentes de pruebas con la API; no ejecuta el servidor |
| `tools/testing/test-beacon-assets.ps1` | Reproduce los recursos y valida rutas, geometría y animación |
| `tools/testing/test-beacon-pipeline.ps1` | Determinismo y rechazo de errores sobre copias en `build/` |

La CI usa Java 25 en Ubuntu para build, Javadoc y compilación de las fuentes de
fixture y profiling. El servidor nativo, los generadores Windows y el cliente se
verifican por separado. No hay una tarea Gradle `integrationTest` que arranque Hytale.

## Fixture nativa

```powershell
.\tools\test-beacon-native.ps1 -Offline
```

Admite `-GamePath` para la instalación, `-AssetsPath` para otro `Assets.zip` y
`-GradleUserHome` para la caché. Servidor y assets deben corresponder a 0.7.4.
El script valida el JAR con el parser nativo y crea una copia de pruebas y un
mundo desechable en `build/beacon-native-test/<fecha-id>/`; nunca instala la fixture
en los mods del usuario. El resultado válido es `PASS` en `smoke-result.txt` y salida cero.

Se comprueban registros y orden ECS; recetas y botín nativo; inventarios, pagos,
devoluciones y codecs; persistencia al recrear mundo; niveles y solapamientos;
combate, salud y sprint; interacciones de Prisa; publicación, geometría, color,
replicación, estabilidad y limpieza del haz. Los colores se prueban también con
cristales altos en secciones inactivas y publicaciones asíncronas pendientes.

`BeaconSmokePlugin` coordina la fixture. `BeaconPaymentChecks`, `BeaconHasteAssetsChecks`,
`BeaconTextChecks` y `BeaconStaminaChecks` reúnen los escenarios específicos. Los
assets sintéticos existen solo en pruebas. Los errores de publicación provocados
deliberadamente se evalúan por sus aserciones y por el resultado final.

Los actores no tienen cliente: la fixture no certifica gestos, renderizado ni el
pipeline completo de un jugador equipado. Tampoco fuerza fallos de guardado o de
devolución; esas compensaciones excepcionales necesitan pruebas específicas.

## Profiling

```powershell
.\tools\test-beacon-native.ps1 -Offline -Profile
java tools/profiling/ReadBeaconJfr.java 'build/beacon-profile/<fecha-id>/workload.jfr'
```

`BeaconProfilePlugin` y la sonda residen en `src/profiling`, fuera del JAR público.
Se generan `startup.jfr`, `workload.jfr` y `profile-results.csv`. Los escenarios
combinan 0/1/8 Beacons, solapamiento o separación y 8/16 actores sin red. Las
ventanas controladas usan 512 muestras; preparación y calentamiento quedan fuera.
La región cargada se dimensiona con el alcance para que el escenario separado no
comparta radios; el CSV registra su número de secciones.
Compara el mismo entorno, escenario y fase. Estas medidas no son FPS del cliente.

## Pruebas con cliente

Usa esta lista de regresión según lo que cambie; registra versión, mods, pasos y resultado.

| Caso | Comprobar |
| --- | --- |
| Obtención | Ambas recetas en sus mesas, materiales exactos y salida unitaria |
| Base y alcance | Niveles I–IV, bordes del radio, degradación, reparación y residual |
| Pago | Arrastrar pilas y cantidades parciales, cancelar el gesto, confirmar y repetir; sin pérdidas ni duplicaciones |
| Cierre | Abrir/cerrar con Usar y Escape, desconectar con depósito e inventario lleno |
| Identidad | Retirar o sustituir el Beacon con la página abierta; impedir compras inválidas |
| Poderes | Movimiento y sprint, Prisa con picos y armas, resistencia con equipo, salud máxima, curación, salto y caída |
| Presentación | Inglés/español, tooltips, icono vanilla de curación, bloque en mano y colocado |
| Haz | Cristales encima y laterales, varias alturas y mezclas, retirada, brillo, transición, giro continuo y halo fijo |
| Ciclo de vida | Reinicio, muerte, reconexión, cambio de mundo, varios jugadores y otros mods |

## Skills locales

- [hytale-new-feature](../.agents/skills/hytale-new-feature/SKILL.md): añadir o ampliar comportamiento.
- [hytale-maintenance](../.agents/skills/hytale-maintenance/SKILL.md): corregir, optimizar con medidas o refactorizar un alcance concreto.

Se descubren desde `.agents/skills` por su nombre y descripción; también pueden
invocarse con `$hytale-new-feature` o `$hytale-maintenance`. Consulta solo la guía
Java pertinente y usa Gradle aunque sus ejemplos utilicen Maven. `skills-lock.json`
registra las skills importadas; las dos propias se mantienen en el repositorio.

## Graphify

El MCP se configura en `.codex/config.toml`. La configuración y la skill se
versionan; el grafo y sus cachés en `graphify-out/` quedan solo en local. Tras
clonar, instala Graphify siguiendo la skill y ejecuta `graphify update .` para
generar el índice de código. También puedes usar `python -m graphify update .`
con el intérprete donde esté instalado.

Contrasta siempre el snapshot con los archivos actuales; una arista no prueba un
contrato del motor. Si ya existe `.graphify_python` y el ejecutable no está en PATH:

```powershell
$graphifyPython = (Get-Content -LiteralPath 'graphify-out/.graphify_python' -Raw).Trim()
& $graphifyPython -m graphify query 'BeaconPage BeaconPaymentWindow' --budget 1800
& $graphifyPython -m graphify path 'BeaconPage' 'BeaconSelection'
& $graphifyPython -m graphify explain 'BeaconStatusSystem'
& $graphifyPython -m graphify update .
```

Actualiza tras cambios de código. Para documentos renombrados o eliminados,
mantén las fuentes del grafo sincronizadas; no reconstruyas todo por una edición
de texto. No ejecutes `graphify init` en mantenimiento: puede reescribir AGENTS.
El hook actual tiene matcher `Bash`; en PowerShell la actualización es explícita.
La skill `.codex/skills/graphify/SKILL.md` describe las operaciones y sus límites.

## Commits y notas de versión

Una intención por commit, con asunto breve e imperativo y los prefijos de
[AGENTS.md](../AGENTS.md#git). Las notas de versión se mantienen manualmente en
[CHANGELOG.md](../CHANGELOG.md): agrupa cambios visibles en
Gameplay, Additions, Bug Fixes, Optimizations o Art & VFX; usa API Changes cuando
haya un contrato externo o migración. La limpieza interna queda en Git, sin una
entrada en las notas por commit. No se crean commits ni etiquetas sin autorización.

## Actualizar Hytale

Comprueba la versión de la documentación oficial, el servidor, sus fuentes y
`Assets.zip` antes de cambiar dependencias o ampliar `=0.7.4`.

En 0.7.4, `DamageSystems.ArmorDamageReduction` y
`ScaleOutgoingDamageFromEntityEffects` están obsoletos, pero siguen registrados.
La fixture exige **efectos salientes → Fuerza → armadura → Resistencia**.
No los retires por la anotación de obsolescencia ni fijes sus índices numéricos:
verifica un reemplazo equivalente y su orden real al migrar de versión.

Repite build, Javadoc, fixture y pruebas de cliente. Revisa codecs, inventarios,
modificadores de stats, dependencias ECS, hilos, assets y cooldowns. Actualiza el
manifiesto y el nombre del JAR de forma coherente con la versión comprobada.
