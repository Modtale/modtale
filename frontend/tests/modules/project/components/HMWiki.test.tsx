import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { WikiSidebar } from '@/modules/project/components/HMWiki';

const tree = [
    {
        id: 'guides',
        slug: 'guides',
        title: 'Guides',
        children: [
            { id: 'install', slug: 'guides/install', title: 'Install' },
            { id: 'advanced', slug: 'guides/advanced', title: 'Advanced' }
        ]
    }
];

describe('WikiSidebar', () => {
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

    it('lets category pages navigate without losing their collapse toggle', async () => {
        const onNavigate = vi.fn();

        await act(async () => {
            root.render(
                <WikiSidebar
                    tree={tree}
                    projectUrl="/mod/project"
                    currentSlug="guides"
                    onNavigate={onNavigate}
                />
            );
        });

        const guidesButton = [...container.querySelectorAll('button')].find((node) => node.textContent === 'Guides');
        const collapseButton = container.querySelector('button[aria-label="Collapse Guides"]');

        expect(guidesButton).not.toBeNull();
        expect(collapseButton).not.toBeNull();

        await act(async () => {
            guidesButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(onNavigate).toHaveBeenCalledWith('guides');
    });

    it('collapses and expands wiki categories', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <WikiSidebar
                        tree={tree}
                        projectUrl="/mod/project"
                        currentSlug="guides"
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Install');

        const collapseButton = container.querySelector('button[aria-label="Collapse Guides"]');
        expect(collapseButton).not.toBeNull();

        await act(async () => {
            collapseButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(container.textContent).not.toContain('Install');

        const expandButton = container.querySelector('button[aria-label="Expand Guides"]');
        expect(expandButton).not.toBeNull();

        await act(async () => {
            expandButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(container.textContent).toContain('Install');
    });

    it('reopens the branch that contains the active page', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter>
                    <WikiSidebar
                        tree={tree}
                        projectUrl="/mod/project"
                        currentSlug="guides"
                    />
                </MemoryRouter>
            );
        });

        const collapseButton = container.querySelector('button[aria-label="Collapse Guides"]');
        expect(collapseButton).not.toBeNull();

        await act(async () => {
            collapseButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(container.textContent).not.toContain('Install');

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <WikiSidebar
                        tree={tree}
                        projectUrl="/mod/project"
                        currentSlug="guides/install"
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Install');
        expect(container.querySelector('button[aria-label="Collapse Guides"]')).not.toBeNull();
    });

    it('does not let empty category pages navigate but keeps their collapse toggle', async () => {
        const onNavigate = vi.fn();
        const emptyCache = {
            'guides': { title: 'Guides', content: '' }
        };

        await act(async () => {
            root.render(
                <WikiSidebar
                    tree={tree}
                    projectUrl="/mod/project"
                    currentSlug="guides"
                    onNavigate={onNavigate}
                    pageCache={emptyCache}
                />
            );
        });

        const guidesButton = [...container.querySelectorAll('button')].find((node) => node.textContent === 'Guides');
        expect(guidesButton).toBeUndefined();

        const guidesDiv = [...container.querySelectorAll('div')].find((node) => node.textContent === 'Guides');
        expect(guidesDiv).not.toBeNull();

        const collapseButton = container.querySelector('button[aria-label="Collapse Guides"]');
        expect(collapseButton).not.toBeNull();
    });

    it('does not let category pages navigate if they are missing from the cache entirely', async () => {
        const onNavigate = vi.fn();

        await act(async () => {
            root.render(
                <WikiSidebar
                    tree={tree}
                    projectUrl="/mod/project"
                    currentSlug="guides"
                    onNavigate={onNavigate}
                    pageCache={{}}
                />
            );
        });

        const guidesButton = [...container.querySelectorAll('button')].find((node) => node.textContent === 'Guides');
        expect(guidesButton).toBeUndefined();
    });

    it('lets category pages navigate if they have non-empty content', async () => {
        const onNavigate = vi.fn();
        const pageCache = {
            'guides': { title: 'Guides', content: 'Some guides content' }
        };

        await act(async () => {
            root.render(
                <WikiSidebar
                    tree={tree}
                    projectUrl="/mod/project"
                    currentSlug="guides"
                    onNavigate={onNavigate}
                    pageCache={pageCache}
                />
            );
        });

        const guidesButton = [...container.querySelectorAll('button')].find((node) => node.textContent === 'Guides');
        expect(guidesButton).not.toBeNull();

        await act(async () => {
            guidesButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(onNavigate).toHaveBeenCalledWith('guides');
    });
});
