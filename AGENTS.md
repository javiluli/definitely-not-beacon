# Contrato de desarrollo de Definitely Not Beacon

Definitely Not Beacon añade a Hytale una pirámide de hierro de cuatro niveles, seis mejoras,
selección pagada y haz visual. Es un módulo Java 25 con Gradle Kotlin DSL.

## Control documental

- **AGENTS.md es estable:** no lo edites en tareas ordinarias. Solo propón cambios
  si cambian las normas generales o la arquitectura; aplicarlos requiere autorización expresa del propietario.
- **README.md es para jugadores:** tras su aprobación, solo podrá cambiar por
  novedades visibles para ellos y con autorización expresa. No añadas detalles internos ni secretos sin permiso.
- Actualiza `docs/` solo cuando cambie lo que describe y las skills cuando cambie
  su procedimiento. `CHANGELOG.md` se mantiene manualmente; no lo actualices por cada commit.
- Consulta únicamente los documentos necesarios. No crees informes de auditoría,
  documentos temporales ni reorganizaciones estéticas por defecto. No borres historial sin mostrar y acordar los candidatos.

## Arquitectura y ubicación

- `src/main/java/dev/hytalemodding/DefinitelyNotBeaconPlugin.java`: registros en `setup()` y
  publicación de efectos y variantes de Prisa en `start()`, tras cargar assets.
- `beacon/`: `BeaconPower` concentra el balance; `BeaconRules`, las reglas puras;
  `BeaconStructures`, las bases; `BeaconSelection`, la selección persistida.
- `BeaconOpenInteraction`, `BeaconPage`, `BeaconPageView`, `BeaconText` y
  `BeaconPaymentWindow`: apertura, eventos, presentación, traducciones e inventarios.
- Los sistemas ECS y `BeaconWorldState` coordinan efectos y estado temporal por mundo;
  `BeaconBeam` mantiene el haz. `BeaconEffects` y `BeaconHasteAssets` publican assets en memoria.
- Recursos empaquetados en `src/main/resources/`; pruebas en `src/test/`,
  fixture en `src/integrationTest/`, mediciones en `src/profiling/` y herramientas en `tools/`.
  `art/` y `public/` contienen fuentes y referencias. No empaquetes herramientas ni archivos de `build/`.

El mapa completo está en [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md);
las reglas de juego en [docs/ESPECIFICACION.md](docs/ESPECIFICACION.md).

## Cambios y Java

- Antes de editar, revisa Git, la responsabilidad y los consumidores afectados:
  llamadas, tests, fixture, registros, codecs y recursos. Conserva cambios ajenos.
- Para mecánicas existentes, corrige el punto que ya posee la regla. Para funciones
  nuevas, intégralas en el componente adecuado; un paquete propio requiere una responsabilidad distinta.
- Reutiliza helpers y API del proyecto y del motor antes de añadir código.
  Evita abstracciones, wrappers y dependencias especulativas. Separa cambios funcionales y estructurales.
- Usa cuatro espacios, llaves en la declaración, `PascalCase`, `camelCase`,
  `UPPER_SNAKE_CASE` y paquetes en minúsculas. Conserva nulabilidad y la menor visibilidad útil.
- Revisa la cohesión de métodos que superen unas 50 líneas al trabajar sobre ellos.
  Extrae solo responsabilidades claras; codecs declarativos o secuencias indivisibles pueden conservarse,
  justificándolo en la revisión. No hagas extracciones mecánicas ni revisiones globales por esta cifra.
- Javadoc breve, natural y en español para clases, métodos y atributos relevantes:
  propósito, unidades y restricciones. Usa `@param`, `@return` y `@throws` cuando aclaren el contrato.
  Dentro de métodos, comenta cálculos, reglas o particularidades de Hytale que el código no explique.

## Contratos de Hytale

- Objetivo: Hytale **0.7.4**, patchline `release`, manifiesto `=0.7.4`. No amplíes
  soporte sin las comprobaciones de [docs/DESARROLLO.md](docs/DESARROLLO.md#actualizar-hytale).
- Empieza por [Java API](https://docs.hytale.com/api/) y [assets](https://docs.hytale.com/assets/),
  comprobando la versión de cada página. Si falta un contrato, inspecciona el servidor,
  fuentes y `Assets.zip` correspondientes; no inventes garantías ni edites fuentes generadas.
- Conserva ciclo de vida, orden de carga, dependencias ECS, hilo propietario del mundo,
  `CommandBuffer` y validez de referencias. El orden del daño es efectos salientes → Fuerza → armadura → Resistencia.
- Preserva IDs, codec `BeaconSelection` (`Primary`, `Secondary`), inventarios,
  pago y compensaciones salvo cambio funcional o migración autorizados. Actualizar requiere reinicio completo del servidor.

## Investigación, skills y verificación

<!-- graphify-rules-start (managed by `graphify init`) -->
Usa Graphify primero para localizar símbolos, llamadas, dependencias o pruebas cuando
aporte valor. Contrasta su snapshot con el árbol local; las aristas no prueban contratos del motor.
Usa el MCP conectado o el grafo local (`query`, `path`, `explain`); si falla o no modela
la información, consulta los archivos. Detalles y alternativa sin PATH en [docs/DESARROLLO.md](docs/DESARROLLO.md#graphify).
Después de modificar código, ejecuta `graphify update .`; no reconstruyas el grafo por cambios exclusivamente documentales.
<!-- graphify-rules-end -->

Usa `.agents/skills/hytale-new-feature` para ampliar y `hytale-maintenance` para
corregir, optimizar o refactorizar. Carga solo las skills Java pertinentes; sus ejemplos
Maven se sustituyen por el wrapper Gradle y sus métricas no imponen cuotas.

Establece una base verde y verifica lo afectado: `./gradlew compileJava`, `test` y
`build`; `javadoc` si cambian contratos. En PowerShell usa `.\gradlew.bat`.
Pagos, persistencia, assets y ECS requieren la fixture pertinente; UI y gestos, cliente.
No debilites tests fallidos. Optimiza solo con mediciones comparables. Comandos y límites en `docs/DESARROLLO.md`.

## Git

Commits atómicos con asunto breve e imperativo y prefijo `feat:`, `fix:`, `perf:`,
`refactor:`, `docs:`, `art:` o `test:`. No hagas commits sin autorización.
No incluyas credenciales, tokens ni runtime generado; registra verificaciones y límites en la entrega o PR.
