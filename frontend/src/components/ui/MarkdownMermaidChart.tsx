import React, { useEffect, useMemo, useState } from 'react';
import mermaid from 'mermaid';

export const MermaidChart: React.FC<{ chart: string }> = ({ chart }) => {
    const [svg, setSvg] = useState<string>('');
    const [renderError, setRenderError] = useState(false);
    const containerRef = React.useRef<HTMLDivElement>(null);

    const id = useMemo(() => `mermaid-${Math.random().toString(36).slice(2, 11)}`, []);

    useEffect(() => {
        const isDark = document.documentElement.classList.contains('dark');

        mermaid.initialize({
            startOnLoad: false,
            theme: isDark ? 'dark' : 'default',
            securityLevel: 'loose',
            fontFamily: 'inherit'
        });

        let isMounted = true;

        const renderChart = async () => {
            try {
                const { svg: renderedSvg } = await mermaid.render(id, chart);
                if (isMounted) {
                    setSvg(renderedSvg);
                    setRenderError(false);
                }
            } catch (e) {
                console.error('Mermaid rendering failed', e);
                if (isMounted) {
                    setSvg('');
                    setRenderError(true);
                }
            }
        };

        renderChart();

        return () => {
            isMounted = false;
        };
    }, [chart, id]);

    useEffect(() => {
        const container = containerRef.current;
        if (!container) return;

        while (container.firstChild) {
            container.removeChild(container.firstChild);
        }

        if (!svg || renderError) return;

        try {
            const parser = new DOMParser();
            const parsed = parser.parseFromString(svg, 'image/svg+xml');
            const parseError = parsed.querySelector('parsererror');
            const parsedSvg = parsed.documentElement;

            if (parseError || !parsedSvg || parsedSvg.tagName.toLowerCase() !== 'svg') {
                setRenderError(true);
                return;
            }

            container.appendChild(document.importNode(parsedSvg, true));
        } catch (e) {
            console.error('Mermaid SVG injection failed', e);
            setRenderError(true);
        }
    }, [svg, renderError]);

    if (!svg) {
        if (renderError) {
            return (
                <div className="my-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                    Failed to render diagram
                </div>
            );
        }
        return <div className="animate-pulse h-32 bg-slate-100 dark:bg-slate-800 rounded-xl my-4" />;
    }

    return (
        <div
            ref={containerRef}
            className="my-6 p-4 bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-white/10 shadow-sm overflow-x-auto flex justify-center mermaid-container"
        />
    );
};
