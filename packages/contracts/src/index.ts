import { z } from 'zod';

const isoDateTime = z.iso.datetime({ offset: true });
const uuid = z.uuid();

export const healthStatusSchema = z.object({
  status: z.literal('ok'),
  service: z.string().min(1),
  version: z.string().min(1),
});

export const sensorAvailabilitySchema = z.enum([
  'available',
  'unsupported',
  'unavailable',
]);

export const deviceCapabilitiesSchema = z.object({
  deviceModel: z.string().min(1),
  wearOsVersion: z.string().min(1),
  healthPlatformVersion: z.string().nullable(),
  capabilities: z.object({
    heartRate: sensorAvailabilitySchema,
    ibi: sensorAvailabilitySchema,
    accelerometer: sensorAvailabilitySchema,
    ppg: sensorAvailabilitySchema,
    skinTemperature: sensorAvailabilitySchema,
    eda: sensorAvailabilitySchema,
    spo2OnDemand: sensorAvailabilitySchema,
    ecgOnDemand: sensorAvailabilitySchema,
  }),
});

export const signalQualitySchema = z.enum(['good', 'fair', 'poor', 'unknown']);
export const wearingStateSchema = z.enum(['onBody', 'offBody', 'unknown']);

export const telemetrySampleSchema = z.object({
  timestamp: isoDateTime,
  heartRateBpm: z.number().positive().nullable(),
  ibiMs: z.array(z.number().positive()).max(16),
  accelerometer: z
    .object({
      x: z.number(),
      y: z.number(),
      z: z.number(),
    })
    .nullable(),
  skinTemperatureCelsius: z.number().nullable(),
  ambientTemperatureCelsius: z.number().nullable(),
  quality: z.object({
    heartRate: signalQualitySchema,
    ibi: signalQualitySchema,
    wearingState: wearingStateSchema,
  }),
});

export const telemetryBatchSchema = z
  .object({
    batchId: uuid,
    deviceId: uuid,
    userId: uuid,
    sessionId: uuid,
    startedAt: isoDateTime,
    endedAt: isoDateTime,
    sequence: z.int().nonnegative(),
    samples: z.array(telemetrySampleSchema).min(1).max(600),
  })
  .refine((batch) => Date.parse(batch.endedAt) >= Date.parse(batch.startedAt), {
    message: 'endedAt must be greater than or equal to startedAt',
    path: ['endedAt'],
  });

export const eventResponseSchema = z.enum([
  'PHYSICAL_ACTIVITY',
  'USER_OK',
  'NEED_SUPPORT',
  'NO_RESPONSE',
]);

export const userResponseSchema = z.object({
  eventId: uuid,
  response: eventResponseSchema,
  respondedAt: isoDateTime,
  source: z.enum(['WATCH', 'MOBILE']),
});

export const sosTriggerSchema = z.object({
  eventId: uuid,
  userId: uuid,
  deviceId: uuid,
  triggeredAt: isoDateTime,
  source: z.enum(['WATCH', 'MOBILE']),
  reason: z.string().trim().min(1).max(500).nullable().optional(),
});

export const sosCancelSchema = z.object({
  eventId: uuid,
  userId: uuid,
  deviceId: uuid,
  cancelledAt: isoDateTime,
  reason: z.string().trim().min(1).max(500).nullable().optional(),
});

export const suspiciousEventSchema = z.object({
  eventId: uuid,
  deviceId: uuid,
  detectedAt: isoDateTime,
  ruleVersion: z.string().min(1),
  score: z.number().min(0).max(1),
  features: z.record(z.string(), z.number().nullable()),
  availableSensors: z.array(z.string()),
  missingSensors: z.array(z.string()),
});

export type HealthStatus = z.infer<typeof healthStatusSchema>;
export type DeviceCapabilities = z.infer<typeof deviceCapabilitiesSchema>;
export type TelemetrySample = z.infer<typeof telemetrySampleSchema>;
export type TelemetryBatch = z.infer<typeof telemetryBatchSchema>;
export type UserResponse = z.infer<typeof userResponseSchema>;
export type SosTrigger = z.infer<typeof sosTriggerSchema>;
export type SosCancel = z.infer<typeof sosCancelSchema>;
export type SuspiciousEvent = z.infer<typeof suspiciousEventSchema>;
