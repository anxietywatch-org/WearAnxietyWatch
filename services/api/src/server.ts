import { app } from './app.ts';
import { sharedDatabase } from './repositories.ts';

const rawPort = process.env.API_PORT ?? '3000';
const port = Number.parseInt(rawPort, 10);
if (!Number.isInteger(port) || port <= 0 || port > 65_535) {
  console.error(
    JSON.stringify({
      level: 'error',
      message: 'API_PORT inválido',
      value: rawPort,
    }),
  );
  process.exit(1);
}

const server = app.listen(port, () => {
  console.log(
    JSON.stringify({
      level: 'info',
      message: 'AnxietyWatch API listening',
      port,
    }),
  );
});

const shutdown = () => {
  server.close(() => {
    sharedDatabase.close();
    process.exit(0);
  });
  // Si quedan conexiones abiertas, no alargar el apagado indefinidamente.
  setTimeout(() => process.exit(0), 5_000).unref();
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);