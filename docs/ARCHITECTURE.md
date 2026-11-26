# Arquitectura y ADR · Planidroid 0.3

## ADR 001 — UI y estado
Compose nativo y Material 3, ViewModel `PlanidroidModel`, StateFlow y Room. Tokens semánticos pastel, temas claro/oscuro, logo original. No blur dinámico ni sensores. La navegación usa cinco destinos principales: Inicio, Calendario, Captura, Tareas y Espacios; desde ellos se accede a ajustes, asistente, concentración, compra, hogar y colaboración. El editor de hogar tiene cuadrícula independiente de interior y puerta. Las operaciones de negocio no dependen de Compose. Las animaciones y accesibilidad requieren validación en dispositivo.

## ADR 002 — Datos privados offline-first
PK Room `(scope,id)`, entidad Record con kind, payload, deleted, dirty, baseVersion y revision. Scope local o Firebase UID. Las entidades de dominio embebidas se serializan; los IDs se mantienen estables. Task contiene checks, fases, dependencias, adjuntos, recurrencia; Session referencia Task y Phase; Group agrupa Task; Furniture tiene Zone; Item referencia Furniture y nombre de compartimento; Shopping referencia Item opcionalmente; Purchase captura cantidad y stock antes/después. Las mutaciones relacionadas de compra y planificación son transaccionales. La cola privada Firestore usa tombstones y versión por registro, sin sobrescribir ciegamente. La recuperación de conflictos se mantiene separada por cuenta. No hay sincronización atómica global de todos los registros. 

## ADR 003 — Colaboración separada
`sharedSpaces` con ownerUid, kind, title, body (SharedContent JSON), version, updatedAt. `memberships` con spaceId, email, role, status, invitedBy, expiresAt, acceptedUid, updatedAt. `comments` subordinados al espacio. Compartir un espacio no lee el árbol privado. Consulta online por owner o correo autenticado, y lectura explícita de recursos aceptados. Creación nueva de espacio; no transforma automáticamente una tarea privada existente. Campos internos del JSON no se validan individualmente en reglas; no usar datos reales hasta pruebas de seguridad.

| Operación | Dueño | Admin aceptado | Editor aceptado | Lector aceptado | Pendiente/ajeno |
|---|---|---|---|---|---|
| Leer espacio | Sí | Sí | Sí | Sí | No |
| Editar cuerpo/título | Sí | Sí | Sí | No | No |
| Comentar | Sí | Sí | Sí | No | No |
| Invitar/revocar editor o lector | Sí | Sí | No | No | No |
| Invitar/revocar admin | Sí | No | No | No | No |
| Cambiar owner / borrar espacio | No (pendiente) | No | No | No | No |

## ADR 004 — Planificador determinista
Planner trabaja sobre intervalos en zona horaria local y resta disponibilidad ocupada. AgendaPlanner ordena por fecha límite y prioridad, desglosa fases, respeta fecha inicial, margen, días bloqueados y máximo de sesión. Las dependencias incompletas impiden planificar (no se programa todavía todo el grafo). La propuesta incluye déficit; replanificación sustituye solo sesiones futuras no realizadas que fueron seleccionadas. Se valida de nuevo en transacción antes de guardar. Los eventos creados e importados bloquean su horario en la agenda.

## ADR 005 — IA y OAuth opcionales
LocalAi es parser/reglas, no LLM. GeminiGateway usa proxy autenticado, consentimiento explícito, campos acotados y propuesta revisable. CalendarSync usa OAuth independiente, token efímero, caché de externos, identificador estable y ETag. No se suben fotos, notas privadas ni contactos a Calendar. Notificaciones locales WorkManager y resumen aproximado, no FCM. 

## ADR 006 — Backup
JSON schemaVersion 1 conserva arrays tipados por kind en `entries`; el ZIP añade `backup.json` y archivos bajo `files/` con nombres controlados. Validación, preview, combinar, remapear IDs o reemplazo local con copia previa; los URI externos de JSON se descartan. No hay secretos ni tokens. No se exportan colaboraciones ni vínculos OAuth. 

## Calidad y plan de salida
1. Compilar app y resolver dependencias con Android SDK 35/JDK 17. 2. Probar UI, Room y backup en emulador y terminal. 3. Ejecutar Emulator Suite y revisar roles. 4. Configurar cuentas reales de prueba y OAuth Calendar/Gemini. 5. Completar características de `REQUISITOS.md`: transferir/borrar espacios, borrado de cuenta, FCM, editor colaborativo completo, permisos, accesibilidad y pruebas. 6. Auditoría de seguridad, rendimiento y Play antes de distribuir. Ver `verification.txt` para alcance real de las comprobaciones.
