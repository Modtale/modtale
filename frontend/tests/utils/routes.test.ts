import { describe, expect, it } from 'vitest';
import { SiteRoutes } from '@/utils/routes';

describe('SiteRoutes', () => {
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

    it('generates project routes from title and classification when slug is blank', () => {
        expect(SiteRoutes.project({
            id: 'abc123',
            title: 'Amazing Plugin',
            slug: '   ',
            classification: 'PLUGIN'
        })).toBe('/mod/amazing-plugin-abc123');
        expect(SiteRoutes.project({
            id: 'abc123',
            title: 'World Save',
            classification: 'SAVE'
        })).toBe('/world/world-save-abc123');
    });

    it('builds sub-routes for project pages', () => {
        const project = {
            id: 'abc123',
            title: 'Amazing Plugin',
            classification: 'PLUGIN'
        };

        expect(SiteRoutes.projectDownload(project)).toBe('/mod/amazing-plugin-abc123/download');
        expect(SiteRoutes.projectChangelog(project)).toBe('/mod/amazing-plugin-abc123/changelog');
        expect(SiteRoutes.projectGallery(project)).toBe('/mod/amazing-plugin-abc123/gallery');
        expect(SiteRoutes.projectWiki(project)).toBe('/mod/amazing-plugin-abc123/wiki');
        expect(SiteRoutes.projectWiki(project, 'docs/install')).toBe('/mod/amazing-plugin-abc123/wiki/docs/install');
        expect(SiteRoutes.projectEdit(project)).toBe('/mod/amazing-plugin-abc123/edit');
    });

    it('creates clean slugs and truncates long titles', () => {
        expect(SiteRoutes.createSlug('This Title Has !!! plenty_of__symbols and words', 'id123'))
            .toBe('this-title-has-plenty-of-symbo-id123');
    });

    it('falls back to the id when the title is empty or fully stripped', () => {
        expect(SiteRoutes.createSlug('', 'id123')).toBe('id123');
        expect(SiteRoutes.createSlug('!!!', 'id123')).toBe('id123');
    });

    it('adds a mod suffix when the sanitized title is numeric only', () => {
        expect(SiteRoutes.createSlug('12345', 'id123')).toBe('12345-mod-id123');
    });

    it('extracts uuid suffixes from route params', () => {
        const uuid = '123e4567-e89b-12d3-a456-426614174000';

        expect(SiteRoutes.extractId(`cool-project-${uuid}`)).toBe(uuid);
        expect(SiteRoutes.extractId('not-a-uuid')).toBe('not-a-uuid');
        expect(SiteRoutes.extractId(undefined)).toBe('');
    });
});
