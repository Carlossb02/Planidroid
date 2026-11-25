# Seguridad y pruebas

- Firebase privado: reglas de UID, validación de forma, límites, versión monotónica y timestamp de servidor. Datos sensibles locales no se sincronizan. Room está aislado por scope lógico, no cifrado adicional.
- Espacios compartidos: propietario por UID; lector, editor y administrador por membresía aceptada y UID aceptante. Invitaciones con correo exacto verificado, 7 días, sin acceso previo a aceptación. El administrador no puede crear/revocar otros administradores, cambiar dueño ni elevar su rol. El propietario controla permisos. Los comentarios se autentican por UID y son inmutables. No se comparte ninguna otra sección privada.
- El contenido de los espacios es JSON limitado a 50 KB: las reglas comprueban la forma externa y los roles, no cada elemento interior. La edición es online y transaccional por versión. No hay transferencia ni eliminación de propietario en este prototipo.
- Las consultas de colaboración se hacen al servidor y no se guardan en Room. No usar en producción antes de ejecutar las pruebas y una revisión de reglas independientes.
- La resolución de conflicto privado compara versiones y guarda recuperación por cuenta antes de conservar nube. Los conflictos entre documentos dependientes pueden requerir revisión manual. La sincronización no es una transacción global entre múltiples dispositivos.
- Backups privados, sin credenciales; la importación ZIP exige rutas controladas, límites y referencias válidas. JSON externo pierde URIs para no confiar en referencias arbitrarias. Los archivos importados se guardan en almacenamiento privado y se abren mediante FileProvider. No hay cifrado de exportación.
- No se implementa todavía borrado integral de cuenta remota, transferencia de propiedad, FCM, App Check, almacenamiento remoto de adjuntos ni política de privacidad final. No confundir vaciar espacio local con eliminar cuenta.

## Emulator Suite
Con Firebase CLI, Node y Java instalados:
```sh
cd security-tests
npm install
cd ..
firebase emulators:exec --project demo-planidroid --only firestore "npm --prefix security-tests test"
```
Casos: propietario/terceros/anónimo, invitación pendiente, aceptación, cambio de rol indebido, lector sin escritura, editor sin gestión de miembros, modificación de propietario, identidad de autor y revocación. **Las pruebas están escritas pero no se han ejecutado en este entorno.**
