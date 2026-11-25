# Credenciales privadas y publicación

El repositorio es público. **Nunca pegar valores reales en código, README, issues, PR, logs, gradle.properties ni BuildConfig.**

- Firebase Android: `app/google-services.json` es una configuración cliente que se excluye de Git en todas las variantes. Colócala localmente solo para compilar con Firebase. Su contenido cliente (incluida la API key de Firebase) se puede extraer de un APK; restringe su uso y protege Firestore con reglas. No es una clave de administración.
- Gemini: `GEMINI_API_KEY` solo en el servidor, mediante un gestor de secretos o variable de entorno. No introducirla en `google-services.json`, Android, el ZIP ni GitHub. El backend Kotlin ya lee las variables de entorno.
- Firebase Admin: preferir Application Default Credentials (identidad del servicio). Si se necesita un JSON de cuenta de servicio, guardarlo **fuera del repositorio**, por ejemplo en un directorio privado del usuario y apuntar `GOOGLE_APPLICATION_CREDENTIALS` a su ruta. No subirlo ni incorporarlo al APK.
- Firma de Android: almacén de claves y contraseñas fuera del repositorio, con copia privada. No publicar archivos JKS/PKCS12. Si se usa CI para firmar, configurar credenciales en un entorno protegido; no ejecutar compilaciones de PR no confiables con esos secretos.
- OAuth: no guardar secretos de clientes web/servidor ni tokens en Git. Android usa la configuración pública de cliente Android.
- Backups, bases de datos y archivos de datos del usuario se conservan fuera de Git.

## Barreras contra errores

1. `.gitignore` excluye las rutas y extensiones sensibles; no protege archivos ya rastreados ni `git add -f`.
2. En cada clon/Codespace, activar los hooks con `bash tools/install-git-hooks.sh`. Requiere Python 3 (incluido en Codespaces). Pre-commit revisa el índice; pre-push revisa los commits que se van a enviar, incluso archivos añadidos y eliminados en commits sucesivos. Los errores bloquean la operación sin mostrar valores.
3. Android CI revisa el árbol del commit antes de compilar. CI ocurre después del envío: no evita una filtración inicial.
4. En GitHub habilitar secret scanning/push protection si están disponibles y configurar la comprobación `credentials-check` como obligatoria en la rama principal. No se ha cambiado la configuración de seguridad de GitHub desde aquí. Los hooks son locales, se pueden desactivar y detectan patrones conocidos, no todos los secretos; no hay garantía absoluta. No usar `--no-verify`.

Comprobar antes de enviar: `git check-ignore -v app/google-services.json` y `python3 tools/check_secrets.py --staged`. Si un archivo privado ya estuviera rastreado, no basta con añadirlo a .gitignore: retirarlo del índice, revisar el historial y revocar/rotar inmediatamente cualquier credencial que ya se haya publicado.
