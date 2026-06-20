import React, { Suspense, lazy, useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { Check, Copy } from 'lucide-react';
import { getYouTubeEmbedUrl, getYouTubeVideoId } from '@/utils/youtube';

const HighlightedCode = lazy(() => import('./MarkdownSyntaxHighlighter').then((module) => ({ default: module.HighlightedCode })));

const allowedTextAlignments = new Set(['left', 'center', 'right', 'justify']);
const youtubeIframeAllow = 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share';

const getSafeYouTubeEmbedUrl = (src: unknown) => {
    if (typeof src !== 'string') {
        return null;
    }

    const videoId = getYouTubeVideoId(src);
    return videoId ? getYouTubeEmbedUrl(videoId) : null;
};

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

const rehypeNormalizeYouTubeEmbeds = () => (tree: any) => {
    const visit = (node: any) => {
        if (node?.type === 'element' && node.tagName === 'iframe') {
            const embedUrl = getSafeYouTubeEmbedUrl(node.properties?.src);

            if (!embedUrl) {
                node.tagName = 'span';
                node.properties = {};
                node.children = [];
            } else {
                node.properties = {
                    src: embedUrl,
                    title: typeof node.properties?.title === 'string' && node.properties.title.trim()
                        ? node.properties.title
                        : 'YouTube video',
                    allow: youtubeIframeAllow,
                    allowFullScreen: true,
                    loading: 'lazy',
                    referrerPolicy: 'strict-origin-when-cross-origin'
                };
            }
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

const fallbackCopyText = (content: string) => {
    if (typeof document === 'undefined') {
        return false;
    }

    const textarea = document.createElement('textarea');
    textarea.value = content;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.top = '-9999px';
    textarea.style.left = '-9999px';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);

    const legacyDocument = document as unknown as { execCommand?: (command: string) => boolean };

    try {
        return legacyDocument.execCommand?.('copy') ?? false;
    } finally {
        document.body.removeChild(textarea);
    }
};

const copyText = async (content: string) => {
    try {
        if (navigator.clipboard?.writeText) {
            await navigator.clipboard.writeText(content);
            return true;
        }
    } catch {
        // Fall through to the textarea fallback for non-secure or embedded browser contexts.
    }

    return fallbackCopyText(content);
};

const MermaidFallback = ({ content }: { content: string }) => (
    <div className="relative w-full my-4 rounded-xl overflow-hidden bg-slate-900 ring-1 ring-slate-300 dark:ring-white/10 shadow-lg z-10">
        <div className="px-4 py-1.5 bg-slate-800 border-b border-slate-950 text-xs font-sans text-slate-400 select-none">
            mermaid
        </div>
        <CodeFallback content={content} />
    </div>
);

const DeferredMermaidChart = ({ content }: { content: string }) => {
    const [MermaidChart, setMermaidChart] = useState<React.ComponentType<{ chart: string }> | null>(null);
    const [loadFailed, setLoadFailed] = useState(false);

    useEffect(() => {
        let isMounted = true;

        import('./MarkdownMermaidChart')
            .then((module) => {
                if (isMounted) {
                    setMermaidChart(() => module.MermaidChart);
                    setLoadFailed(false);
                }
            })
            .catch(() => {
                if (isMounted) {
                    setMermaidChart(null);
                    setLoadFailed(true);
                }
            });

        return () => {
            isMounted = false;
        };
    }, []);

    if (loadFailed) {
        return <MermaidFallback content={content} />;
    }

    if (!MermaidChart) {
        return <div className="animate-pulse h-32 bg-slate-100 dark:bg-slate-800 rounded-xl my-4" />;
    }

    return <MermaidChart chart={content} />;
};

const CodeBlock = ({ node: _node, inline, className, children, ...props }: any) => {
    const [copied, setCopied] = useState(false);
    const resetTimerRef = useRef<number | null>(null);
    const match = /language-(\w+)/.exec(className || '');
    const isBlock = !inline && (match || String(children).includes('\n'));

    useEffect(() => () => {
        if (resetTimerRef.current !== null) {
            window.clearTimeout(resetTimerRef.current);
        }
    }, []);

    if (isBlock) {
        const lang = match ? match[1] : 'text';
        const content = String(children).replace(/\n$/, '');

        if (lang === 'mermaid') {
            return <DeferredMermaidChart content={content} />;
        }

        const handleCopy = async () => {
            const copiedToClipboard = await copyText(content);
            if (!copiedToClipboard) {
                return;
            }

            setCopied(true);
            if (resetTimerRef.current !== null) {
                window.clearTimeout(resetTimerRef.current);
            }
            resetTimerRef.current = window.setTimeout(() => {
                setCopied(false);
                resetTimerRef.current = null;
            }, 2000);
        };

        return (
            <div className="relative w-full my-4 rounded-xl overflow-hidden bg-slate-900 ring-1 ring-slate-300 dark:ring-white/10 shadow-lg [&+&]:-mt-[17px] [&+&]:rounded-t-none [&+&]:!border-t-0 z-10 group">
                <div className="px-4 py-1.5 bg-slate-800 border-b border-slate-950 text-xs font-sans text-slate-400 select-none flex items-center justify-between">
                    <span>{lang}</span>
                    <button
                        type="button"
                        onClick={() => { void handleCopy(); }}
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
    iframe({ node: _node, src, title }: any) {
        const embedUrl = getSafeYouTubeEmbedUrl(src);
        if (!embedUrl) return null;

        return (
            <div className="my-6 aspect-video w-full overflow-hidden rounded-xl bg-slate-950 shadow-lg ring-1 ring-slate-300 dark:ring-white/10">
                <iframe
                    src={embedUrl}
                    title={typeof title === 'string' && title.trim() ? title : 'YouTube video'}
                    className="h-full w-full"
                    allow={youtubeIframeAllow}
                    allowFullScreen
                    loading="lazy"
                    referrerPolicy="strict-origin-when-cross-origin"
                />
            </div>
        );
    },
};

const markdownSanitizeSchema = {
    ...defaultSchema,
    tagNames: [
        ...(defaultSchema.tagNames || []),
        'iframe'
    ],
    attributes: {
        ...defaultSchema.attributes,
        code: ['className'],
        iframe: ['src', 'title', 'allow', 'allowFullScreen', 'loading', 'referrerPolicy']
    },
    protocols: {
        ...defaultSchema.protocols,
        src: Array.from(new Set([...(defaultSchema.protocols?.src || []), 'http', 'https']))
    }
};

export const MarkdownRichRenderer: React.FC<{ content: string }> = ({ content }) => {
    return (
        <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            rehypePlugins={[
                rehypeRaw,
                rehypePreserveTextAlign,
                rehypeNormalizeYouTubeEmbeds,
                [
                    rehypeSanitize,
                    markdownSanitizeSchema
                ]
            ]}
            components={markdownComponents}
        >
            {content}
        </ReactMarkdown>
    );
};
