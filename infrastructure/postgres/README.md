# PostgreSQL local

## Comandos

- Iniciar: `docker compose up -d postgres`
- Ver estado: `docker compose ps`
- Detener: `docker compose down`

Los datos persisten en el volumen `anxietywatch_postgres_data`. El script `init/001_bootstrap.sql` solo crea la tabla de control inicial; el esquema clínico y de telemetría llegará mediante migraciones posteriores.

Nunca utilices la contraseña predeterminada fuera del entorno local. Copia `.env.example` a `.env` y define una credencial propia.
