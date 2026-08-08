import { app } from './app.ts';

const port = Number.parseInt(process.env.API_PORT ?? '3000', 10);

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
  server.close(() => process.exit(0));
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
