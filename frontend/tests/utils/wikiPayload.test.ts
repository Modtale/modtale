import { describe, expect, it } from 'vitest';
import { isWikiMetadata, isWikiPage } from '@/utils/wikiPayload';

const metadata = { index: { slug: 'intro' }, pages: [{ slug: 'intro', title: 'Intro' }, { title: 'Guides', children: [{ slug: 'guides/install', title: 'Install' }] }] };

describe('wiki payload validation', () => {
    it('accepts navigable metadata and legitimate empty navigation', () => {
        expect(isWikiMetadata(metadata)).toBe(true);
        expect(isWikiMetadata({ pages: [], index: null })).toBe(true);
        expect(isWikiMetadata({ pages: [{ name: 'Guides', children: [{ slug: 'intro', title: null, children: null }] }] })).toBe(true);
    });
    it.each([null, {}, [], { error: 'upstream failure' }, { pages: {} }, { pages: [null] }, { pages: [{}] },
        { pages: [{ title: 'Group', children: {} }] }, { pages: [{ slug: 1 }] }, { pages: [], index: {} },
        { pages: [], index: { slug: '' } }, { pages: [], error: 'failure' }])('rejects malformed metadata %j', value => {
        expect(isWikiMetadata(value)).toBe(false);
    });
    it('accepts markdown and legitimate empty page content', () => {
        expect(isWikiPage({ title: 'Intro', content: '# Welcome' })).toBe(true);
        expect(isWikiPage({ title: null, content: '' })).toBe(true);
        expect(isWikiPage({ content: 'Fallback title comes from metadata' })).toBe(true);
    });
    it.each([null, {}, [], { error: 'failure' }, { title: 'Intro' }, { content: {} }, { content: [] },
        { content: 'text', title: {} }, { content: 'text', error: 'failure' }])('rejects malformed page %j', value => {
        expect(isWikiPage(value)).toBe(false);
    });
});
