import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SourceInspector } from '@/modules/admin/views/SourceInspector';
import { adminClient } from '@/modules/admin/api/adminClient';

vi.mock('@/modules/admin/api/adminClient', () => ({ adminClient: { getFileContent: vi.fn(), scanVersion: vi.fn() } }));
vi.mock('@/components/ui/ModalPortal', () => ({ ModalPortal: ({ children }: any) => children }));

const props = { modId: 'project', versionId: 'version-id', version: '1.0', structure: ['a.txt', 'b.txt', 'Example.class'], onClose: vi.fn() };
describe('SourceInspector evidence display', () => {
    let container: HTMLDivElement;
    let root: Root;
    beforeEach(() => {
        container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container);
        vi.clearAllMocks();
    });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    it('never displays a late response under a different selected filename', async () => {
        let resolveA!: (value: string) => void;
        let resolveB!: (value: string) => void;
        vi.mocked(adminClient.getFileContent).mockImplementation((_id, _version, path) => new Promise(resolve => {
            if (path === 'a.txt') resolveA = resolve; else resolveB = resolve;
        }));
        await act(async () => root.render(<SourceInspector {...props} initialFile="a.txt" />));
        await act(async () => root.render(<SourceInspector {...props} initialFile="b.txt" />));
        await act(async () => resolveB('Current file evidence'));
        await act(async () => resolveA('Stale file evidence'));
        expect(container.querySelector('code')?.textContent).toBe('Current file evidence');
        expect(container.textContent).not.toContain('Stale file evidence');
    });
    it('maps source line references to bytecode markers instead of text line numbers', async () => {
        vi.mocked(adminClient.getFileContent).mockResolvedValue('// JVM bytecode of the uploaded class.\nMETHOD\n  LINENUMBER 42 L0\n  RETURN');
        await act(async () => root.render(<SourceInspector {...props} initialFile="Example.class" initialLine={42} />));
        const highlighted = [...container.querySelectorAll('div.text-yellow-500')].map(node => node.textContent);
        expect(highlighted).toEqual(['3']);
    });
});
