import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import test from 'node:test';

import { app } from '../src/app.ts';

process.env.API_AUTH_TOKENS = 'test-token-1,test-token-2';

const authHeaders = {
  'content-type': 'application/json',
  authorization: 'Bearer test-token-1',
};

test('GET /health reports the API status', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));

  try {
    const address = server.address() as AddressInfo;
    const response = await fetch(`http://127.0.0.1:${address.port}/health`);
    const body = (await response.json()) as Record<string, unknown>;

    assert.equal(response.status, 200);
    assert.equal(body.status, 'ok');
    assert.equal(body.service, 'anxietywatch-api');
    assert.ok(response.headers.get('x-correlation-id'));
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

const validBatch = {
  batchId: '550e8400-e29b-41d4-a716-446655440010',
  deviceId: '550e8400-e29b-41d4-a716-446655440011',
  userId: '550e8400-e29b-41d4-a716-446655440012',
  sessionId: '550e8400-e29b-41d4-a716-446655440013',
  startedAt: '2026-08-01T13:00:00Z',
  endedAt: '2026-08-01T13:01:00Z',
  sequence: 1,
  samples: [
    {
      timestamp: '2026-08-01T13:00:00Z',
      heartRateBpm: 80,
      ibiMs: [750],
      accelerometer: null,
      skinTemperatureCelsius: null,
      ambientTemperatureCelsius: null,
      quality: { heartRate: 'good', ibi: 'good', wearingState: 'onBody' },
    },
  ],
};

test('POST /api/v1/telemetry/batch is idempotent by batchId', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;

  try {
    const first = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/telemetry/batch`,
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify(validBatch),
      },
    );
    const second = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/telemetry/batch`,
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify(validBatch),
      },
    );

    assert.equal(first.status, 202);
    assert.equal(second.status, 200);
    assert.equal((await second.json()).duplicate, true);
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('POST /api/v1/sos/trigger validates and deduplicates events', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;
  const event = {
    eventId: '550e8400-e29b-41d4-a716-446655440020',
    userId: '550e8400-e29b-41d4-a716-446655440012',
    deviceId: '550e8400-e29b-41d4-a716-446655440011',
    triggeredAt: '2026-08-01T13:02:00Z',
    source: 'MOBILE',
  };

  try {
    const first = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/sos/trigger`,
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify(event),
      },
    );
    const second = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/sos/trigger`,
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify(event),
      },
    );

    assert.equal(first.status, 202);
    assert.equal(second.status, 200);
    assert.equal((await second.json()).duplicate, true);
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('POST /api/v1/sos/cancel validates and deduplicates events', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;
  const cancel = {
    eventId: '550e8400-e29b-41d4-a716-446655440021',
    userId: '550e8400-e29b-41d4-a716-446655440012',
    deviceId: '550e8400-e29b-41d4-a716-446655440011',
    cancelledAt: '2026-08-01T13:03:00Z',
    reason: 'Usuario canceló la alerta',
  };

  try {
    const first = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/sos/cancel`,
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify(cancel),
      },
    );
    const second = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/sos/cancel`,
      {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify(cancel),
      },
    );

    assert.equal(first.status, 202);
    assert.equal(second.status, 200);
    assert.equal((await second.json()).duplicate, true);
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('POST /api/v1/telemetry/batch without token returns 401', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;

  try {
    const response = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/telemetry/batch`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(validBatch),
      },
    );

    assert.equal(response.status, 401);
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('POST /api/v1/telemetry/batch with invalid token returns 403', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;

  try {
    const response = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/telemetry/batch`,
      {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          authorization: 'Bearer wrong-token',
        },
        body: JSON.stringify(validBatch),
      },
    );

    assert.equal(response.status, 403);
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('POST /api/v1/telemetry/batch with malformed JSON returns 400', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;

  try {
    const response = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/telemetry/batch`,
      {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          authorization: 'Bearer test-token-1',
        },
        body: '{not json',
      },
    );

    assert.equal(response.status, 400);
    const body = (await response.json()) as Record<string, unknown>;
    assert.equal(body.error, 'invalid_json');
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('POST /api/v1/telemetry/batch with invalid body returns 400', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;

  try {
    const response = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/telemetry/batch`,
      {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          authorization: 'Bearer test-token-1',
        },
        body: JSON.stringify({ batchId: 'missing-fields' }),
      },
    );

    assert.equal(response.status, 400);
    const body = (await response.json()) as Record<string, unknown>;
    assert.equal(body.error, 'invalid_request');
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

test('GET unknown route returns 404', async () => {
  const server = app.listen(0);
  await new Promise<void>((resolve) => server.once('listening', resolve));
  const address = server.address() as AddressInfo;

  try {
    const response = await fetch(
      `http://127.0.0.1:${address.port}/api/v1/telemetry/nope`,
      {
        headers: { authorization: 'Bearer test-token-1' },
      },
    );

    assert.equal(response.status, 404);
  } finally {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});
