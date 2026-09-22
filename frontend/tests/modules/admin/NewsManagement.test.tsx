import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { NewsManagement } from '@/modules/admin/components/NewsManagement';
import { newsClient, type NewsDraft } from '@/modules/news/api/newsClient';
vi.mock('@/modules/admin/components/NewsRichEditor', () => ({ NewsRichEditor: ({ value, onChange }: any) => <button onClick={() => onChange('<p>Revised draft.</p>')}>Edit body: {value}</button> }));
vi.mock('@/modules/news/api/newsClient', () => ({ newsClient: { drafts: vi.fn(), save: vi.fn(), publish: vi.fn(), unpublish: vi.fn() } }));
const draft: NewsDraft = { slug: 'example', version: 1, published: null, draft: { title: 'An update', description: 'Summary', author: 'Team', excerpt: '', tags: [], heroImage: '/cover.png', heroAlt: 'Cover', body: '<p>Original</p>' } };
let root: Root; let container: HTMLDivElement;
const click = async (text: string) => act(async () => { const button = [...document.querySelectorAll('button')].find(b => b.textContent?.includes(text)); expect(button).toBeTruthy(); button!.click(); });
beforeEach(async () => {
    vi.clearAllMocks();
    vi.mocked(newsClient.drafts).mockResolvedValue([structuredClone(draft)]);
    vi.mocked(newsClient.save).mockImplementation(async post => ({ ...post, version: 2 }));
    container = document.createElement('div'); document.body.append(container); root = createRoot(container);
    await act(async () => root.render(<NewsManagement userId="test" />));
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
describe('News admin workflow', () => {
    it('saves a draft without publishing and preserves editing on API failure', async () => {
        await click('An update'); await click('Edit body'); await click('Save draft');
        expect(newsClient.save).toHaveBeenCalledWith(expect.objectContaining({ draft: expect.objectContaining({ body: '<p>Revised draft.</p>' }) }));
        expect(newsClient.publish).not.toHaveBeenCalled();
        vi.mocked(newsClient.save).mockRejectedValueOnce(new Error('Network failed'));
        await click('Save draft');
        expect(container.querySelector('[role="alert"]')).not.toBeNull();
        expect(container.textContent).toContain('Revised draft.');
    });
    it('requires an explicit publish confirmation before publishing the saved revision', async () => {
        vi.mocked(newsClient.publish).mockImplementation(async post => ({ ...post, published: post.draft, version: 3 }));
        await click('An update'); await click('Publish');
        expect(newsClient.publish).not.toHaveBeenCalled();
        const buttons = [...document.querySelectorAll('button')].filter(b => b.textContent?.trim() === 'Publish');
        await act(async () => buttons.find(b => !container.contains(b))!.click());
        expect(newsClient.publish).toHaveBeenCalledWith(expect.objectContaining({ version: 2 }));
        expect(container.textContent).toContain('Article published.');
    });
});
