# AnxietyWatch Mobile

Aplicación React Native 0.84 para Android. El teléfono será el coordinador local, pero esta primera entrega contiene únicamente una base compilable y segura.

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
