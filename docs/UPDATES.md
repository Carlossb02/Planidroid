# Actualizaciones 0.1.0.0
Al arrancar se consulta `https://api.github.com/repos/Carlossb02/Planidroid/releases/latest` en segundo plano. Tags estables `vX.Y.Z.W` o `X.Y.Z.W` (también se admiten los antiguos de tres cifras) y un asset `Planidroid_vX.Y.Z.W.apk` (o el antiguo `Planidroid.apk`) con digest SHA-256 de GitHub. No se consultan commits ni artefactos de CI. Se ofrece descarga e instalación mediante el instalador del sistema; no se instala en silencio. Si no hay red o release, el inicio continúa. No se requiere token GitHub.

## Publicación
1. Compilar y probar APK con Android Studio. Generar APK firmado con una clave estable propia, no con la clave debug de CI. Conservar copia segura de la clave fuera de GitHub.
2. Incrementar `versionCode` y `versionName` en app/build.gradle.kts. Crear una release pública no prerelease, tag coincidente con versionName, p. ej. v0.1.0.0.
3. Adjuntar el APK firmado como `Planidroid_v0.1.0.0.apk` (sustituir por la versión real). Comprobar que la API incluye el digest sha256 del asset.
4. Probar actualización desde una instalación firmada con la misma clave. Android requerirá autorización de instalación desde esta app. No desinstalar para cambiar de clave sin exportar antes los datos.

El APK se verifica por tamaño, digest, applicationId, versionCode, versionName y certificados actuales. La rotación de claves no está soportada. El workflow CI produce APK debug para pruebas, no una release instalable sobre la versión firmada de producción. No publicar APK debug como actualización estable. No se ha podido probar el flujo real en Android en este entorno.

## Primera publicación y transición desde compilaciones de desarrollo
La primera versión oficial es 0.1.0.0 (versionCode 9). Las compilaciones de desarrollo 0.5.x tienen un versionName numéricamente superior; su actualizador no detectará este cambio de numeración. Si ya instalaste una, instala manualmente 0.1.0.0 con la misma firma y conserva tus datos. Android permite la actualización porque el versionCode aumenta. A partir de 0.1.0.0, incrementar ambas versiones en cada publicación; ejemplos: 0.1.0.1, 0.1.1.0, 0.2.0.0. No publicar las compilaciones antiguas como releases estables.
