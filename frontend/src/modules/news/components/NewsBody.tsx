import React from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';
import { FeatureDemo } from './FeatureDemo';
import clips from '@/data/newsMediaVersions.json';
import './news-body.css';

const schema = {
    ...defaultSchema,
    clobberPrefix: '',
    tagNames: [...(defaultSchema.tagNames || []), 'video', 'mark', 'u', 'sub', 'sup'],
    attributes: {
        ...defaultSchema.attributes,
        '*': [...(defaultSchema.attributes?.['*'] || []), 'style'],
        div: ['dataDemoClip', 'dataDemoAlt'],
        video: ['src', 'poster', 'controls', 'loop', 'muted', 'playsInline', 'title'],
    },
};
export function NewsBody({ content }: { content: string }) {
    return <div className="news-body"><ReactMarkdown rehypePlugins={[rehypeRaw, [rehypeSanitize, schema]]} components={{
        div: ({ node: _node, ...props }: any) => {
            const clip = props['data-demo-clip'];
            if (typeof clip === 'string' && Object.hasOwn(clips, clip)) return <FeatureDemo clip={clip as keyof typeof clips} alt={props['data-demo-alt'] || 'Product walkthrough'} />;
            return <div {...props} />;
        },
        video: ({ node: _node, ...props }: any) => <video {...props} controls playsInline preload="metadata" />,
        a: ({ node: _node, ...props }: any) => <a {...props} rel="noopener noreferrer" />,
    }}>{content}</ReactMarkdown></div>;
}
