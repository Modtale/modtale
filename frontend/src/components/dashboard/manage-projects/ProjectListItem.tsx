import React from 'react';
import { Link } from 'react-router-dom';
import type {Mod} from '../../../types';
import { Download, Eye, BarChart2, Edit, ArrowRightLeft, Trash2, Building2, User as UserIcon, Undo2 } from 'lucide-react';

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
                                                                    project,
                                                                    isOwner = false,
                                                                    canManage,
                                                                    showAuthor = false,
                                                                    onTransfer,
                                                                    onDelete,
                                                                    onRevert
                                                                }) => {
    return (
        <div className="bg-white dark:bg-modtale-card border border-slate-200 dark:border-white/5 p-4 rounded-2xl flex flex-col sm:flex-row items-center gap-4 group">
            <div className="w-16 h-16 rounded-xl bg-slate-100 overflow-hidden flex-shrink-0">
                <img src={project.imageUrl} alt="" className="w-full h-full object-cover" />
            </div>

            <div className="flex-1 text-center sm:text-left">
                <div className="flex items-center justify-center sm:justify-start gap-2 flex-wrap">
                    <h3 className="font-bold text-slate-900 dark:text-white text-lg">{project.title}</h3>
                    {project.status === 'DRAFT' && <span className="text-[10px] bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-200 px-2 py-0.5 rounded font-bold uppercase">Draft</span>}
                    {project.status === 'PENDING' && <span className="text-[10px] bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-200 px-2 py-0.5 rounded font-bold uppercase">Pending</span>}
                    {project.status === 'UNLISTED' && <span className="text-[10px] bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300 px-2 py-0.5 rounded font-bold uppercase">Unlisted</span>}
                    {project.status === 'ARCHIVED' && <span className="text-[10px] bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-200 px-2 py-0.5 rounded font-bold uppercase">Archived</span>}
                </div>

                <div className="text-sm text-slate-500 flex items-center justify-center sm:justify-start gap-4 mt-1 flex-wrap">
                    <span className="flex items-center gap-1"><Download className="w-3 h-3" /> {project.downloadCount}</span>
                    <span className="flex items-center gap-1"><Eye className="w-3 h-3" /> Public</span>
                    {showAuthor && !isOwner && (
                        <span className="flex items-center gap-1 text-purple-600 dark:text-purple-400 font-bold bg-purple-100 dark:bg-purple-900/30 px-2 py-0.5 rounded text-[10px] uppercase">
                            <Building2 className="w-3 h-3" /> {project.author}
                        </span>
                    )}
                    {showAuthor && isOwner && (
                        <span className="flex items-center gap-1 text-slate-400 font-bold text-[10px] uppercase">
                            <UserIcon className="w-3 h-3" /> You
                        </span>
                    )}
                </div>
            </div>

            <div className="flex items-center gap-2 opacity-100 sm:opacity-0 group-hover:opacity-100 transition-opacity">
                {canManage ? (
                    <>
                        {project.status === 'PENDING' && onRevert && (
                            <button
                                onClick={() => onRevert(project)}
                                className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-orange-500 hover:text-white rounded-lg transition-colors"
                                title="Revert to Draft"
                            >
                                <Undo2 className="w-4 h-4" />
                            </button>
                        )}
                        <Link to={`/dashboard/analytics/project/${project.id}`} className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white rounded-lg transition-colors" title="Analytics">
                            <BarChart2 className="w-4 h-4" />
                        </Link>
                        <Link to={`/mod/${project.id}/edit`} className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-modtale-accent hover:text-white rounded-lg transition-colors" title="Edit">
                            <Edit className="w-4 h-4" />
                        </Link>
                        <button
                            onClick={() => onTransfer(project)}
                            className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-slate-900 hover:text-white dark:hover:bg-white dark:hover:text-slate-900 rounded-lg transition-colors"
                            title="Transfer Ownership"
                        >
                            <ArrowRightLeft className="w-4 h-4" />
                        </button>
                        <button
                            onClick={() => onDelete(project)}
                            className="p-2 bg-slate-100 dark:bg-white/5 hover:bg-red-500 hover:text-white rounded-lg transition-colors"
                            title="Delete"
                        >
                            <Trash2 className="w-4 h-4" />
                        </button>
                    </>
                ) : (
                    <span className="text-xs text-slate-400 italic pr-2">Read Only</span>
                )}
            </div>
        </div>
    );
};