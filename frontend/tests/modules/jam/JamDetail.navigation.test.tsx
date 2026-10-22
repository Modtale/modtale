import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { JamDetail } from '@/modules/jam/views/JamDetail';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';

vi.mock('@/modules/jam/components/JamBuilder', () => ({ JamBuilder: (props: any) => {
    const [error, setError] = React.useState('');
    return <section data-testid="builder"><input aria-label="test slug" value={props.metaData.slug} onChange={event => props.setMetaData((data: any) => ({ ...data, slug: event.target.value }))} /><button onClick={() => props.handleSave().catch((failure: Error) => setError(failure.message))}>Save test</button><button onClick={() => props.onPublish().catch((failure: Error) => setError(failure.message))}>Publish test</button><p role="alert">{error}</p></section>;
} }));
vi.mock('@/components/ui/OptimizedImage', () => ({ OptimizedImage: ({ alt }: any) => <img alt={alt} /> }));
vi.mock('@/components/ui/MarkdownRenderer', () => ({ MarkdownRenderer: ({ content }: any) => <p>{content}</p> }));
const user = { id: 'host-1', username: 'Host' } as User;
const jam = (slug = 'summer-jam'): Modjam => ({ id: 'jam-1', slug, title: 'Summer Jam', description: 'Build with us.', hostId: 'host-1', hostName: 'Host', status: 'DRAFT', participantIds: [], categories: [], startDate: '', endDate: '', votingEndDate: '', createdAt: '', allowPublicVoting: true, allowConcurrentVoting: false, showResultsBeforeVotingEnds: false });
const input = (element: HTMLInputElement, value: string) => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(element, value); element.dispatchEvent(new Event('input', { bubbles: true })); };
const Probe = () => { const location = useLocation(); const navigate = useNavigate(); return <aside data-path={location.pathname}><button onClick={() => navigate('/jam/newer-jam/overview')}>Switch jam</button></aside>; };

describe('jam builder navigation', () => {
    let root: Root, container: HTMLDivElement, serverJam: Modjam;
    beforeEach(() => {
        container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); serverJam = jam();
        vi.spyOn(api, 'get').mockImplementation(async (url: string) => ({ data: url.endsWith('/submissions') ? [] : url.includes('/projects') ? { content: [] } : serverJam }));
        vi.spyOn(api, 'put').mockImplementation(async (_url: string, data: any) => { serverJam = { ...serverJam, ...data }; return { data: serverJam }; });
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    const render = async (path = '/jam/summer-jam/edit') => { await act(async () => root.render(<MemoryRouter initialEntries={[path]}><Probe /><Routes><Route path="/jam/:slug/*" element={<JamDetail currentUser={user} />} /></Routes></MemoryRouter>)); };
    const button = (label: string) => Array.from(container.querySelectorAll('button')).find(item => item.textContent === label)!;

    it('replaces the edit route with the canonical slug after a save', async () => {
        await render();
        await act(async () => input(container.querySelector('[aria-label="test slug"]')!, 'renamed-jam'));
        await act(async () => button('Save test').click());
        expect(container.querySelector('[data-path]')?.getAttribute('data-path')).toBe('/jam/renamed-jam/edit');
        expect((container.querySelector('[aria-label="test slug"]') as HTMLInputElement).value).toBe('renamed-jam');
    });
    it('stays in the builder after a failed save', async () => {
        vi.mocked(api.put).mockRejectedValueOnce(new Error('Save rejected'));
        await render();
        await act(async () => button('Save test').click());
        expect(container.querySelector('[data-path]')?.getAttribute('data-path')).toBe('/jam/summer-jam/edit');
        expect(container.querySelector('[role="alert"]')?.textContent).toBe('Save rejected');
    });
    it('publishes drafts using the real phase status and then opens the overview', async () => {
        await render();
        await act(async () => button('Publish test').click());
        expect(api.put).toHaveBeenCalledWith('/modjams/jam-1', expect.objectContaining({ status: 'UPCOMING' }));
        expect(container.querySelector('[data-path]')?.getAttribute('data-path')).toBe('/jam/summer-jam/overview');
    });
    it('ignores an older pending fetch after navigation to a newer jam', async () => {
        let resolve!: (value: any) => void;
        vi.mocked(api.get).mockImplementation((url: string) => url === '/modjams/summer-jam' ? new Promise(done => { resolve = done; }) : Promise.resolve({ data: url.endsWith('/submissions') ? [] : { ...jam('newer-jam'), id: 'jam-2', title: 'Newer Jam' } }));
        await render('/jam/summer-jam/overview');
        await act(async () => button('Switch jam').click());
        await act(async () => resolve({ data: jam() }));
        expect(container.querySelector('[data-path]')?.getAttribute('data-path')).toBe('/jam/newer-jam/overview');
        expect(container.textContent).toContain('Newer Jam');
        expect(container.textContent).not.toContain('Summer Jam');
    });
});
