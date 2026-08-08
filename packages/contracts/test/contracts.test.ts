import assert from 'node:assert/strict';
import test from 'node:test';

import {
  deviceCapabilitiesSchema,
  telemetryBatchSchema,
} from '../src/index.ts';

test('Galaxy Watch7 can explicitly report EDA as unsupported', () => {
  const parsed = deviceCapabilitiesSchema.parse({
    deviceModel: 'Samsung Galaxy Watch7',
    wearOsVersion: '6',
    healthPlatformVersion: null,
    capabilities: {
      heartRate: 'available',
      ibi: 'available',
      accelerometer: 'available',
      ppg: 'available',
      skinTemperature: 'available',
      eda: 'unsupported',
      spo2OnDemand: 'available',
      ecgOnDemand: 'available',
    },
  });

  assert.equal(parsed.capabilities.eda, 'unsupported');
});

test('telemetry batches reject empty sample arrays', () => {
  const result = telemetryBatchSchema.safeParse({
    batchId: '550e8400-e29b-41d4-a716-446655440000',
    deviceId: '550e8400-e29b-41d4-a716-446655440001',
    userId: '550e8400-e29b-41d4-a716-446655440002',
    sessionId: '550e8400-e29b-41d4-a716-446655440003',
    startedAt: '2026-08-01T13:00:00Z',
    endedAt: '2026-08-01T13:01:00Z',
    sequence: 1,
    samples: [],
  });

  assert.equal(result.success, false);
});
