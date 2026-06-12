import React from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { FeaturedModCard } from '@/modules/home/components/HeroMarquee';

describe('FeaturedModCard project links', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    it('renders the canonical slug route for featured projects', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <FeaturedModCard
                        project={{
                            id: 'project-1',
                            slug: 'levelingcore',
                            title: 'LevelingCore',
                            authorId: 'author-1',
                            author: 'AzureDoom',
                            classification: 'PLUGIN',
                            downloadCount: 905,
                            favoriteCount: 21,
                            updatedAt: '2026-06-01T00:00:00Z',
                            comments: [],
                            versions: [],
                            galleryImages: []
                        } as any}
                    />
                </MemoryRouter>
            );
        });

        expect(container.querySelector('a[aria-label="Download LevelingCore Hytale Mod"]')?.getAttribute('href'))
            .toBe('/mod/levelingcore');
    });
});
