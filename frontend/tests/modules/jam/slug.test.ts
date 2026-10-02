import { describe, expect, it } from 'vitest';
import { jamSlugFromTitle, normalizeJamSlug, validateJamSlug } from '@/modules/jam/utils/slug';

describe('jam URL editing', () => {
    it('cleans pasted URLs and punctuation without copying the host or query', () => {
        expect(normalizeJamSlug('https://modtale.net/jam/Summer-Build?tab=rules#top')).toBe('summer-build');
        expect(normalizeJamSlug('/jam/My-Build/overview')).toBe('my-build');
        expect(normalizeJamSlug('My   Great JAM')).toBe('my-great-jam');
        expect(jamSlugFromTitle('  Hello, builders!  ')).toBe('hello-builders');
    });
    it('enforces the documented length and dash boundaries', () => {
        for (const slug of ['a', 'ab', '-abc', 'abc-', 'a'.repeat(51)]) expect(validateJamSlug(slug)).not.toBeNull();
        for (const slug of ['abc', 'a-b', 'a'.repeat(50)]) expect(validateJamSlug(slug)).toBeNull();
    });
});
