import React, { useState, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { darcula } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { Check, Copy } from 'lucide-react';
import mermaid from 'mermaid';

const CodeBlock = ({ node, inline, className, children, ...props }: any) => {
    const [copied, setCopied] = useState(false);
    const match = /language-(\w+)/.exec(className || '');
    const isBlock = !inline && (match || String(children).includes('\n'));

    if (isBlock) {
        const lang = match ? match[1] : 'text';
        const content = String(children).replace(/\n$/, '');

        if (lang === 'mermaid') {
            return <MermaidChart chart={content} />;
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
                <SyntaxHighlighter
                    {...props}
                    style={darcula}
                    language={lang}
                    PreTag="div"
                    className="!bg-transparent !m-0 !p-4 text-[13px] leading-relaxed"
                    customStyle={{
                        margin: 0,
                        padding: match ? '1rem' : '1.25rem',
                        background: 'transparent',
                        fontFamily: '"JetBrains Mono", "Fira Code", Consolas, monospace',
                    }}
                >
                    {content}
                </SyntaxHighlighter>
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

const MermaidChart: React.FC<{ chart: string }> = ({ chart }) => {
    const [svg, setSvg] = useState<string>('');
    const id = `mermaid-${Math.random().toString(36).substr(2, 9)}`;

    useEffect(() => {
        const isDark = document.documentElement.classList.contains('dark');

        mermaid.initialize({
            startOnLoad: false,
            theme: isDark ? 'dark' : 'default',
            securityLevel: 'loose',
            fontFamily: 'inherit'
        });

        const renderChart = async () => {
            try {
                const { svg: renderedSvg } = await mermaid.render(id, chart);
                setSvg(renderedSvg);
            } catch (e) {
                console.error('Mermaid rendering failed', e);
                setSvg(`<div class="text-red-500 bg-red-50 p-4 rounded-lg border border-red-200 text-sm">Failed to render diagram</div>`);
            }
        };

        renderChart();
    }, [chart, id]);

    if (!svg) {
        return <div className="animate-pulse h-32 bg-slate-100 dark:bg-slate-800 rounded-xl my-4"></div>;
    }

    return (
        <div
            className="my-6 p-4 bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-white/10 shadow-sm overflow-x-auto flex justify-center mermaid-container"
            dangerouslySetInnerHTML={{ __html: svg }}
        />
    );
};

const markdownComponents = {
    code: CodeBlock,
    pre({ node, children, ...props }: any) {
        return <>{children}</>;
    },
    p({ node, children, ...props }: any) {
        return <p className="my-3 leading-relaxed break-words text-base" {...props}>{children}</p>;
    },
    li({ node, children, ...props }: any) {
        return <li className="my-1.5 [&>p]:my-0 break-words text-base" {...props}>{children}</li>;
    },
    ul({ node, children, ...props }: any) {
        return <ul className="list-disc pl-6 my-3 space-y-1.5" {...props}>{children}</ul>;
    },
    ol({ node, children, ...props }: any) {
        return <ol className="list-decimal pl-6 my-3 space-y-1.5" {...props}>{children}</ol>;
    },
};

export const MarkdownRenderer: React.FC<{ content: string }> = ({ content }) => {
    return (
        <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            rehypePlugins={[
                rehypeRaw,
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