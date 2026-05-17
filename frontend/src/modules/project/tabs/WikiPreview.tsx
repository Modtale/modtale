import React from 'react';
import { BookOpen } from 'lucide-react';
import { theme } from '@/styles/theme';
import { WikiContent } from '@/modules/project/components/HMWiki';
import type { Project } from '@/types';

interface WikiPreviewProps {
    wikiLoading: boolean;
    wikiError: boolean;
    wikiData: any;
    wikiPreviewSlug: string | undefined;
    projectData: Project | null;
}

export const WikiPreview: React.FC<WikiPreviewProps> = ({ wikiLoading, wikiError, wikiData, wikiPreviewSlug, projectData }) => {
    return (
        <div className="h-full flex flex-col">
            <div className={`flex items-center justify-between mb-4 pb-2 border-b ${theme.colors.borderFaint}`}>
                <h3 className={`text-xs font-bold ${theme.colors.textMuted} uppercase tracking-widest flex items-center gap-2`}><BookOpen className="w-3 h-3"/> Wiki Preview</h3>
            </div>
            <WikiContent wikiLoading={wikiLoading} wikiError={wikiError} wikiData={wikiData} wikiPageSlug={wikiPreviewSlug} mod={projectData} />
        </div>
    );
};