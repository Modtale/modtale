import React from 'react';
import { theme } from '@/styles/theme';
import { FileText } from 'lucide-react';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import type { MetadataFormData } from '../components/FormShared';
import { Permission } from '@/modules/permissions/permissions';

interface EditDetailsProps {
    metaData: MetadataFormData;
    setMetaData: React.Dispatch<React.SetStateAction<MetadataFormData>>;
    readOnly: boolean;
    hasProjectPermission: (perm: Permission) => boolean;
    editorMode: 'write' | 'preview';
    setEditorMode: (mode: 'write' | 'preview') => void;
    markDirty: () => void;
}

export const EditDetails: React.FC<EditDetailsProps> = ({ metaData, setMetaData, readOnly, hasProjectPermission, editorMode, setEditorMode, markDirty }) => {
    const canEdit = !readOnly && hasProjectPermission(Permission.PROJECT_EDIT_METADATA);
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
            {editorMode === 'write' && canEdit ? (
                <textarea value={metaData.description} onChange={e => { markDirty(); setMetaData({...metaData, description: e.target.value}); }} className={`flex-1 w-full h-full min-h-[400px] bg-transparent border-none outline-none ${theme.colors.textPrimary} font-mono text-sm resize-none`} placeholder="# Description..." />
            ) : (
                <div className="prose dark:prose-invert prose-lg max-w-none min-h-[400px] prose-code:before:hidden prose-code:after:hidden">
                    {metaData.description ? <MarkdownRenderer content={metaData.description} /> : <p className={`${theme.colors.textMuted} italic`}>No description.</p>}
                </div>
            )}
        </div>
    );
};
