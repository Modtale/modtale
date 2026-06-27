import { afterEach, describe, expect, it, vi } from 'vitest';
import { compareGameVersionsDesc, compareSemVer, formatDateTime, formatTimeAgo, getLicenseInfo, toTitleCase } from '@/utils/modHelpers';

describe('mod helpers', () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it('formats recent timestamps into compact relative labels', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-01-10T00:00:00Z'));

        expect(formatTimeAgo('2026-01-09T23:59:45Z')).toBe('Just now');
        expect(formatTimeAgo('2026-01-09T23:57:00Z')).toBe('3m ago');
        expect(formatTimeAgo('2026-01-09T21:00:00Z')).toBe('3h ago');
        expect(formatTimeAgo('2026-01-07T00:00:00Z')).toBe('3d ago');
        expect(formatTimeAgo('2025-11-10T00:00:00Z')).toBe('2mo ago');
        expect(formatTimeAgo('2024-01-10T00:00:00Z')).toBe('2y ago');
    });

    it('formats timestamps into readable local date-time labels', () => {
        expect(formatDateTime('2026-06-01T21:59:41.107666477Z')).toBe(
            new Intl.DateTimeFormat(undefined, {
                month: 'short',
                day: 'numeric',
                year: 'numeric',
                hour: 'numeric',
                minute: '2-digit'
            }).format(new Date('2026-06-01T21:59:41.107666477Z'))
        );
        expect(formatDateTime('not-a-date')).toBe('not-a-date');
        expect(formatDateTime()).toBe('Unknown');
    });

    it('compares semantic versions, including prereleases', () => {
        expect(compareSemVer('1.0.0', '1.0.0-beta.1')).toBe(1);
        expect(compareSemVer('1.0.0-beta.2', '1.0.0-beta.1')).toBe(1);
        expect(compareSemVer('1.0.0-beta', '1.0.0-beta.1')).toBe(-1);
        expect(compareSemVer('1.0.0+build.1', '1.0.0+build.2')).toBe(0);
    });

    it('falls back to locale-aware comparison for invalid versions', () => {
        expect(compareSemVer('version10', 'version2')).toBe(1);
    });

    it('sorts game versions newest-first with semver before legacy date builds', () => {
        expect([
            '2026.03.26-89796E57B',
            '0.5.3',
            '2026.02.01-ABCDEF123',
            '0.6.0-pre.3',
            '0.5.4'
        ].sort(compareGameVersionsDesc)).toEqual([
            '0.6.0-pre.3',
            '0.5.4',
            '0.5.3',
            '2026.03.26-89796E57B',
            '2026.02.01-ABCDEF123'
        ]);
    });

    it('maps common license identifiers to friendly names and urls', () => {
        expect(getLicenseInfo('mit')).toEqual({
            name: 'MIT',
            url: 'https://opensource.org/licenses/MIT'
        });

        expect(getLicenseInfo('CC-BY-NC-SA-4.0')).toEqual({
            name: 'CC BY-NC-SA 4.0',
            url: 'https://creativecommons.org/licenses/by-nc-sa/4.0/'
        });

        expect(getLicenseInfo('ARR')).toEqual({
            name: 'All Rights Reserved',
            url: null
        });

        expect(getLicenseInfo('Custom License')).toEqual({
            name: 'Custom License',
            url: null
        });
    });

    it('title-cases classifications with a special label for saves', () => {
        expect(toTitleCase('PLUGIN')).toBe('Plugin');
        expect(toTitleCase('SAVE')).toBe('World');
        expect(toTitleCase('')).toBe('');
    });
});
