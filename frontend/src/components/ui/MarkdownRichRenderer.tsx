import React, { Suspense, lazy, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Check, Copy } from 'lucide-react';

const HighlightedCode = lazy(() => import('./MarkdownSyntaxHighlighter').then((module) => ({ default: module.HighlightedCode })));
const MermaidChart = lazy(() => import('./MarkdownMermaidChart').then((module) => ({ default: module.MermaidChart })));

const allowedTextAlignments = new Set(['left', 'center', 'right', 'justify']);

const extractTextAlign = (value: unknown): React.CSSProperties['textAlign'] | undefined => {
    if (typeof value !== 'string') {
        return undefined;
    }

    const match = value.match(/(?:^|;)\s*text-align\s*:\s*(left|center|right|justify)\s*(?:;|$)/i);
    if (!match) {
        return undefined;
    }

    const alignment = match[1].toLowerCase();
    return allowedTextAlignments.has(alignment) ? (alignment as React.CSSProperties['textAlign']) : undefined;
};

const createAlignedProps = (props: Record<string, unknown>) => {
    const align = typeof props.align === 'string' ? props.align.toLowerCase() : '';
    const textAlign = allowedTextAlignments.has(align) ? (align as React.CSSProperties['textAlign']) : undefined;

    return textAlign
        ? { ...props, style: { ...(props.style as React.CSSProperties | undefined), textAlign } }
        : props;
};

const rehypePreserveTextAlign = () => (tree: any) => {
    const visit = (node: any) => {
        if (node?.type === 'element' && node.properties) {
            const textAlign = extractTextAlign(node.properties.style);
            if (textAlign) {
                node.properties.align = textAlign;
            }

            delete node.properties.style;
        }

        if (Array.isArray(node?.children)) {
            node.children.forEach(visit);
        }
    };

    visit(tree);
};

const CodeFallback = ({ content }: { content: string }) => (
    <pre className="!bg-transparent !m-0 !p-4 text-[13px] leading-relaxed overflow-x-auto">
        <code>{content}</code>
    </pre>
);

const CodeBlock = ({ node: _node, inline, className, children, ...props }: any) => {
    const [copied, setCopied] = useState(false);
    const match = /language-(\w+)/.exec(className || '');
    const isBlock = !inline && (match || String(children).includes('\n'));

    if (isBlock) {
        const lang = match ? match[1] : 'text';
        const content = String(children).replace(/\n$/, '');

        if (lang === 'mermaid') {
            return (
                <Suspense fallback={<div className="animate-pulse h-32 bg-slate-100 dark:bg-slate-800 rounded-xl my-4" />}>
                    <MermaidChart chart={content} />
                </Suspense>
            );
        }

        const handleCopy = () => {
            navigator.clipboard.writeText(content);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        };

        return (
            <div className="relative w-full my-4 rounded-xl overflow-hidden bg-slate-900 ring-1 ring-slate-300 dark:ring-white/10 shadow-lg [&+&]:-mt-[17px] [&+&]:rounded-t-none [&+&]:!border-t-0 z-10 group">
                <div className="px-4 py-1.5 bg-slate-800 border-b border-slate-950 text-xs font-sans text-slate-400 select-none flex items-center justify-between">
                    <span>{lang}</span>
                    <button
                        onClick={handleCopy}
                        className="group/copy flex items-center justify-end px-2 py-1 -mr-2 rounded-md hover:bg-white/10 transition-colors text-slate-400 hover:text-white focus:outline-none"
                        title="Copy code"
                    >
                        {copied ? <Check className="w-3.5 h-3.5 text-green-500 shrink-0" /> : <Copy className="w-3.5 h-3.5 shrink-0" />}
                        <div className={`overflow-hidden transition-all duration-300 ease-in-out flex items-center ${copied ? 'max-w-[50px] ml-1.5 opacity-100' : 'max-w-0 opacity-0 group-hover/copy:max-w-[40px] group-hover/copy:ml-1.5 group-hover/copy:opacity-100'}`}>
                             <span className={`text-[10px] font-bold whitespace-nowrap ${copied ? 'text-green-500' : ''}`}>
                                 {copied ? 'Copied!' : 'Copy'}
                             </span>
                        </div>
                    </button>
                </div>
                <Suspense fallback={<CodeFallback content={content} />}>
                    <HighlightedCode content={content} lang={lang} hasLanguage={Boolean(match)} />
                </Suspense>
            </div>
        );
    }

    return (
        <code
            className={`${className || ''} !before:hidden !after:hidden bg-slate-200/70 dark:bg-slate-800 px-1.5 py-0.5 rounded-md text-[0.85em] font-mono text-slate-800 dark:text-slate-300 border border-slate-300/50 dark:border-slate-700/50 break-words`}
            style={{ fontFamily: '"JetBrains Mono", "Fira Code", Consolas, monospace' }}
            {...props}
        >
            {children}
        </code>
    );
};

const markdownComponents = {
    code: CodeBlock,
    pre({ children }: any) {
        return <>{children}</>;
    },
    p({ node: _node, children, ...props }: any) {
        return <p className="my-3 leading-relaxed break-words text-base" {...createAlignedProps(props)}>{children}</p>;
    },
    li({ node: _node, children, ...props }: any) {
        return <li className="my-1.5 [&>p]:my-0 break-words text-base" {...createAlignedProps(props)}>{children}</li>;
    },
    ul({ node: _node, children, ...props }: any) {
        return <ul className="list-disc pl-6 my-3 space-y-1.5" {...createAlignedProps(props)}>{children}</ul>;
    },
    ol({ node: _node, children, ...props }: any) {
        return <ol className="list-decimal pl-6 my-3 space-y-1.5" {...createAlignedProps(props)}>{children}</ol>;
    },
    div({ node: _node, children, ...props }: any) {
        return <div {...createAlignedProps(props)}>{children}</div>;
    },
    h1({ node: _node, children, ...props }: any) {
        return <h1 className="text-4xl font-black mb-6" {...createAlignedProps(props)}>{children}</h1>;
    },
    h2({ node: _node, children, ...props }: any) {
        return <h2 className="text-2xl font-black mt-8 mb-4" {...createAlignedProps(props)}>{children}</h2>;
    },
    h3({ node: _node, children, ...props }: any) {
        return <h3 className="text-xl font-black mt-6 mb-3" {...createAlignedProps(props)}>{children}</h3>;
    },
    img({ node: _node, ...props }: any) {
        return <img className="inline-block align-middle max-w-full h-auto my-0" {...props} />;
    },
};

export const MarkdownRichRenderer: React.FC<{ content: string }> = ({ content }) => {
    return (
        <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            rehypePlugins={[
                rehypeRaw,
                rehypePreserveTextAlign,
                [
                    rehypeSanitize,
                    {
                        ...defaultSchema,
                        attributes: {
                            ...defaultSchema.attributes,
                            code: ['className']
                        }
                    }
                ]
            ]}
            components={markdownComponents}
        >
            {content}
        </ReactMarkdown>
    );
};
