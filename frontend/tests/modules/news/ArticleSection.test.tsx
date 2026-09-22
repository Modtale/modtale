import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { ArticleSection } from '@/modules/news/components/ArticleSection';
import { FeatureDemo } from '@/modules/news/components/FeatureDemo';

const render = (children: React.ReactNode) => {
    const container = document.createElement('div');
    container.innerHTML = renderToStaticMarkup(<ArticleSection id="feature">{children}</ArticleSection>);
    return container;
};

describe('Article feature layout', () => {
    it('keeps trailing explanation with its demo and separates the next feature', () => {
        const article = render([
            <h2 key="a">Download</h2>, <p key="b">Before the download.</p>,
            <FeatureDemo key="c" clip="browse-projects" alt="Download demo" />,
            <p key="d">After the download.</p>, <h3 key="e">Library</h3>,
            <p key="f">Manage your worlds.</p>, <FeatureDemo key="g" clip="world-library" alt="Library demo" />,
        ]);
        const section = article.querySelector('section')!;
        expect(Array.from(section.children).map(child => child.tagName)).toEqual([
            'H2', 'P', 'FIGURE', 'P', 'H3', 'P', 'FIGURE'
        ]);
        expect(section.querySelectorAll('img')[0]?.alt).toBe('Download demo');
        expect(section.querySelectorAll('img')[1]?.alt).toBe('Library demo');
    });

    it('preserves text-only sections without an empty media column', () => {
        const article = render([<h2 key="a">Configs</h2>, <p key="b">Keep your settings.</p>]);
        expect(article.querySelector('h2')?.textContent).toBe('Configs');
        expect(article.querySelector('p')?.textContent).toBe('Keep your settings.');
        expect(article.querySelector('.news-feature-media')).toBeNull();
    });
});
