import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { User } from '@/types';
import { CreateProject } from '@/modules/project/views/CreateProject';

const mocks = vi.hoisted(() => ({ load: vi.fn(), post: vi.fn(), get: vi.fn(), navigate: vi.fn() }));
vi.mock('@/modules/project/api/curseForgeImport', () => ({ loadCurseForgeImport: mocks.load }));
vi.mock('@/utils/api', () => ({ api: { get: mocks.get, post: mocks.post }, extractApiErrorMessage: (_: unknown, fallback: string) => fallback }));
vi.mock('react-router-dom', async importOriginal => ({ ...await importOriginal<typeof import('react-router-dom')>(), useNavigate: () => mocks.navigate }));
vi.mock('@/components/ui/MarkdownRenderer', () => ({ MarkdownRenderer: ({ content }: { content: string }) => <div>{content}</div> }));

const seed = { title: 'Imported Mod', summary: 'An imported Hytale mod.', about: '<h2>Features</h2>',
    sourceUrl: 'https://www.curseforge.com/hytale/mods/imported-mod', classification: 'PLUGIN',
    imageUrl: 'https://media.forgecdn.net/avatars/1/icon.png', warnings: [] };
let container: HTMLDivElement;
let root: Root;
const button = (text: string) => [...container.querySelectorAll('button')].find(node => node.textContent?.includes(text))!;
async function importDetails() {
    const input = container.querySelector('#curseforge-reference') as HTMLInputElement;
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, '42');
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => { container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
}

describe('CreateProject CurseForge starting point', () => {
    beforeEach(async () => {
        vi.resetAllMocks();
        mocks.get.mockResolvedValue({ data: [] });
        mocks.load.mockResolvedValue(seed);
        mocks.post.mockResolvedValue({ data: { id: 'draft-1', slug: 'imported-mod' } });
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter><CreateProject currentUser={{ id: 'user-1', username: 'Creator', emailVerified: true } as User} onNavigate={() => {}} onRefresh={() => {}} /></MemoryRouter>));
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });

    it('previews the import and creates a draft only when the user continues', async () => {
        await importDetails();
        expect(container.textContent).toContain('Review your import.');
        expect(mocks.post).not.toHaveBeenCalled();
        expect(container.querySelector('input')?.value).toBe(seed.title);
        await act(async () => button('Start Building').click());
        const payload = mocks.post.mock.calls[0][1] as FormData;
        expect(Object.fromEntries(payload.entries())).toMatchObject({
            title: seed.title, description: seed.summary, about: seed.about, imageUrl: seed.imageUrl,
            curseForgeUrl: seed.sourceUrl, classification: 'PLUGIN', owner: 'user-1',
        });
        expect(mocks.navigate).toHaveBeenCalled();
    });

    it('lets the user retry a failed import without creating a project', async () => {
        mocks.load.mockRejectedValueOnce(new Error('CurseForge is busy. Please try again shortly.'));
        await importDetails();
        expect(container.querySelector('[role="alert"]')?.textContent).toContain('Please try again');
        expect(mocks.post).not.toHaveBeenCalled();
        await importDetails();
        expect(container.textContent).toContain('Review your import.');
    });

    it('does not carry imported description or source into the regular creation path', async () => {
        await importDetails();
        await act(async () => button('Back').click());
        await act(async () => button('Plugins').click());
        await act(async () => button('Start Building').click());
        const payload = mocks.post.mock.calls[0][1] as FormData;
        expect(payload.has('about')).toBe(false);
        expect(payload.has('curseForgeUrl')).toBe(false);
        expect(payload.has('imageUrl')).toBe(false);
    });
});
