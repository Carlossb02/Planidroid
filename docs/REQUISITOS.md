# Matriz de requisitos · Planidroid 0.3.0

Estado se refiere a código, no a validación de APK. «Parcial» significa que faltan comportamientos solicitados. No se presenta como proyecto terminado ni seguro para producción.

| Bloque del PDF / ampliación | Estado y alcance |
|---|---|
| 1–3 Visión, tareas, categorías | Local: título, descripción, estado, fecha límite/inicio, prioridad, etiquetas, tiempo, objetivo, recurrencia y proyecto. Faltan personalización completa de área/icono y todas las vistas combinadas. |
| 4 Fases y objetivos | Editar descripción, fecha, tiempo, checklist, reordenar; dependencia entre tareas bloquea hasta finalizar requisitos. Faltan división/fusión, conversión de fase a tarea y objetivos cuantitativos específicos. |
| 5 Captura | Texto, dictado, cámara, galería OCR, archivo SAF y contacto. Falta extracción PDF, foto OCR en resolución completa y refinamiento selector de teléfono; nombre o teléfono manual con marcador. |
| 6 Planificador | Kotlin determinista, varias tareas, orden fecha/prioridad, fases, días/franja, mínimo de un minuto, máximo, margen, días bloqueados, déficit, antes/después de replanificación futura, aceptación transaccional. Dependencias se excluyen hasta completarse; falta planificación automática del grafo completo y múltiples franjas por día. |
| 7 Calendario | Día, semana, mes y agenda; sesiones y eventos editables. Importa archivos iCalendar y vCalendar con vista previa. Faltan arrastre de sesiones, expansión de series recurrentes y filtros completos. |
| 8 Colaboración | Nuevos espacios compartidos, roles, UID, invitaciones con vencimiento, comentarios, asignación textual, elementos compartidos y compras con historial. Parcial: online únicamente, no migración canónica de una tarea privada, sin transferencia/borrado de grupo ni agenda de grupo, sin historial completo de actividad, sin FCM. |
| 9 Interfaz | Material 3 pastel claro/oscuro, logo original, navegación 5 secciones, progreso animado, agenda, tareas, editor, captura, asistente, hogar, compra y ajustes. Faltan widgets configurables y navegación tablet especializada. |
| 10 Ajustes | Cuenta Google, modos Calendar, conflictos de nube, copia JSON/ZIP, tema, IA, avisos, silencio, resumen, recuperación. Faltan gestión de dispositivos, edición de perfil/avatar, borrado de cuenta y preferencias avanzadas de accesibilidad, idioma y háptica. |
| 11 Estética | Sustituido Liquid Glass dinámico según preferencia del usuario; Material 3 minimalista, pastel, sin blur continuo. No probado visualmente en dispositivo. |
| 12–14 Arquitectura/Firebase | Room + ViewModel/StateFlow + WorkManager + DataStore + Firebase opcional + ML Kit. Reglas e índices, adaptador Gemini, pruebas de emulador escritas. Faltan FCM, App Check, adjuntos remotos, pruebas reales y evaluación de cuotas. |
| 15 IA | Local ligero, interfaz AiProvider, proxy HTTPS Gemini con lista UID, propuesta revisable. Faltan contexto de calendario exhaustivo, estimaciones especializadas, multimodal remoto y límites distribuidos. |
| 16 Notificaciones | Fechas, sesiones, antelación, silencio, resumen diario, permisos. Aproximadas. Faltan acciones rápidas en notificación, push y deduplicación multidevice. |
| 17 Privacidad | Privado por defecto, scope UID, no secretos, exportación, permisos mínimos, invitaciones explícitas. Faltan eliminación integral de cuenta, política de publicación, App Check y auditoría de retención. |
| 18–21 Calidad / entregables | Proyecto Gradle, fuentes, recursos, README, arquitectura, reglas, JUnit, smoke, UI test, ejemplo backup. Sintaxis y dominio comprobados, no build Android ni pruebas Firebase. |
| Hogar privado | Editor 2D con interior/puerta, arrastre, tipos y dimensiones, fotos, PNG originales, iconos de productos, stock/caducidad, búsqueda, OCR/etiquetado local. |
| Hogar compartido | Espacio común de elementos con cantidad y ubicación, roles. No editor visual colaborativo ni sincronización de fotografías. |
| Compra privada | Rutina/puntual, mínimos/objetivos, historial diario, deshacer y repetir compra. |
| Compra compartida | Lista con cantidades, rutina e historial recuperable. No automatización por stock de inventario compartido. |
| Logo | Se conserva el primer diseño de tarjetas pastel sobre blanco; se descarta el render posterior. |

## Bloqueos antes de entrega final
SDK/Gradle no disponibles; red de descarga bloqueada. Añadir Wrapper oficial en Windows, ejecutar `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, probar SDK y dispositivos; configurar proyecto de Google y emulador; corregir fallos de compilación/ejecución. No se puede asegurar que esta versión compile hasta realizar esos pasos. Las características de infraestructura se activan solo después de configuración explícita y validación.
