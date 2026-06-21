import { describe, expect, it } from 'vitest';
import { chartVisibilityKey } from '@/components/ui/charts/chartVisibility';

describe('chartVisibilityKey', () => {
    it('scopes matching series ids to their chart', () => {
        expect(chartVisibilityKey('downloads', 'overall')).not.toBe(chartVisibilityKey('views', 'overall'));
    });
});
