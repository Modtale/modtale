import React, { act, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { JamBuilder } from '@/modules/jam/components/JamBuilder';
import { api } from '@/utils/api';
import type { Modjam, User } from '@/types';

vi.mock('@/components/ui/MarkdownRenderer', () => ({ MarkdownRenderer: ({ content }: any) => <p>{content}</p> }));
vi.mock('@/components/ui/OptimizedImage', () => ({ OptimizedImage: ({ alt }: any) => <img alt={alt} /> }));
const initial: Modjam = { id: 'jam-1', slug: 'summer-jam', title: 'Summer Jam', description: 'Build with us.', hostId: 'host', hostName: 'Host', status: 'DRAFT', participantIds: [], categories: [], startDate: '', endDate: '', votingEndDate: '', createdAt: '', allowPublicVoting: true, allowConcurrentVoting: false, showResultsBeforeVotingEnds: false, restrictions: {} };
const host = { id: 'host' } as User;
const setInput = (element: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement, value: string) => {
    const prototype = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype : element instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype, 'value')!.set!.call(element, value);
    element.dispatchEvent(new Event(element instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
};
const Harness = ({ tab = 'details', user = host, data = initial, save = async () => true }: any) => {
    const [metadata, setMetadata] = useState(data), [activeTab, setTab] = useState(tab);
    return <><JamBuilder metaData={metadata} setMetaData={setMetadata} activeTab={activeTab} setActiveTab={setTab} currentUser={user} handleSave={save} onBack={vi.fn()} onPublish={vi.fn()} isLoading={false} /><output data-testid="metadata">{JSON.stringify(metadata)}</output></>;
};

describe('jam builder interactions', () => {
    let root: Root, container: HTMLDivElement;
    beforeEach(() => {
        container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container);
        vi.spyOn(api, 'get').mockResolvedValue({ data: { allVersions: ['v3', 'v2', 'v1'] } });
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    const render = async (props: any = {}) => { await act(async () => root.render(<MemoryRouter><Harness {...props} /></MemoryRouter>)); };
    const metadata = () => JSON.parse(container.querySelector('[data-testid="metadata"]')!.textContent!);

    it('limits a rules-only organizer to the rules tab and disables title/media editing', async () => {
        await render({ user: { id: 'helper' }, data: { ...initial, organizerMembers: [{ userId: 'helper', roleId: 'rules' }], organizerRoles: [{ id: 'rules', name: 'Rules editor', color: '#3b82f6', permissions: ['EDIT_RULES'] }] } });
        expect(container.textContent).toContain('Jam Rules');
        expect(container.textContent).not.toContain('Appearance');
        expect(container.textContent).not.toContain('Organizers');
        expect((container.querySelector('[aria-label="Edit jam title"]') as HTMLButtonElement).disabled).toBe(true);
        expect(Array.from(container.querySelectorAll('input[type="file"]')).every(element => (element as HTMLInputElement).disabled)).toBe(true);
    });

    it('keeps portal multiselect options mounted through mousedown and applies their click', async () => {
        await render({ tab: 'restrictions' });
        await act(async () => (container.querySelector('[aria-label="Any Type"]') as HTMLButtonElement).click());
        const option = Array.from(document.querySelectorAll('button')).find(button => button.textContent === 'Plugin')!;
        await act(async () => option.dispatchEvent(new MouseEvent('mousedown', { bubbles: true })));
        expect(option.isConnected).toBe(true);
        await act(async () => option.click());
        expect(metadata().restrictions.allowedClassifications).toEqual(['PLUGIN']);
    });

    it('uses newest-first catalogue order for inclusive ranges and clears exact-mode data', async () => {
        await render({ tab: 'restrictions', data: { ...initial, restrictions: { allowedGameVersions: ['v2'] } } });
        await act(async () => setInput(container.querySelector('#jam-version-mode')!, 'range'));
        expect(metadata().restrictions.allowedGameVersions).toEqual([]);
        await act(async () => setInput(container.querySelector('[aria-label="First game version"]')!, 'v1'));
        await act(async () => setInput(container.querySelector('[aria-label="Last game version"]')!, 'v3'));
        expect(container.querySelector('[role="alert"]')).toBeNull();
        await act(async () => setInput(container.querySelector('[aria-label="First game version"]')!, 'v3'));
        await act(async () => setInput(container.querySelector('[aria-label="Last game version"]')!, 'v1'));
        expect(container.querySelector('[role="alert"]')?.textContent).toContain('first version no later');
    });

    it('does not clear edits made while an earlier save is pending', async () => {
        let resolve!: (value: boolean) => void;
        const save = vi.fn(() => new Promise<boolean>(done => { resolve = done; }));
        await render({ save });
        await act(async () => setInput(container.querySelector('[aria-label="Jam description"]')!, 'First edit'));
        const saveButton = container.querySelector('[aria-label="Save draft"]') as HTMLButtonElement;
        await act(async () => { saveButton.click(); saveButton.click(); });
        expect(save).toHaveBeenCalledOnce();
        await act(async () => setInput(container.querySelector('[aria-label="Jam description"]')!, 'Newer edit'));
        await act(async () => resolve(true));
        expect(metadata().description).toBe('Newer edit');
        expect(container.textContent).toContain('Unsaved changes');
        expect((container.querySelector('[aria-label="Save draft"]') as HTMLButtonElement).disabled).toBe(false);
    });

    it('reports a rejected save inline and preserves unsaved data', async () => {
        await render({ save: async () => { throw new Error('Save unavailable'); } });
        await act(async () => setInput(container.querySelector('[aria-label="Jam description"]')!, 'Keep this edit'));
        await act(async () => (container.querySelector('[aria-label="Save draft"]') as HTMLButtonElement).click());
        expect(container.querySelector('[role="alert"]')?.textContent).toContain('Save unavailable');
        expect(metadata().description).toBe('Keep this edit');
    });
});
