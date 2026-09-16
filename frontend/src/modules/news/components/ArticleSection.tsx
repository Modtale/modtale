import React, { Children, isValidElement, type ReactNode } from 'react';
import { FeatureDemo } from './FeatureDemo';

/** Keep each walkthrough beside the copy that introduces it, in reading order. */
export function ArticleSection({ id, children }: { id: string; children: ReactNode }) {
    const rows: ReactNode[] = [];
    let copy: ReactNode[] = [];
    Children.toArray(children).forEach(child => {
        if (isValidElement(child) && child.type === FeatureDemo) {
            rows.push(
                <div className="news-feature-row" key={rows.length}>
                    <div className="news-feature-copy">{copy}</div>
                    {child}
                </div>
            );
            copy = [];
        } else if (child != null && typeof child !== 'boolean') {
            copy.push(child);
        }
    });
    return <section id={id}>{rows}{copy.length > 0 && <div className="news-section-copy">{copy}</div>}</section>;
}
