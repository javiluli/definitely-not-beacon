---
name: hytale-new-feature
description: Añadir o ampliar funcionalidades del mod Definitely Not Beacon de este repositorio, integrando reglas, Hytale, UI y assets. Usar para cambios funcionales nuevos; para corregir o refactorizar lo existente, usar hytale-maintenance.
---

# Añadir una funcionalidad a Definitely Not Beacon

## Delimitar

- Lee el contrato `AGENTS.md` y el estado de Git; conserva cambios ajenos.
- Concreta el comportamiento pedido y sus casos límite; pregunta por reglas desconocidas.
- Consulta `docs/ESPECIFICACION.md` solo para las reglas afectadas y `docs/ARQUITECTURA.md` para su ubicación.
- Usa Graphify para localizar dependencias y pruebas; contrasta los resultados con el código actual.
- Incluye en el alcance consumidores, registros, codecs, traducciones, recursos y fixture.

## Integrar

- Reutiliza el componente y los helpers que ya poseen la regla; no crees un paquete por cada función.
- Mantén balance en `BeaconPower` y reglas puras en `BeaconRules`; la UI usa `BeaconPageView` y `BeaconText`.
- Integra sistemas, interacciones, codecs o eventos en los registros apropiados de `DefinitelyNotBeaconPlugin.setup`.
- Publica transformaciones que requieran assets cargados desde `start`; conserva dependencias ECS e hilo del mundo.
- Protege IDs y selección persistida; acuerda cualquier migración o cambio de pago antes de aplicarlo.
- Coloca recursos en `src/main/resources` siguiendo `docs/RECURSOS.md`; usa `docs/INTERFAZ.md` si cambia el panel.
- Añade claves necesarias en ambos catálogos y conserva referencias de Common e IDs de servidor.
- Documenta contratos nuevos con Javadoc breve y decisiones no evidentes con comentarios; evita repetir el código.

## Verificar y entregar

- Establece compilación y tests verdes antes de editar; añade pruebas del comportamiento nuevo y sus límites.
- Usa el wrapper: `compileJava`, tests relevantes y `build`; genera `javadoc` si cambian contratos.
- Ejecuta la fixture nativa para pagos, persistencia, assets o ECS; verifica gestos y VFX con cliente.
- Consulta los comandos y límites en `docs/DESARROLLO.md`; usa la skill Java especializada solo si hace falta.
- Actualiza Graphify tras cambiar código y únicamente los documentos técnicos cuya información cambie.
- Respeta las autorizaciones documentales de AGENTS; no actualices el changelog ni hagas commits automáticamente.
- Entrega comportamiento, verificaciones, límites y pruebas manuales pendientes.
