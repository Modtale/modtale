import { describe, expect, it } from 'vitest';
import { BUFFER, calculateRollingAverage, calculateWoW, generateEmptyHistory, sliceData } from '@/utils/analytics';

describe('analytics utils', () => {
    it('slices off the buffer window from history arrays', () => {
        const data = Array.from({ length: BUFFER + 3 }, (_, i) => i);
        expect(sliceData(data)).toEqual([BUFFER, BUFFER + 1, BUFFER + 2]);
    });

    it('calculates week-over-week deltas after enough data exists', () => {
        const fullData = Array.from({ length: 14 }, (_, i) => ({
            date: `2026-01-${String(i + 1).padStart(2, '0')}`,
            value: i < 7 ? 1 : 2
        }));

        const wow = calculateWoW(fullData);

        expect(wow.slice(0, 13).every(point => point.value === 0)).toBe(true);
        expect(wow[13]).toEqual({
            date: '2026-01-14',
            value: 100
        });
    });

    it('calculates rolling averages and coerces non-numeric values to zero', () => {
        const rolling = calculateRollingAverage([
            { date: '2026-01-01', value: 1 },
            { date: '2026-01-02', value: '3' },
            { date: '2026-01-03', value: 'bad' }
        ], 2);

        expect(rolling).toEqual([
            { date: '2026-01-01', value: 1 },
            { date: '2026-01-02', value: 2 },
            { date: '2026-01-03', value: 1.5 }
        ]);
    });

    it('returns an empty array for invalid rolling average inputs', () => {
        expect(calculateRollingAverage('not-an-array' as any, 2)).toEqual([]);
        expect(calculateRollingAverage([], 0)).toEqual([]);
    });

    it('generates zero-filled history for each day in the requested range', () => {
        expect(generateEmptyHistory(2, new Date('2026-01-01T12:00:00Z'))).toEqual([
            { date: '2026-01-01', value: 0, count: 0 },
            { date: '2026-01-02', value: 0, count: 0 },
            { date: '2026-01-03', value: 0, count: 0 }
        ]);
    });
});
