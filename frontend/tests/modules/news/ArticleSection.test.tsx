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
        const rows = article.querySelectorAll('.news-feature-row');
        expect(rows).toHaveLength(2);
        expect(rows[0].querySelector('.news-feature-copy')?.textContent).toContain('After the download.');
        expect(rows[0].querySelector('img')?.alt).toBe('Download demo');
        expect(rows[1].querySelector('h3')?.textContent).toBe('Library');
        expect(rows[1].querySelector('img')?.alt).toBe('Library demo');
    });

    it('preserves text-only sections without an empty media column', () => {
        const article = render([<h2 key="a">Configs</h2>, <p key="b">Keep your settings.</p>]);
        expect(article.querySelector('.news-section-heading')?.textContent).toBe('Configs');
        expect(article.querySelector('.news-section-copy')?.textContent).toBe('Keep your settings.');
        expect(article.querySelector('.news-feature-media')).toBeNull();
    });
});
