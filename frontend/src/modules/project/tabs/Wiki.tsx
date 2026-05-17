import React from 'react';
import { WikiContent } from '@/modules/project/components/HMWiki';
import type { Project } from '@/types';

interface WikiProps {
    wikiLoading: boolean;
    wikiError: any;
    displayWikiData: any;
    displaySlug: string | undefined;
    project: Project;
    wikiContentRef: React.RefObject<HTMLDivElement | null>;
    lockedHeight: number | undefined;
}

export const Wiki: React.FC<WikiProps> = ({ wikiLoading, wikiError, displayWikiData, displaySlug, project, wikiContentRef, lockedHeight }) => {
    return (
        <div ref={wikiContentRef} style={{ minHeight: lockedHeight ? `${lockedHeight}px` : undefined }} className={`transition-opacity duration-200 ${wikiLoading ? 'opacity-60 pointer-events-none' : 'opacity-100'}`}>
            <WikiContent wikiLoading={wikiLoading && !displayWikiData} wikiError={wikiError} wikiData={displayWikiData} wikiPageSlug={displaySlug} mod={project} />
        </div>
    );
};