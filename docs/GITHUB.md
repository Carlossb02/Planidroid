# GitHub y CI

Repositorio: https://github.com/Carlossb02/Planidroid. La rama de trabajo es `feat/planidroid-simple-ui`. El flujo Android CI compila, ejecuta pruebas y genera un APK de debug cuando los permisos de Actions lo permiten. No publicar APK de debug como actualización estable. Mantener la misma firma de producción y configurar las credenciales fuera del repositorio.

Antes de añadir archivos al índice, ejecuta `bash tools/install-git-hooks.sh`. El workflow revisa credenciales antes de compilar; habilita también push protection desde los ajustes de GitHub. Véase `docs/SECRETS.md`.
