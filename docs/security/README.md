# Línea base de seguridad y privacidad

- Minimizar telemetría y conservar muestras crudas solo el tiempo aprobado.
- Solicitar consentimiento explícito para datos fisiológicos y ubicación.
- No registrar contraseñas, tokens, coordenadas completas ni muestras crudas.
- Separar roles de paciente, cuidador y administrador.
- Tratar toda entrada de red, Data Layer y almacenamiento como no confiable.
- Aplicar idempotencia y correlación desde la primera ruta vertical.
- No presentar resultados como diagnóstico ni sustituir atención profesional.

Las credenciales locales viven en `.env`, que está excluido de Git. `.env.example` solo contiene nombres y valores de desarrollo no secretos.
