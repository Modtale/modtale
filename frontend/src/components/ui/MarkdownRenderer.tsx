import React, { Suspense, lazy, useMemo } from 'react';

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
    const parts = text.split(/(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\[[^\]]+\]\([^)]+\)|\*[^*\s][^*]*\*|_[^_\s][^_]*_)/g).filter(Boolean);

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
                                {renderInline(item, `li-${i}-${index}`)}
                            </li>
                        ))}
                    </ul>
                );
                continue;
            }

            const paragraph: string[] = [line];
            i += 1;
            while (i < lines.length && lines[i].trim() && !/^(#{1,3})\s+/.test(lines[i].trim()) && !/^[-*]\s+/.test(lines[i].trim())) {
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

export const MarkdownRenderer: React.FC<{ content: string }> = ({ content }) => {
    if (!requiresRichMarkdown(content || '')) {
        return <MarkdownFallback content={content} />;
    }

    return (
        <Suspense fallback={<MarkdownFallback content={content} />}>
            <MarkdownRichRenderer content={content} />
        </Suspense>
    );
};
