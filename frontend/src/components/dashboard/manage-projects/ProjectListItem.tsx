import React from 'react';
import { Link } from 'react-router-dom';
import type { Mod } from '../../../types';
import { Download, Eye, BarChart2, Edit, ArrowRightLeft, Trash2, Building2, User as UserIcon, Undo2, Clock, AlertCircle, ExternalLink } from 'lucide-react';

interface ProjectListItemProps {
    project: Mod;
    isOwner?: boolean;
    canManage: boolean;
    showAuthor?: boolean;
    onTransfer: (project: Mod) => void;
    onDelete: (project: Mod) => void;
    onRevert?: (project: Mod) => void;
}

export const ProjectListItem: React.FC<ProjectListItemProps> = ({
                                                                    project, isOwner = false, canManage, showAuthor = false, onTransfer, onDelete, onRevert
                                                                }) => {

    const pendingVersion = project.versions?.find(v => v.reviewStatus === 'PENDING');
    const rejectedVersion = project.versions?.find(v => v.reviewStatus === 'REJECTED');

    let projectLink = `/mod/${project.id}`;
    if (project.classification === 'MODPACK') projectLink = `/modpack/${project.id}`;
    if (project.classification === 'SAVE') projectLink = `/world/${project.id}`;

    if (project.slug) {
        if (project.classification === 'MODPACK') projectLink = `/modpack/${project.slug}`;
        else if (project.classification === 'SAVE') projectLink = `/world/${project.slug}`;
        else projectLink = `/mod/${project.slug}`;
    }

    return (
        <div className="p-4 flex flex-col sm:flex-row items-center gap-4 group relative w-full h-full">
            <div className="w-16 h-16 rounded-xl bg-slate-200/50 dark:bg-white/5 overflow-hidden flex-shrink-0 border border-slate-200 dark:border-white/10 shadow-sm">
                <img src={project.imageUrl} alt="" className="w-full h-full object-cover" />
            </div>

            <div className="flex-1 text-center sm:text-left">
                <div className="flex items-center justify-center sm:justify-start gap-2 flex-wrap">
                    <Link to={projectLink} className="font-bold text-slate-900 dark:text-white text-lg hover:underline decoration-2 underline-offset-2 flex items-center gap-2">
                        {project.title}
                        <ExternalLink className="w-3.5 h-3.5 opacity-50" />
                    </Link>

                    {project.status === 'DRAFT' && <span className="text-[10px] bg-yellow-50 text-yellow-800 border border-yellow-200 dark:border-yellow-900/50 dark:bg-yellow-900/30 dark:text-yellow-200 px-2 py-0.5 rounded-md font-bold uppercase tracking-wider">Draft</span>}
                    {project.status === 'PENDING' && <span className="text-[10px] bg-orange-50 text-orange-800 border border-orange-200 dark:border-orange-900/50 dark:bg-orange-900/30 dark:text-orange-200 px-2 py-0.5 rounded-md font-bold uppercase tracking-wider">Pending Approval</span>}
                    {project.status === 'UNLISTED' && <span className="text-[10px] bg-slate-100 text-slate-600 border border-slate-200 dark:border-white/10 dark:bg-white/10 dark:text-slate-300 px-2 py-0.5 rounded-md font-bold uppercase tracking-wider">Unlisted</span>}

                    {project.status === 'PUBLISHED' && pendingVersion && (
                        <div className="flex items-center gap-1 bg-blue-50/50 dark:bg-blue-500/10 text-blue-600 dark:text-blue-400 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase border border-blue-200 dark:border-blue-500/20 tracking-wider">
                            <Clock className="w-3 h-3" /> v{pendingVersion.versionNumber} Pending
                        </div>
                    )}
                    {rejectedVersion && (
                        <div className="flex items-center gap-1 bg-red-50/50 dark:bg-red-500/10 text-red-600 dark:text-red-400 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase border border-red-200 dark:border-red-500/20 tracking-wider" title={rejectedVersion.rejectionReason}>
                            <AlertCircle className="w-3 h-3" /> v{rejectedVersion.versionNumber} Rejected
                        </div>
                    )}
                </div>

                <div className="text-sm text-slate-500 flex items-center justify-center sm:justify-start gap-4 mt-1.5 flex-wrap font-medium">
                    <span className="flex items-center gap-1.5"><Download className="w-3.5 h-3.5" /> {project.downloadCount}</span>
                    <span className="flex items-center gap-1.5"><Eye className="w-3.5 h-3.5" /> {project.status === 'PUBLISHED' ? 'Public' : 'Private'}</span>
                    {showAuthor && !isOwner && (
                        <span className="flex items-center gap-1.5 text-purple-600 dark:text-purple-400 font-bold bg-purple-50 dark:bg-purple-900/30 border border-purple-200 dark:border-purple-800/30 px-2 py-0.5 rounded-md text-[10px] uppercase tracking-wider">
                            <Building2 className="w-3 h-3" /> {project.author}
                        </span>
                    )}
                </div>
            </div>

            <div className="flex items-center gap-2 opacity-100 sm:opacity-0 group-hover:opacity-100 transition-opacity">
                {canManage ? (
                    <>
                        {project.status === 'PENDING' && onRevert && (
                            <button onClick={() => onRevert(project)} className="p-2.5 bg-slate-200/50 dark:bg-white/5 hover:bg-orange-500 hover:text-white rounded-xl transition-colors shadow-sm" title="Revert to Draft"><Undo2 className="w-4 h-4" /></button>
                        )}
                        <Link to={`/dashboard/analytics/project/${project.id}`} className="p-2.5 bg-slate-200/50 dark:bg-white/5 hover:bg-modtale-accent hover:text-white rounded-xl transition-colors shadow-sm" title="Analytics"><BarChart2 className="w-4 h-4" /></Link>
                        <Link to={`/mod/${project.id}/edit`} className="p-2.5 bg-slate-200/50 dark:bg-white/5 hover:bg-modtale-accent hover:text-white rounded-xl transition-colors shadow-sm" title="Edit"><Edit className="w-4 h-4" /></Link>
                        <button onClick={() => onTransfer(project)} className="p-2.5 bg-slate-200/50 dark:bg-white/5 hover:bg-slate-900 hover:text-white dark:hover:bg-white dark:hover:text-slate-900 rounded-xl transition-colors shadow-sm" title="Transfer"><ArrowRightLeft className="w-4 h-4" /></button>
                        <button onClick={() => onDelete(project)} className="p-2.5 bg-slate-200/50 dark:bg-white/5 hover:bg-red-500 hover:text-white rounded-xl transition-colors shadow-sm" title="Delete"><Trash2 className="w-4 h-4" /></button>
                    </>
                ) : (
                    <span className="text-xs text-slate-400 italic pr-2 font-medium">Read Only</span>
                )}
            </div>
        </div>
    );
};