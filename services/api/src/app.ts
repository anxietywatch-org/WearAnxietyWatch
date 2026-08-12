import { randomUUID } from 'node:crypto';

import {
  sosCancelSchema,
  sosTriggerSchema,
  telemetryBatchSchema,
  type HealthStatus,
} from '@anxietywatch/contracts';
import express, {
  type ErrorRequestHandler,
  type NextFunction,
  type Request,
  type Response,
} from 'express';

import {
  acceptedCaregiversFor,
  pushNotifier,
  sosEventRepository,
  telemetryRepository,
} from './repositories.ts';

const version = '0.1.0';

export const app = express();

app.disable('x-powered-by');
app.use(express.json({ limit: '1mb' }));
app.use((request: Request, response: Response, next: NextFunction) => {
  const correlationId = request.header('x-correlation-id') ?? randomUUID();
  response.setHeader('x-correlation-id', correlationId);
  next();
});

/**
 * Valida el token Bearer contra API_AUTH_TOKENS (CSV de tokens válidos).
 * 401 si falta el header o no es Bearer; 403 si el token no es válido.
 */
function bearerAuth(request: Request, response: Response, next: NextFunction) {
  const header = request.header('authorization');
  if (!header?.startsWith('Bearer ')) {
    response.status(401).json({
      error: 'unauthorized',
      message: 'Falta el encabezado Authorization con un token Bearer.',
    });
    return;
  }
  const token = header.slice('Bearer '.length).trim();
  const validTokens = (process.env.API_AUTH_TOKENS ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
  if (!validTokens.includes(token)) {
    response.status(403).json({
      error: 'forbidden',
      message: 'El token Bearer no es válido.',
    });
    return;
  }
  next();
}

app.use('/api/v1', bearerAuth);

app.get('/health', (_request: Request, response: Response<HealthStatus>) => {
  response.status(200).json({
    status: 'ok',
    service: 'anxietywatch-api',
    version,
  });
});

app.post('/api/v1/telemetry/batch', (request: Request, response: Response) => {
  const result = telemetryBatchSchema.safeParse(request.body);
  if (!result.success) {
    response
      .status(400)
      .json({ error: 'invalid_request', issues: result.error.issues });
    return;
  }

  const accepted = telemetryRepository.saveIfNew(result.data);
  response.status(accepted ? 202 : 200).json({
    batchId: result.data.batchId,
    accepted,
    duplicate: !accepted,
  });
});

app.post(
  '/api/v1/sos/trigger',
  async (request: Request, response: Response) => {
    const result = sosTriggerSchema.safeParse(request.body);
    if (!result.success) {
      response
        .status(400)
        .json({ error: 'invalid_request', issues: result.error.issues });
      return;
    }

    const accepted = sosEventRepository.saveIfNew(result.data);
    if (accepted) {
      await pushNotifier.notifyCaregivers(
        result.data,
        acceptedCaregiversFor(result.data.userId),
      );
    }

    response.status(accepted ? 202 : 200).json({
      eventId: result.data.eventId,
      accepted,
      duplicate: !accepted,
    });
  },
);

app.post(
  '/api/v1/sos/cancel',
  (request: Request, response: Response) => {
    const result = sosCancelSchema.safeParse(request.body);
    if (!result.success) {
      response
        .status(400)
        .json({ error: 'invalid_request', issues: result.error.issues });
      return;
    }

    const accepted = sosEventRepository.cancel(result.data);
    response.status(accepted ? 202 : 200).json({
      eventId: result.data.eventId,
      accepted,
      duplicate: !accepted,
    });
  },
);

app.use((_request: Request, response: Response) => {
  response.status(404).json({
    error: 'not_found',
    message: 'El recurso solicitado no existe.',
  });
});

const errorHandler: ErrorRequestHandler = (
  error,
  _request,
  response,
  _next,
) => {
  // Errores del body parser (express.json): respetar su semántica HTTP.
  if (error instanceof SyntaxError && 'status' in error) {
    const status = (error as SyntaxError & { status: number }).status;
    if (status === 400) {
      response.status(400).json({
        error: 'invalid_json',
        message: 'El cuerpo de la solicitud no es JSON válido.',
      });
      return;
    }
  }
  const status = (error as Error & { status?: number; type?: string }).status;
  if (status === 413 || (error as { type?: string }).type === 'entity.too.large') {
    response.status(413).json({
      error: 'payload_too_large',
      message: 'El cuerpo de la solicitud excede el límite permitido.',
    });
    return;
  }
  const message = error instanceof Error ? error.message : 'Unknown error';
  console.error(JSON.stringify({ level: 'error', message }));
  response.status(500).json({
    error: 'internal_error',
    message: 'No fue posible completar la solicitud.',
  });
};

app.use(errorHandler);
