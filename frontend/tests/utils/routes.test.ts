import { describe, expect, it } from 'vitest';
import { SiteRoutes } from '@/utils/routes';

describe('SiteRoutes', () => {
    it('returns the launcher route', () => {
        expect(SiteRoutes.launcher()).toBe('/launcher');
    });

    it('maps browse routes by classification', () => {
        expect(SiteRoutes.browse('PLUGIN')).toBe('/plugins');
        expect(SiteRoutes.browse('MODPACK')).toBe('/modpacks');
        expect(SiteRoutes.browse('SAVE')).toBe('/worlds');
        expect(SiteRoutes.browse('ART')).toBe('/art');
        expect(SiteRoutes.browse('DATA')).toBe('/data');
        expect(SiteRoutes.browse('UNKNOWN')).toBe('/mods');
    });

    it('returns the home route when a project is missing', () => {
        expect(SiteRoutes.project(null)).toBe('/');
    });

    it('uses an explicit slug when one is provided', () => {
        expect(SiteRoutes.project({
            id: 'abc123',
            title: 'Ignored Title',
            slug: 'custom-slug',
            classification: 'MODPACK'
        })).toBe('/modpack/custom-slug');
    });

    it('uses clean creator usernames when available', () => {
        expect(SiteRoutes.creator('user-1', 'AzureDoom')).toBe('/creator/AzureDoom');
        expect(SiteRoutes.creator('user-1', '  azuredoom  ')).toBe('/creator/azuredoom');
        expect(SiteRoutes.creator('user-1')).toBe('/creator/user-1');
    });

    it('generates project routes from title and classification when slug is blank', () => {
        expect(SiteRoutes.project({
            id: 'abc123',
            title: 'LevelingCore',
            slug: '   ',
            classification: 'PLUGIN'
        })).toBe('/mod/levelingcore~abc123');
        expect(SiteRoutes.project({
            id: 'abc123',
            title: 'Ev0s Smokable Herbs',
            classification: 'SAVE'
        })).toBe('/world/ev0s-smokable-herbs~abc123');
    });

    it('builds sub-routes for project pages', () => {
        const project = {
            id: 'abc123',
            title: 'LevelingCore',
            classification: 'PLUGIN',
            slug: 'levelingcore'
        };

        expect(SiteRoutes.projectDownload(project)).toBe('/mod/levelingcore/download');
        expect(SiteRoutes.projectChangelog(project)).toBe('/mod/levelingcore/changelog');
        expect(SiteRoutes.projectGallery(project)).toBe('/mod/levelingcore/gallery');
        expect(SiteRoutes.projectWiki(project)).toBe('/mod/levelingcore/wiki');
        expect(SiteRoutes.projectWiki(project, 'docs/install')).toBe('/mod/levelingcore/wiki/docs/install');
        expect(SiteRoutes.projectEdit(project)).toBe('/mod/levelingcore/edit');
    });

    it('matches canonical slug routes and legacy id-suffixed routes', () => {
        const project = {
            id: 'abc123',
            title: 'Ev0s Smokable Herbs',
            slug: 'ev0s-smokable-herbs'
        };

        expect(SiteRoutes.matchesProjectRoute(project, 'ev0s-smokable-herbs')).toBe(true);
        expect(SiteRoutes.matchesProjectRoute(project, 'ev0s-smokable-herbs~abc123')).toBe(true);
        expect(SiteRoutes.matchesProjectRoute(project, 'ev0s-smokable-herbs-abc123')).toBe(false);
        expect(SiteRoutes.matchesProjectRoute(project, 'abc123')).toBe(true);
    });

    it('creates clean slugs and truncates long titles', () => {
        expect(SiteRoutes.createSlug('This Title Has !!! plenty_of__symbols and words', 'id123'))
            .toBe('this-title-has-plenty-of-symbo~id123');
    });

    it('falls back to the id when the title is empty or fully stripped', () => {
        expect(SiteRoutes.createSlug('', 'id123')).toBe('id123');
        expect(SiteRoutes.createSlug('!!!', 'id123')).toBe('id123');
    });

    it('adds a mod suffix when the sanitized title is numeric only', () => {
        expect(SiteRoutes.createSlug('12345', 'id123')).toBe('12345-mod~id123');
    });

    it('extracts ids from route params', () => {
        const uuid = '123e4567-e89b-12d3-a456-426614174000';

        expect(SiteRoutes.extractId(`cool-project~${uuid}`)).toBe(uuid);
        expect(SiteRoutes.extractId(`cool-project-${uuid}`)).toBe(uuid);
        expect(SiteRoutes.extractId('not-a-uuid')).toBe('not-a-uuid');
        expect(SiteRoutes.extractId(undefined)).toBe('');
    });
});
