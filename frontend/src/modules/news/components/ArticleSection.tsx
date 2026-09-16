import React, { Children, isValidElement, type ReactNode } from 'react';
import { FeatureDemo } from './FeatureDemo';

type Feature = { heading: ReactNode; copy: ReactNode[]; media: ReactNode[] };

/** Headings define a feature; its explanation stays with its walkthrough. */
export function ArticleSection({ id, children }: { id: string; children: ReactNode }) {
    const features: Feature[] = [];
    let feature: Feature = { heading: null, copy: [], media: [] };
    const finish = () => {
        if (feature.heading || feature.copy.length || feature.media.length) features.push(feature);
        feature = { heading: null, copy: [], media: [] };
    };
    Children.toArray(children).forEach(child => {
        if (typeof child === 'string' && !child.trim()) return;
        if (isValidElement(child) && (child.type === 'h2' || child.type === 'h3')) {
            finish();
            feature.heading = child;
        } else if (isValidElement(child) && child.type === FeatureDemo) {
            feature.media.push(child);
        } else {
            feature.copy.push(child);
        }
    });
    finish();
    return (
        <section id={id}>
            {features.map((item, index) => item.media.length ? (
                <div className="news-feature-row" key={index}>
                    <div className="news-feature-copy">{item.heading}{item.copy}</div>
                    <div className="news-feature-media">{item.media}</div>
                </div>
            ) : (
                <div className="news-text-row" key={index}>
                    <div className="news-section-heading">{item.heading}</div>
                    <div className="news-section-copy">{item.copy}</div>
                </div>
            ))}
        </section>
    );
}
