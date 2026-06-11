import React from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { darcula } from 'react-syntax-highlighter/dist/cjs/styles/prism';

export const HighlightedCode: React.FC<{ content: string; lang: string; hasLanguage: boolean }> = ({ content, lang, hasLanguage }) => (
    <SyntaxHighlighter
        style={darcula}
        language={lang}
        PreTag="div"
        className="!bg-transparent !m-0 !p-4 text-[13px] leading-relaxed"
        customStyle={{
            margin: 0,
            padding: hasLanguage ? '1rem' : '1.25rem',
            background: 'transparent',
            fontFamily: '"JetBrains Mono", "Fira Code", Consolas, monospace',
        }}
    >
        {content}
    </SyntaxHighlighter>
);
