import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AnimatedThemeToggler } from '@/components/ui/AnimatedThemeToggler';

describe('AnimatedThemeToggler', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        vi.stubGlobal('localStorage', { setItem: vi.fn() });
        document.documentElement.classList.add('dark');
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
        document.documentElement.classList.remove('dark');
        vi.unstubAllGlobals();
        Reflect.deleteProperty(document, 'startViewTransition');
        Reflect.deleteProperty(document.documentElement, 'animate');
    });

    it.each([
        { device: 'mobile', viewport: 390, left: 310, top: 520, moves: true },
        { device: 'desktop', viewport: 1440, left: 1140, top: 30, moves: false },
    ])('keeps the $device animation centered on the clicked button', async ({ viewport, left, top, moves }) => {
        vi.stubGlobal('innerWidth', viewport);
        const onToggle = vi.fn();
        const animate = vi.fn();
        Object.defineProperty(document.documentElement, 'animate', { configurable: true, value: animate });
        await act(async () => root.render(<AnimatedThemeToggler onToggle={onToggle} />));
        const button = container.querySelector('button')!;
        const bounds = vi.spyOn(button, 'getBoundingClientRect').mockReturnValue({
            left, top, width: 36, height: 36,
        } as DOMRect);
        Object.defineProperty(document, 'startViewTransition', {
            configurable: true,
            value: (update: () => void) => {
                update();
                if (moves) bounds.mockReturnValue({ left: 0, top: 0, width: 0, height: 0 } as DOMRect);
                return { ready: Promise.resolve() };
            },
        });

        await act(async () => button.click());

        const radius = Math.hypot(Math.max(left, viewport - left), Math.max(top, window.innerHeight - top));
        expect(animate).toHaveBeenCalledWith({ clipPath: [
            `circle(0px at ${left + 18}px ${top + 18}px)`,
            `circle(${radius}px at ${left + 18}px ${top + 18}px)`,
        ] }, { duration: 400, easing: 'ease-in-out', pseudoElement: '::view-transition-new(root)' });
        expect(onToggle).toHaveBeenCalledOnce();
        expect(document.documentElement.classList.contains('dark')).toBe(false);
        expect(localStorage.setItem).toHaveBeenCalledWith('modtale-theme', 'light');
    });
});
