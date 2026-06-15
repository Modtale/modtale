import React from 'react';
import { theme } from '@/styles/theme';
import { FileText } from 'lucide-react';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import type { Project } from '@/types';
import type { MetadataFormData } from '../components/FormShared';
import { GalleryCarousel } from '../components/GalleryCarousel';
import { Permission } from '@/modules/permissions/permissions';
import { GALLERY_CAROUSEL_MARKER, splitDescriptionByGalleryCarouselMarker } from '../utils/galleryCarouselMarker';

interface EditDetailsProps {
    metaData: MetadataFormData;
    projectData: Project | null;
    setMetaData: React.Dispatch<React.SetStateAction<MetadataFormData>>;
    readOnly: boolean;
    hasProjectPermission: (perm: Permission) => boolean;
    editorMode: 'write' | 'preview';
    setEditorMode: (mode: 'write' | 'preview') => void;
    markDirty: () => void;
}

export const EditDetails: React.FC<EditDetailsProps> = ({ metaData, projectData, setMetaData, readOnly, hasProjectPermission, editorMode, setEditorMode, markDirty }) => {
    const canEdit = !readOnly && hasProjectPermission(Permission.PROJECT_EDIT_METADATA);
    const previewDescriptionParts = splitDescriptionByGalleryCarouselMarker(metaData.description || "*No description.*");
    const renderGalleryCarousel = (key: string) => (
        <GalleryCarousel
            key={key}
            images={projectData?.galleryImages}
            captions={projectData?.galleryImageCaptions}
            title={metaData.title || projectData?.title || 'Project'}
        />
    );

    return (
        <div className="h-full flex flex-col">
            <div className={`flex items-center justify-between mb-4 pb-2 border-b ${theme.colors.borderFaint}`}>
                <h3 className={`text-xs font-bold ${theme.colors.textMuted} uppercase tracking-widest flex items-center gap-2`}><FileText className="w-3 h-3"/> Description</h3>
                {canEdit && (
                    <div className={`flex ${theme.colors.bgSurfaceAlt} rounded-lg p-1 border ${theme.colors.border}`}>
                        <button type="button" onClick={() => setEditorMode('write')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'write' ? 'bg-modtale-accent text-white' : theme.colors.textMuted}`}>Write</button>
                        <button type="button" onClick={() => setEditorMode('preview')} className={`px-4 py-1.5 text-xs font-bold rounded-md ${editorMode === 'preview' ? 'bg-modtale-accent text-white' : theme.colors.textMuted}`}>Preview</button>
                    </div>
                )}
            </div>
            {canEdit && (
                <p className={`mb-3 text-xs ${theme.colors.textMuted}`}>
                    Add <code className="font-mono">{GALLERY_CAROUSEL_MARKER}</code> once where the gallery carousel should appear. Leave it out if you do not want the carousel in the description.
                </p>
            )}
            {editorMode === 'write' && canEdit ? (
                <textarea value={metaData.description} onChange={e => { markDirty(); setMetaData({...metaData, description: e.target.value}); }} className={`flex-1 w-full h-full min-h-[400px] bg-transparent border-none outline-none ${theme.colors.textPrimary} font-mono text-sm resize-none`} placeholder="# Description..." />
            ) : (
                <div className="min-h-[400px]">
                    {previewDescriptionParts.map((part, index) => (
                        part.type === 'gallery'
                            ? renderGalleryCarousel(`gallery-preview-${index}`)
                            : (
                                <div key={`description-preview-${index}`} className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden">
                                    <MarkdownRenderer content={part.content} />
                                </div>
                            )
                    ))}
                </div>
            )}
        </div>
    );
};
