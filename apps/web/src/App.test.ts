import { describe, expect, it } from 'vitest';

import { dashboardSummary } from './App';

describe('dashboard summary', () => {
  it('starts without claiming an active health event', () => {
    expect(dashboardSummary.status).toBe('Sin eventos activos');
  });
});
