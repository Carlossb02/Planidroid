# Planidroid · Android 0.1.0.0

Aplicación nativa Kotlin, Compose y Material 3. Prioridad: tareas, proyectos, calendario y planificación personal. Hogar y compra son módulos complementarios. Evolución de v0.2 con nuevas funciones; **no es todavía una versión certificada de todos los requisitos**. Consulta `docs/REQUISITOS.md` para el estado de cada bloque.

## Primera versión oficial: 0.1.0.0

La numeración oficial comienza en 0.1.0.0; las versiones 0.5.x anteriores eran compilaciones de desarrollo. Versiones de cuatro cifras y nombre automático del APK `Planidroid_vX.Y.Z.W.apk`. Actualizador compatible con los tags anteriores de tres cifras.

## Captura e interfaz simplificadas

Captura de tareas exclusivamente mediante texto: interpretación local o Gemini con consentimiento, propuesta editable y guardado explícito. Los controles de cámara, OCR y archivos no se muestran en tareas. Se conservan los adjuntos existentes y las fotografías de inventario. Inicio, filtros y editor de tareas simplificados; opciones de planificación avanzada plegadas.

## Compilar en Android Studio (Windows)
1. Descomprime el ZIP. Abre PowerShell en `Planidroid` (contiene `settings.gradle.kts`). Ejecuta `powershell -NoProfile -ExecutionPolicy Bypass -File .\prepare-wrapper.ps1`. El script descarga el JAR oficial de Gradle 8.9; no se pudo descargar en el entorno de entrega (DNS sin acceso a repositorios). Si tienes Gradle instalado, alternativa: `gradle wrapper --gradle-version 8.9`.
2. Android Studio → Open → carpeta Planidroid. Instala SDK Android 35 y selecciona JDK 17 para Gradle. Sync Project with Gradle Files.
3. Selecciona `app` y un móvil/emulador Android 8 (API 26) o superior. Run. O ejecuta ` .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest `; APK esperado: `app/build/outputs/apk/debug/Planidroid_v0.1.0.0.apk`.

### Ubicación del SDK (configuración local)
Al sincronizar Gradle se genera `local.properties` si no existe y se detecta un SDK instalado en `ANDROID_HOME`, `ANDROID_SDK_ROOT` o en las rutas habituales de Windows, macOS o Linux. No se sobrescribe una configuración existente. Si la detección falla, instala Android SDK 35 y copia `local.properties.example` a `local.properties`, descomentando y ajustando `sdk.dir` a tu ruta real.

`local.properties` permanece ignorado por Git y bloqueado por los controles de secretos. Solo se versiona la plantilla sin datos privados; no guardes claves en ella.

No necesitas credenciales para usar el modo local. AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, Room 2.7.0, Compose BOM 2024.10.01. Versiones fijadas, no se afirma que sean las más recientes. **No se ha compilado ni instalado el APK**: no hay Android SDK/Gradle en el entorno y las descargas fallan. La revisión sintáctica y las comprobaciones JVM se detallan en `docs/verification.txt`. Las pruebas instrumentadas, compilación completa y Emulator Suite no se han ejecutado. No utilizar datos reales de varias cuentas hasta probar las reglas de seguridad.

## Recorrido por la app
- Inicio: agenda, progreso, tareas urgentes, accesos a espacios, hogar, compra y concentración. Tema Material 3 pastel, animaciones y tarjetas ligeras; logo original de tres tarjetas.
- Tareas: título rápido, descripción, categoría, prioridad, fechas, recurrencia al completar, tiempo real, dependencias, fases con objetivos y fechas, notas, contactos y adjuntos. Dependencias incompletas bloquean planificación; los ciclos se rechazan. Repeticiones: el próximo vencimiento se calcula a partir de la fecha anterior, no a partir del día de finalización.
- Agenda: vistas día/semana/mes/lista, planificación individual o de varias tareas, fechas de inicio, días excluidos, margen, propuesta de sustitución de sesiones futuras y déficit. Las sesiones realizadas no se cambian por replanificación. Edición de sesión por formulario, no arrastre en calendario.
- Concentración: temporizador con pausa, registro explícito de minutos al terminar. No es un servicio persistente en segundo plano.
- Hogar: muebles ilustrados y fotos locales, editor de cuadrícula para baldas/cajones de interior y puerta, arrastre y ajuste de dimensiones, productos con icono, cantidad, unidad y caducidad. Etiquetado de fotos/OCR ML Kit en local con revisión. No garantiza identificación específica de alimentos.
- Compra privada: lista permanente, rutina/puntual, stock mínimo y objetivo, historial diario, reutilización y deshacer. El stock se revierte solo si no cambió desde la compra.
- Espacios: proyectos privados. Espacios compartidos separados por cuenta, con invitaciones, roles, listas de tareas/objetivos, asignación por nombre, inventario de texto por compartimento, compras con historial y comentarios. La versión compartida no tiene aún el mismo editor visual ni automatización de stock que la privada. Requiere conexión y reglas desplegadas. No convierte tareas privadas existentes en una tarea colaborativa canónica; permite crear una nueva compartida.
- Captura de tareas: exclusivamente texto; OCR y fotografía se mantienen en inventario. Asistente local sin LLM; Gemini opcional con revisión y consentimiento, vía proxy HTTPS propio.
- Ajustes: Google, conflictos de nube, tema, IA, notificaciones (avisos de sesiones, antelación, silencio, resumen diario), copias JSON/ZIP y recuperación. Avisos WorkManager aproximados, no alarmas exactas.

## Google, Firebase y Gemini (opcionales)
1. Registra `es.planidroid.app` en Firebase. Habilita Google Sign-In; añade SHA-1 y SHA-256 (`:app:signingReport`); copia `google-services.json` a `app/`. El plugin se aplica solo si existe ese archivo.
2. Prueba Firestore en Emulator Suite antes de desplegar. Sigue `docs/SECURITY.md`; no abras reglas globales. Configura el proyecto y despliega `firebase deploy --only firestore` tras validar. Firebase privado: cada UID es independiente del modo local; no se copia información al iniciar sesión. Resolución de conflictos con selección explícita. Los contactos, adjuntos y fotos privados no se suben.
3. Calendario: abre un archivo .ics, .ical o .vcs con Planidroid, o elígelo en el calendario. Revisa, modifica y selecciona los eventos antes de añadirlos. Puedes elegir si cada evento bloquea su horario.
4. Gemini: en Ajustes conecta y comprueba una clave personal de Google AI Studio. Planidroid te permite revisar las propuestas antes de guardar tareas o productos. Consulta [Integraciones](docs/INTEGRATIONS.md).

## Copias
JSON versionado con metadatos y datos privados; ZIP opcional incluye los archivos accesibles (20 MB por archivo, 150 MB total, 500 archivos). Los backups no contienen tokens ni secretos. Vista previa, combinación sin sobreescribir, importación con IDs nuevos y reemplazo únicamente del espacio local con copia previa. Exportación de la última recuperación. Las copias no incluyen espacios colaborativos ni preferencias externas, y el JSON solo no recupera binarios. No borrar datos de la nube mediante restauración destructiva.

## Árbol
`app/src/main/kotlin/es/planidroid/app/{domain,data,sync,ui}` · `res/drawable-nodpi` · `app/src/test` · `app/src/androidTest` · `backend/` · `security-tests/` · `docs/` · reglas Firestore/Storage · Gradle.

## Actualizaciones 0.4.0
Consulta [docs/UPDATES.md](docs/UPDATES.md). Comprobación automática al inicio de GitHub Releases, descarga verificada e instalación con confirmación Android. CI compila y ejecuta tests en GitHub Actions cuando se importe el proyecto.

## Identidad interna (0.4.1)
- Namespace, applicationId y paquetes Kotlin: `es.planidroid.app`. Clases y rutas de pruebas renombradas.
- Se mantiene el ID de aplicación de 0.4.0, sin necesidad de desinstalar si la firma es la misma. El nombre SQLite anterior se conserva únicamente cuando ya existe; las instalaciones nuevas usan `planidroid.db`. Los trabajos y accesos directos antiguos tienen adaptadores de compatibilidad. No se cambian los IDs ni el esquema JSON.
- Ajustes y nuevas copias leen la versión del BuildConfig. Compilación y migración sobre un dispositivo real pendientes de verificación.

## Backend Kotlin (0.4.2)
El servidor Gemini se ha migrado de JavaScript a Kotlin/JVM 17, como proyecto Gradle independiente (`backend/`). Ejecuta `gradle -p backend test` y `gradle -p backend run` (o `./gradlew -p backend ...` desde la raíz después de preparar el wrapper). No es necesario iniciar el backend para usar Android offline. Las pruebas de reglas Firebase conservan JavaScript porque usan el SDK oficial de Emulator Suite; no forman parte del APK.

## Seguridad de credenciales

La primera versión pública es `0.1.0.0`. No subas `app/google-services.json`, `.env`, secretos, claves de firma ni copias de datos. El servidor Gemini usa variables de entorno y nunca una clave incluida en el APK. Activa las comprobaciones locales en cada clon con `bash tools/install-git-hooks.sh` y consulta [docs/SECRETS.md](docs/SECRETS.md). La protección es preventiva, no infalible.
