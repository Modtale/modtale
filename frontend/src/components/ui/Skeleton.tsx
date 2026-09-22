import { useLayoutEffect, useRef, useState, type ReactNode } from 'react';

/** Decorative placeholders share one restrained treatment in both themes. */
export function Skeleton({ className = '' }: { className?: string }) {
    return <div aria-hidden="true" className={`skeleton rounded-md ${className}`} />;
}

export function SkeletonGroup({ children, className = '', label = 'Loading content' }: {
    children: ReactNode; className?: string; label?: string;
}) {
    return <div role="status" aria-busy="true" className={className}>
        <span className="sr-only">{label}</span>
        <div aria-hidden="true">{children}</div>
    </div>;
}

interface SkeletonMark { x: number; y: number; width: number; height: number; radius: number }

/** Use the real view's layout and mask its ink, rather than approximating its boxes.
 * The subtree is inert and never represents loaded data to assistive technology.
 * ResizeObserver keeps the mask aligned with responsive wrapping and late fonts.
 */
export function SkeletonSurface({ children, className = '', label = 'Loading content' }: {
    children: ReactNode; className?: string; label?: string;
}) {
    const surface = useRef<HTMLDivElement>(null);
    const [marks, setMarks] = useState<SkeletonMark[]>([]);
    useLayoutEffect(() => {
        const root = surface.current;
        const content = root?.firstElementChild as HTMLElement | null;
        if (!root || !content || typeof Range.prototype.getClientRects !== 'function') return;
        let frame = 0;
        let active = true;
        const measure = () => {
            const origin = root.getBoundingClientRect();
            const next: SkeletonMark[] = [];
            const add = (rect: DOMRect, text = false, radius = 4) => {
                if (rect.width < 1 || rect.height < 1) return;
                // Clip clamped/truncated text to the surface; don't change its layout.
                const x = Math.max(rect.left, origin.left);
                const width = Math.min(rect.right, origin.right) - x;
                if (width <= 0) return;
                const height = text ? rect.height * .58 : rect.height;
                next.push({ x: x - origin.left, y: rect.top - origin.top + (rect.height - height) / 2,
                    width, height, radius });
            };
            const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
            while (walker.nextNode()) {
                const node = walker.currentNode;
                const parent = node.parentElement;
                if (!node.textContent?.trim() || !parent || parent.closest('.skeleton-surface') !== root || parent.closest('[data-skeleton-keep], .sr-only, svg, script, style, [data-skeleton-media]')) continue;
                const clip = parent.getBoundingClientRect();
                const range = document.createRange();
                range.selectNodeContents(node);
                for (const rect of range.getClientRects()) {
                    const right = Math.min(rect.right, clip.right);
                    const bottom = Math.min(rect.bottom, clip.bottom);
                    if (right > rect.left && bottom > rect.top) add(new DOMRect(rect.left, rect.top, right - rect.left, bottom - rect.top), true);
                }
            }
            content.querySelectorAll<HTMLElement>('img, svg, input:not([type="hidden"]), textarea, [data-skeleton-media]').forEach(node => {
                if (node.closest('.skeleton-surface') !== root || node.closest('[data-skeleton-keep]') || node.parentElement?.closest('[data-skeleton-media]')) return;
                const rect = node.getBoundingClientRect();
                add(rect, false, node.tagName.toLowerCase() === 'svg' ? 3 : 8);
            });
            if (active) setMarks(next);
        };
        const schedule = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(measure); };
        measure();
        const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(schedule);
        observer?.observe(root);
        observer?.observe(content);
        content.querySelectorAll('img, [data-skeleton-media]').forEach(node => observer?.observe(node));
        document.fonts?.ready.then(() => { if (active) schedule(); });
        window.addEventListener('resize', schedule);
        return () => { active = false; cancelAnimationFrame(frame); observer?.disconnect(); window.removeEventListener('resize', schedule); };
    }, [children]);
    return <div ref={surface} className={`skeleton-surface ${className}`} role="status" aria-label={label} aria-busy="true">
        <div className="skeleton-layout" aria-hidden="true" inert>{children}</div>
        <div aria-hidden="true" className="skeleton-marks">
            {marks.map((mark, index) => <span key={index} className="skeleton" style={{ left: mark.x, top: mark.y, width: mark.width, height: mark.height, borderRadius: mark.radius }} />)}
        </div>
    </div>;
}
