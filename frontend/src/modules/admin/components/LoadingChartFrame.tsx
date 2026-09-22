import { useLayoutEffect, useRef, type ReactNode } from 'react';

/** Preserve the chart's real grid, axis gutters and legend wrapping inside SkeletonSurface. */
export function LoadingChartFrame({ pending, className, children }: {
    pending: boolean; className: string; children: ReactNode;
}) {
    const root = useRef<HTMLDivElement>(null);
    useLayoutEffect(() => {
        if (!pending) return;
        const charts = Array.from(root.current?.querySelectorAll('svg') || []);
        const plots = Array.from(root.current?.querySelectorAll<SVGElement>('svg g[clip-path]') || []);
        charts.forEach(chart => chart.setAttribute('data-skeleton-keep', ''));
        plots.forEach(plot => { plot.style.visibility = 'hidden'; });
        return () => {
            charts.forEach(chart => chart.removeAttribute('data-skeleton-keep'));
            plots.forEach(plot => { plot.style.removeProperty('visibility'); });
        };
    }, [pending, children]);
    return <div ref={root} className={className}>{children}</div>;
}
