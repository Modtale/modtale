import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { useScrollLock } from '@/hooks/useScrollLock';

const Harness = ({ lock }: { lock: boolean }) => {
    useScrollLock(lock);
    return null;
};

describe('useScrollLock', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        document.body.style.overflow = '';
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        document.body.style.overflow = '';
    });

    it('locks and unlocks document scrolling as the hook state changes', async () => {
        await act(async () => {
            root.render(<Harness lock={true} />);
        });
        expect(document.body.style.overflow).toBe('hidden');

        await act(async () => {
            root.render(<Harness lock={false} />);
        });
        expect(document.body.style.overflow).toBe('');
    });

    it('keeps the body locked until the last consumer releases it', async () => {
        const renderLocks = (count: number) => (
            <>
                {Array.from({ length: count }, (_, index) => (
                    <Harness key={index} lock={true} />
                ))}
            </>
        );

        await act(async () => {
            root.render(renderLocks(2));
        });
        expect(document.body.style.overflow).toBe('hidden');

        await act(async () => {
            root.render(renderLocks(1));
        });
        expect(document.body.style.overflow).toBe('hidden');

        await act(async () => {
            root.render(renderLocks(0));
        });
        expect(document.body.style.overflow).toBe('');
    });
});
