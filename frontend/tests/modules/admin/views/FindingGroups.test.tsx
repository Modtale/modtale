import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FindingGroups } from '@/modules/admin/views/FindingGroups';
import type { ScanIssue } from '@/types';

const issue = (type: string, overrides: Partial<ScanIssue> = {}): ScanIssue => ({ type, severity: 'LOW', description: 'Evidence', filePath: 'library/Type.class', lineStart: 1, lineEnd: 1, ...overrides });
describe('Finding groups', () => {
    let container: HTMLDivElement; let root: Root;
    beforeEach(() => { container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); });
    afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
    it('counts occurrences and distinct paths separately and preserves overlapping review states', async () => {
        const select = vi.fn();
        await act(async () => root.render(<FindingGroups issues={[
            issue('Library', { knownIssue: true }), issue('Library', { knownIssue: true, reviewCadence: 'ALWAYS' }),
            issue('Library', { filePath: 'nested.jar!/library/Type.class' }), issue('Execution', { severity: 'HIGH' })
        ]} selected="Library" onSelect={select} />));
        const rows = [...container.querySelectorAll('tbody tr')];
        expect(rows[0].textContent).toContain('Execution');
        expect([...rows[1].querySelectorAll('th,td')].map(cell => cell.textContent)).toEqual(['Library', 'LOW', '3', '2', '2', '1', 'Show']);
        expect(container.textContent).toContain('counts can overlap');
        const button = container.querySelector<HTMLButtonElement>('button[aria-label="Show Library findings"]')!;
        expect(button.getAttribute('aria-pressed')).toBe('true');
        await act(async () => button.click()); expect(select).toHaveBeenCalledWith('Library');
        await act(async () => [...container.querySelectorAll('button')].find(b => b.textContent === 'Show all types')!.click());
        expect(select).toHaveBeenLastCalledWith(null);
    });
    it('bounds groups without dropping later types or interpreting hostile names as markup', async () => {
        const issues = Array.from({ length: 26 }, (_, i) => issue(`Type${String(i).padStart(2, '0')}`));
        issues.push(issue('<img src=x onerror=alert(1)>', { severity: 'CRITICAL' }));
        await act(async () => root.render(<FindingGroups issues={issues} selected={null} onSelect={vi.fn()} />));
        expect(container.querySelectorAll('tbody tr')).toHaveLength(12);
        expect(container.querySelector('img')).toBeNull();
        expect(container.querySelector('tbody tr')?.textContent).toContain('<img src=x onerror=alert(1)>');
        const next = () => [...container.querySelectorAll('button')].find(b => b.textContent === 'Next groups')!;
        await act(async () => next().click()); expect(container.querySelectorAll('tbody tr')).toHaveLength(12);
        await act(async () => next().click()); expect(container.querySelectorAll('tbody tr')).toHaveLength(3);
        expect(next().disabled).toBe(true); expect(container.textContent).toContain('Type25');
    });
    it('keeps an unrecognized severity visible instead of presenting its group as low severity', async () => {
        await act(async () => root.render(<FindingGroups issues={[issue('__proto__', { severity: '__proto__' }), issue('__proto__'), issue('Critical', { severity: 'CRITICAL' })]} selected={null} onSelect={vi.fn()} />));
        expect(container.querySelector('tbody tr')?.textContent).toContain('__proto__');
        expect(container.querySelector('tbody tr td')?.textContent).toBe('__proto__');
    });
});
