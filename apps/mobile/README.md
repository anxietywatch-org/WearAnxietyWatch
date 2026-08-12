# AnxietyWatch Mobile

Aplicación React Native 0.84 para Android que funciona como nodo fog entre el
Galaxy Watch y el backend oficial.

La app inicia sesión en `https://api.mangoon.xyz`, guarda el JWT cifrado con
Android Keystore y restaura/renueva la sesión mediante `/api/auth/session`. Los
sobres recibidos por Wear Data Layer quedan en Room y una tarea Headless JS
intenta entregarlos aunque la interfaz no esté abierta. Android no permite
despertar una app que el usuario haya detenido de forma forzada; el reloj
conserva los sobres durante ese caso.

## Variables de entorno

La plantilla no requiere secretos. Las URL y credenciales futuras se documentarán en `.env.example` y nunca se guardarán en el repositorio.

## Comandos

- `npm run start --workspace @anxietywatch/mobile`
- `npm run android --workspace @anxietywatch/mobile`
- `npm run test --workspace @anxietywatch/mobile`
- `npm run lint --workspace @anxietywatch/mobile`

Para compilar Android sin iniciar un emulador:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
Set-Location apps\mobile\android
.\gradlew.bat assembleDebug
```

## Arquitectura y manejo de errores

Los módulos futuros vivirán bajo `src/` por dominio. El módulo nativo de Wearable Data Layer será un adaptador, no una dependencia directa de la detección. Las colas offline deben ser idempotentes.

## Criterios de aceptación

- El bundle TypeScript y la aplicación Android compilan.
- La pantalla inicial indica que el reloj aún no está vinculado.
- El texto visible no afirma diagnósticos.

## Limitaciones conocidas

No hay autenticación, Data Layer, almacenamiento offline ni permisos de ubicación. Se implementarán después de la prueba vertical.
