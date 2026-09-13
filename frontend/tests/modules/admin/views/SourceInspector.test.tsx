import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SourceInspector } from '@/modules/admin/views/SourceInspector';
import { adminClient } from '@/modules/admin/api/adminClient';

vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { getFileWindow: vi.fn(), scanVersion: vi.fn() } }));
vi.mock('@/components/ui/ModalPortal', () => ({ ModalPortal: ({ children }: any) => children }));

const window = (content: string, format = 'TEXT_RESOURCE', start = 0, totalCharacters = start + content.length, identity = 'a'.repeat(64)) => ({ identity, content, format, start, end: start + content.length, totalCharacters, firstLine: 1, lineMatched: true, representationComplete: true, gaps: [] });
const props = { reviewToken: 'snapshot', modId: 'project', versionId: 'version-id', version: '1.0', structure: ['a.txt', 'b.txt', 'Example.class'], onClose: vi.fn() };
describe('SourceInspector evidence display', () => {
    let container: HTMLDivElement;
    let root: Root;
    beforeEach(() => {
        container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container);
        vi.clearAllMocks();
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    const click = async (label: string) => act(async () => [...container.querySelectorAll('button')].find(button => button.textContent === label)!.click());
    it('bounds a 20,000-file inventory while allowing the final page to be inspected', async () => {
        const structure = Array.from({ length: 20_000 }, (_, i) => `file-${String(i).padStart(5, '0')}.txt`);
        vi.mocked(adminClient.getFileWindow).mockResolvedValue(window('last file'));
        await act(async () => root.render(<SourceInspector {...props} structure={structure} />));
        expect(container.querySelectorAll('svg').length).toBeLessThan(500);
        await click('Last files');
        const last = container.querySelector<HTMLButtonElement>('button[title="file-19999.txt"]');
        expect(last).not.toBeNull();
        await act(async () => last!.click());
        expect(adminClient.getFileWindow).toHaveBeenLastCalledWith('project', '1.0', 'file-19999.txt', 'snapshot', 0, undefined, 0);
    });
    it('provides native keyboard controls for folders and files', async () => {
        vi.mocked(adminClient.getFileWindow).mockResolvedValue(window('nested'));
        await act(async () => root.render(<SourceInspector {...props} structure={['folder/nested.txt']} />));
        const folder = container.querySelector<HTMLButtonElement>('button[aria-expanded="false"]');
        expect(folder).not.toBeNull();
        await act(async () => folder!.click());
        expect(folder!.getAttribute('aria-expanded')).toBe('true');
        const file = container.querySelector<HTMLButtonElement>('button[title="folder/nested.txt"]');
        expect(file).not.toBeNull();
        await act(async () => file!.click());
        expect(container.querySelector('code')?.textContent).toBe('nested');
    });
    it('opens a deeply nested finding without recursive DOM or losing the selected file', async () => {
        const path = `${'a/'.repeat(3500)}tail.txt`;
        vi.mocked(adminClient.getFileWindow).mockResolvedValue(window('deep evidence'));
        await act(async () => root.render(<SourceInspector {...props} structure={[path]} initialFile={path} />));
        expect(container.querySelectorAll('svg').length).toBeLessThan(500);
        expect(container.querySelector('button[aria-current="true"]')?.getAttribute('title')).toBe(path);
        expect(container.querySelector('code')?.textContent).toBe('deep evidence');
    });
    it('searches all inventory pages and resets pagination when the search changes', async () => {
        const structure = Array.from({ length: 2000 }, (_, i) => `item-${String(i).padStart(4, '0')}.txt`);
        await act(async () => root.render(<SourceInspector {...props} structure={structure} />));
        await click('Last files');
        const input = container.querySelector('input')!;
        const search = async (value: string) => act(async () => {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value);
            input.dispatchEvent(new Event('input', { bubbles: true }));
        });
        await search('item-0000');
        expect(container.querySelector('button[title="item-0000.txt"]')).not.toBeNull();
        expect(container.querySelector('nav')?.querySelectorAll('button')).toHaveLength(1);
        await search('item-');
        expect(container.querySelector('nav')?.querySelectorAll('button')).toHaveLength(200);
        expect(container.textContent).toContain('Entries 1–200 of 2000');
        await search('missing');
        expect(container.textContent).toContain('No files found');
    });
    it('navigates exact returned offsets and keeps the representation identity', async () => {
        vi.mocked(adminClient.getFileWindow).mockResolvedValueOnce(window('first', 'TEXT_RESOURCE', 0, 10))
            .mockResolvedValueOnce(window('later', 'TEXT_RESOURCE', 5, 10))
            .mockResolvedValueOnce(window('first', 'TEXT_RESOURCE', 0, 10));
        await act(async () => root.render(<SourceInspector {...props} initialFile="a.txt" />));
        await click('Next section');
        expect(adminClient.getFileWindow).toHaveBeenLastCalledWith('project', '1.0', 'a.txt', 'snapshot', 5, 'a'.repeat(64), 0);
        expect(container.querySelector('code')?.textContent).toBe('later');
        await click('Previous section');
        expect(adminClient.getFileWindow).toHaveBeenLastCalledWith('project', '1.0', 'a.txt', 'snapshot', 0, 'a'.repeat(64), 0);
        expect(container.querySelector('code')?.textContent).toBe('first');
    });
    it('refuses a changed representation during continuation', async () => {
        vi.mocked(adminClient.getFileWindow).mockResolvedValueOnce(window('first', 'TEXT_RESOURCE', 0, 10))
            .mockResolvedValueOnce(window('wrong', 'TEXT_RESOURCE', 5, 10, 'b'.repeat(64)));
        await act(async () => root.render(<SourceInspector {...props} initialFile="a.txt" />));
        await click('Next section');
        expect(container.querySelector('code')?.textContent).not.toBe('wrong');
        expect(container.textContent).toContain('inspection changed');
    });
    it('discards a pending continuation when the opened review changes', async () => {
        let stale!: (value: ReturnType<typeof window>) => void;
        vi.mocked(adminClient.getFileWindow).mockResolvedValueOnce(window('first', 'TEXT_RESOURCE', 0, 10))
            .mockReturnValueOnce(new Promise(resolve => { stale = resolve; }))
            .mockResolvedValueOnce(window('new'));
        await act(async () => root.render(<SourceInspector {...props} initialFile="a.txt" />));
        await click('Next section');
        await act(async () => root.render(<SourceInspector {...props} reviewToken="new-review" initialFile="a.txt" />));
        await act(async () => stale(window('stale', 'TEXT_RESOURCE', 5, 10)));
        expect(container.querySelector('code')?.textContent).toBe('new');
        expect([...container.querySelectorAll('button')].find(button => button.textContent === 'Previous section')?.disabled).toBe(true);
    });
    it('never displays a late response under a different selected filename', async () => {
        let resolveA!: (value: ReturnType<typeof window>) => void;
        let resolveB!: (value: ReturnType<typeof window>) => void;
        vi.mocked(adminClient.getFileWindow).mockImplementation((_id, _version, path) => new Promise(resolve => {
            if (path === 'a.txt') resolveA = resolve; else resolveB = resolve;
        }));
        await act(async () => root.render(<SourceInspector {...props} initialFile="a.txt" />));
        await act(async () => root.render(<SourceInspector {...props} initialFile="b.txt" />));
        await act(async () => resolveB(window('Current file evidence')));
        await act(async () => resolveA(window('Stale file evidence')));
        expect(container.querySelector('code')?.textContent).toBe('Current file evidence');
        expect(container.textContent).not.toContain('Stale file evidence');
    });
    it('exposes rescan only with permission and targets the inspected version', async () => {
        await act(async () => root.render(<SourceInspector {...props} />));
        expect(container.querySelector('button[title="Rescan File"]')).toBeNull();
        await act(async () => root.render(<SourceInspector {...props} version="0.9" versionId="older-version" canRescan />));
        await act(async () => container.querySelector<HTMLButtonElement>('button[title="Rescan File"]')!.click());
        expect(adminClient.scanVersion).toHaveBeenCalledWith('project', 'older-version');
    });
    it('maps source line references to bytecode markers instead of text line numbers', async () => {
        vi.mocked(adminClient.getFileWindow).mockResolvedValue(window('// JVM bytecode of the uploaded class.\nMETHOD\n  LINENUMBER 42 L0\n  RETURN', 'JVM_BYTECODE'));
        await act(async () => root.render(<SourceInspector {...props} initialFile="Example.class" initialLine={42} />));
        const highlighted = [...container.querySelectorAll('div.text-yellow-500')].map(node => node.textContent);
        expect(highlighted).toEqual(['3']);
    });
    it('binds file requests and discards responses after the review token changes', async () => {
        let stale!: (value: ReturnType<typeof window>) => void;
        vi.mocked(adminClient.getFileWindow).mockReturnValueOnce(new Promise(done => { stale = done; }));
        await act(async () => root.render(<SourceInspector {...props} initialFile="a.txt" />));
        expect(adminClient.getFileWindow).toHaveBeenLastCalledWith('project', '1.0', 'a.txt', 'snapshot', 0, undefined, 0);
        vi.mocked(adminClient.getFileWindow).mockResolvedValueOnce(window('New review evidence'));
        await act(async () => root.render(<SourceInspector {...props} reviewToken="new" initialFile="a.txt" />));
        expect(adminClient.getFileWindow).toHaveBeenLastCalledWith('project', '1.0', 'a.txt', 'new', 0, undefined, 0);
        await act(async () => stale(window('Old review evidence')));
        expect(container.querySelector('code')?.textContent).toBe('New review evidence');
    });
});
