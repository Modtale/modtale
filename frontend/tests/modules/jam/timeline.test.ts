import { describe, expect, it } from 'vitest';

import { getClampedJamMilestoneDate, getJamMilestoneMinimum } from '@/modules/jam/utils/timeline';

describe('jam timeline constraints', () => {
    const todayStart = new Date('2026-08-12T00:00:00Z').getTime();

    it('uses a later preceding milestone as the next deadline minimum', () => {
        expect(getJamMilestoneMinimum(todayStart, '2026-08-20T18:00:00Z'))
            .toBe(new Date('2026-08-20T18:00:00Z').getTime());
    });

    it('never permits a milestone before today or its preceding milestone', () => {
        const minimum = getJamMilestoneMinimum(todayStart, '2026-08-20T18:00:00Z');

        expect(getClampedJamMilestoneDate('2026-08-19T18:00:00Z', minimum).getTime()).toBe(minimum);
        expect(getClampedJamMilestoneDate('2026-08-22T18:00:00Z', minimum).toISOString())
            .toBe('2026-08-22T18:00:00.000Z');
    });

    it('falls back safely when stored dates are absent or invalid', () => {
        expect(getJamMilestoneMinimum(todayStart, 'not-a-date')).toBe(todayStart);
        expect(getClampedJamMilestoneDate(undefined, todayStart).getTime()).toBe(todayStart);
    });
});
