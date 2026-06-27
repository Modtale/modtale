import React, { Suspense, lazy, useEffect, useMemo, useState } from 'react';

const MarkdownRichRenderer = lazy(() => import('./MarkdownRichRenderer').then((module) => ({ default: module.MarkdownRichRenderer })));

const richMarkdownPatterns = [
    /^```/m,
    /^~~~/m,
    /^\s{4,}\S/m,
    /^\s*>/m,
    /^\s*\d+\.\s+/m,
    /^\s*[-*]\s+\[[ xX]\]\s+/m,
    /^\s*[-*_]{3,}\s*$/m,
    /!\[[^\]]*]\([^)]+\)/,
    /^\|.+\|\s*$/m,
    /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/m,
    /<\/?[a-z][\s\S]*>/i,
    /~~[^~]+~~/,
    /\[[^\]]+]:\s+\S+/,
];

const requiresRichMarkdown = (content: string) => (
    richMarkdownPatterns.some((pattern) => pattern.test(content))
);

const renderInline = (text: string, keyPrefix: string) => {
    const parts = text.split(/(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|!\[[^\]]*]\([^)]+\)|\[[^\]]+\]\([^)]+\)|\*[^*\s][^*]*\*|_[^_\s][^_]*_)/g).filter(Boolean);

    return parts.map((part, index) => {
        const key = `${keyPrefix}-${index}`;

        if ((part.startsWith('**') && part.endsWith('**')) || (part.startsWith('__') && part.endsWith('__'))) {
            return <strong key={key}>{part.slice(2, -2)}</strong>;
        }

        if ((part.startsWith('*') && part.endsWith('*')) || (part.startsWith('_') && part.endsWith('_'))) {
            return <em key={key}>{part.slice(1, -1)}</em>;
        }

        if (part.startsWith('`') && part.endsWith('`')) {
            return (
                <code
                    key={key}
                    className="!before:hidden !after:hidden bg-slate-200/70 dark:bg-slate-800 px-1.5 py-0.5 rounded-md text-[0.85em] font-mono text-slate-800 dark:text-slate-300 border border-slate-300/50 dark:border-slate-700/50 break-words"
                >
                    {part.slice(1, -1)}
                </code>
            );
        }

        const imageMatch = part.match(/^!\[([^\]]*)]\(([^)]+)\)$/);
        if (imageMatch) {
            return <img key={key} src={imageMatch[2]} alt={imageMatch[1]} loading="lazy" className="inline-block align-middle max-w-full h-auto my-0" />;
        }

        const linkMatch = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
        if (linkMatch) {
            return (
                <a key={key} href={linkMatch[2]} target="_blank" rel="noopener noreferrer">
                    {linkMatch[1]}
                </a>
            );
        }

        return part;
    });
};

const splitTableRow = (line: string) => (
    line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim())
);

const MarkdownFallback: React.FC<{ content: string }> = ({ content }) => {
    const blocks = useMemo(() => {
        const lines = (content || '').replace(/\r\n/g, '\n').split('\n');
        const nextBlocks: React.ReactNode[] = [];
        let i = 0;

        while (i < lines.length) {
            const line = lines[i].trim();
            if (!line) {
                i += 1;
                continue;
            }

            const fence = line.match(/^(```|~~~)\s*(\w+)?\s*$/);
            if (fence) {
                const marker = fence[1];
                const lang = fence[2] || 'text';
                const code: string[] = [];
                i += 1;
                while (i < lines.length && lines[i].trim() !== marker) {
                    code.push(lines[i]);
                    i += 1;
                }
                if (i < lines.length) i += 1;
                nextBlocks.push(
                    <div key={`code-${i}`} className="relative w-full my-4 rounded-xl overflow-hidden bg-slate-900 ring-1 ring-slate-300 dark:ring-white/10 shadow-lg z-10">
                        <div className="px-4 py-1.5 bg-slate-800 border-b border-slate-950 text-xs font-sans text-slate-400 select-none">
                            {lang}
                        </div>
                        <pre className="!bg-transparent !m-0 !p-4 text-[13px] leading-relaxed overflow-x-auto">
                            <code>{code.join('\n')}</code>
                        </pre>
                    </div>
                );
                continue;
            }

            const heading = line.match(/^(#{1,3})\s+(.+)$/);
            if (heading) {
                const level = heading[1].length;
                const text = renderInline(heading[2], `heading-${i}`);
                if (level === 1) nextBlocks.push(<h1 key={`h-${i}`} className="text-4xl font-black mb-6">{text}</h1>);
                else if (level === 2) nextBlocks.push(<h2 key={`h-${i}`} className="text-2xl font-black mt-8 mb-4">{text}</h2>);
                else nextBlocks.push(<h3 key={`h-${i}`} className="text-xl font-black mt-6 mb-3">{text}</h3>);
                i += 1;
                continue;
            }

            if (/^[-*_]{3,}$/.test(line)) {
                nextBlocks.push(<hr key={`hr-${i}`} className="my-8 border-slate-200 dark:border-slate-800" />);
                i += 1;
                continue;
            }

            if (/^\s*>/.test(line)) {
                const quote: string[] = [];
                while (i < lines.length && /^\s*>/.test(lines[i].trim())) {
                    quote.push(lines[i].trim().replace(/^\s*>\s?/, ''));
                    i += 1;
                }
                nextBlocks.push(
                    <blockquote key={`quote-${i}`} className="border-l-4 border-slate-300 dark:border-slate-700 pl-4 my-4 text-slate-600 dark:text-slate-300">
                        {renderInline(quote.join(' '), `quote-${i}`)}
                    </blockquote>
                );
                continue;
            }

            if (/^\|.+\|\s*$/.test(line) && i + 1 < lines.length && /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(lines[i + 1])) {
                const headers = splitTableRow(line);
                const rows: string[][] = [];
                i += 2;
                while (i < lines.length && /^\|.+\|\s*$/.test(lines[i].trim())) {
                    rows.push(splitTableRow(lines[i]));
                    i += 1;
                }
                nextBlocks.push(
                    <div key={`table-${i}`} className="my-4 overflow-x-auto">
                        <table className="min-w-full border-collapse text-sm">
                            <thead>
                                <tr>
                                    {headers.map((header, index) => (
                                        <th key={`${header}-${index}`} className="border border-slate-300 dark:border-slate-700 px-3 py-2 text-left font-bold">
                                            {renderInline(header, `th-${i}-${index}`)}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((row, rowIndex) => (
                                    <tr key={`tr-${i}-${rowIndex}`}>
                                        {headers.map((_, cellIndex) => (
                                            <td key={`td-${i}-${rowIndex}-${cellIndex}`} className="border border-slate-300 dark:border-slate-700 px-3 py-2 align-top">
                                                {renderInline(row[cellIndex] || '', `td-${i}-${rowIndex}-${cellIndex}`)}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                );
                continue;
            }

            if (/^[-*]\s+/.test(line)) {
                const items: string[] = [];
                while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
                    items.push(lines[i].trim().replace(/^[-*]\s+/, ''));
                    i += 1;
                }
                nextBlocks.push(
                    <ul key={`ul-${i}`} className="list-disc pl-6 my-3 space-y-1.5">
                        {items.map((item, index) => (
                            <li key={`${item}-${index}`} className="my-1.5 break-words text-base">
                                {/^\[[ xX]\]\s+/.test(item) ? (
                                    <span className="inline-flex items-start gap-2">
                                        <input type="checkbox" checked={/^\[[xX]\]/.test(item)} readOnly className="mt-1" />
                                        <span>{renderInline(item.replace(/^\[[ xX]\]\s+/, ''), `li-${i}-${index}`)}</span>
                                    </span>
                                ) : renderInline(item, `li-${i}-${index}`)}
                            </li>
                        ))}
                    </ul>
                );
                continue;
            }

            if (/^\d+\.\s+/.test(line)) {
                const items: string[] = [];
                while (i < lines.length && /^\d+\.\s+/.test(lines[i].trim())) {
                    items.push(lines[i].trim().replace(/^\d+\.\s+/, ''));
                    i += 1;
                }
                nextBlocks.push(
                    <ol key={`ol-${i}`} className="list-decimal pl-6 my-3 space-y-1.5">
                        {items.map((item, index) => (
                            <li key={`${item}-${index}`} className="my-1.5 break-words text-base">
                                {renderInline(item, `oli-${i}-${index}`)}
                            </li>
                        ))}
                    </ol>
                );
                continue;
            }

            const paragraph: string[] = [line];
            i += 1;
            while (i < lines.length
                && lines[i].trim()
                && !/^(#{1,3})\s+/.test(lines[i].trim())
                && !/^[-*]\s+/.test(lines[i].trim())
                && !/^\d+\.\s+/.test(lines[i].trim())
                && !/^\s*>/.test(lines[i].trim())
                && !/^(```|~~~)/.test(lines[i].trim())) {
                paragraph.push(lines[i].trim());
                i += 1;
            }

            const text = paragraph.join(' ');
            nextBlocks.push(
                <p key={`p-${i}`} className="my-3 leading-relaxed break-words text-base">
                    {renderInline(text, `p-${i}`)}
                </p>
            );
        }

        return nextBlocks.length > 0 ? nextBlocks : [<p key="empty" className="my-3 leading-relaxed break-words text-base">No description.</p>];
    }, [content]);

    return <>{blocks}</>;
};

export const MarkdownRenderer: React.FC<{ content: string; deferRich?: boolean; fastOnly?: boolean }> = ({ content, deferRich = false, fastOnly = false }) => {
    const shouldUseRichMarkdown = !fastOnly && requiresRichMarkdown(content || '');
    const [richEnabled, setRichEnabled] = useState(!deferRich);

    useEffect(() => {
        if (fastOnly || !shouldUseRichMarkdown || !deferRich) {
            setRichEnabled(true);
            return;
        }

        setRichEnabled(false);
        const scheduleIdle = window.requestIdleCallback || ((callback: IdleRequestCallback) => window.setTimeout(callback, 1200));
        const cancelIdle = window.cancelIdleCallback || window.clearTimeout;
        const handle = scheduleIdle(() => setRichEnabled(true), { timeout: 2500 });

        return () => cancelIdle(handle as any);
    }, [content, shouldUseRichMarkdown, deferRich, fastOnly]);

    if (fastOnly || !shouldUseRichMarkdown || !richEnabled) {
        return <MarkdownFallback content={content} />;
    }

    return (
        <Suspense fallback={<MarkdownFallback content={content} />}>
            <MarkdownRichRenderer content={content} />
        </Suspense>
    );
};
