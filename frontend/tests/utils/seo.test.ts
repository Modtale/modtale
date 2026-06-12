import { describe, expect, it } from 'vitest';
import {
    buildCanonicalUrl,
    getRobotsDirective,
    hasNoIndexQueryFilters,
    isBotUserAgent,
    isBrowseRoutePath,
    isProjectSubrouteNoIndex,
    isUtilityNoIndexPath,
    normalizeSeoPath,
} from '@/utils/seo';

describe('seo utils', () => {
    it('detects bots and normalizes paths', () => {
        expect(isBotUserAgent(null)).toBe(false);
        expect(isBotUserAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36')).toBe(false);
        expect(isBotUserAgent('ExampleCrawler/1.0')).toBe(true);
        expect(isBotUserAgent('Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)')).toBe(true);
        expect(normalizeSeoPath('/mods/')).toBe('/mods');
        expect(normalizeSeoPath('/')).toBe('/');
    });

    it('identifies browse routes and noindex utility routes', () => {
        expect(isBrowseRoutePath('/mods')).toBe(true);
        expect(isBrowseRoutePath('/plugins/')).toBe(true);
        expect(isBrowseRoutePath('/creator/test')).toBe(false);

        expect(isUtilityNoIndexPath('/upload')).toBe(true);
        expect(isUtilityNoIndexPath('/dashboard/profile')).toBe(true);
        expect(isUtilityNoIndexPath('/mods')).toBe(false);
    });

    it('marks project subroutes and filtered queries as noindex', () => {
        expect(isProjectSubrouteNoIndex('/mod/cool-project~id/download')).toBe(true);
        expect(isProjectSubrouteNoIndex('/mod/cool-project/download')).toBe(true);
        expect(isProjectSubrouteNoIndex('/world/world-builder~id/wiki/getting-started')).toBe(true);
        expect(isProjectSubrouteNoIndex('/mod/cool-project~id')).toBe(false);

        expect(hasNoIndexQueryFilters('?q=magic')).toBe(true);
        expect(hasNoIndexQueryFilters(new URLSearchParams('tags=tech'))).toBe(true);
        expect(hasNoIndexQueryFilters('?page=2')).toBe(false);
    });

    it('builds canonical urls and robots directives from query state', () => {
        expect(buildCanonicalUrl('/mods', '?q=magic&page=2')).toBe('https://modtale.net/mods?page=2');
        expect(buildCanonicalUrl('/plugins/', new URLSearchParams('sort=popular'))).toBe('https://modtale.net/plugins');
        expect(buildCanonicalUrl('/', '')).toBe('https://modtale.net/');

        expect(getRobotsDirective('/mods', '?page=2')).toBe('index,follow');
        expect(getRobotsDirective('/mods', '?q=magic')).toBe('noindex,follow');
        expect(getRobotsDirective('/upload', '')).toBe('noindex,follow');
        expect(getRobotsDirective('/mod/cool-project~id/download', '')).toBe('noindex,follow');
    });
});
