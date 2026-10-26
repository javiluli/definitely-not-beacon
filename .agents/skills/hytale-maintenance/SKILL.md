---
name: hytale-maintenance
description: Corregir fallos, optimizar hotspots medidos o refactorizar de forma acotada el código existente del mod Definitely Not Beacon. Usar para mantenimiento y regresiones; para una funcionalidad nueva, usar hytale-new-feature.
---

# Mantener Definitely Not Beacon

## Investigar

- Lee `AGENTS.md`, revisa Git y conserva cambios ajenos.
- Delimita el fallo o la mejora pedida; no conviertas el trabajo en otra auditoría o refactorización general.
- Usa Graphify para localizar el responsable y sus llamadas, dependencias y pruebas; verifica el código local.
- Revisa consumidores, fixture, registros, codecs, recursos e idiomas afectados antes de editar.
- Consulta solo las secciones necesarias de `docs/ESPECIFICACION.md`, `docs/ARQUITECTURA.md` y `docs/DESARROLLO.md`.
- Reproduce el fallo y establece una base de compilación y tests; comunica fallos previos graves antes de continuar.

## Cambiar lo mínimo

- Corrige la causa compartida; reutiliza helpers y operaciones nativas, conservando validaciones y compensaciones.
- Para refactorizar, preserva comportamiento y cambia un límite cada vez; separa correcciones funcionales.
- Protege pagos, metadatos, devoluciones, persistencia, IDs y selecciones históricas, incluidos sus límites conocidos.
- Conserva ciclo de vida, orden de assets, dependencias ECS, hilo propietario, buffers y referencias válidas.
- Optimiza solo un hotspot medido; compara antes/después con el mismo entorno y escenario.
- Usa la skill Java pertinente para tests, extracción, diseño o rendimiento; no repitas su procedimiento aquí.
- Actualiza Javadoc o comentarios cuando cambie un contrato o sea necesaria una explicación no evidente.

## Comprobar regresiones

- Añade una prueba del fallo o caracteriza primero el comportamiento crítico desprotegido.
- Ejecuta el wrapper con tests relevantes y `build`; usa `compileJava` entre pasos y `javadoc` para contratos.
- Repite la fixture para inventarios, persistencia, registros, assets o ECS; consulta sus límites en `docs/DESARROLLO.md`.
- Comprueba con cliente UI, movimiento y VFX afectados; si no puedes, declara qué recorrido falta.
- Revisa el diff, actualiza Graphify tras cambiar código y la documentación técnica que haya quedado desfasada.
- Respeta las autorizaciones documentales de AGENTS; no actualices el changelog ni hagas commits automáticamente.
- Entrega causa, cambio mínimo, pruebas y riesgos pendientes; no ocultes ni debilites tests fallidos.
