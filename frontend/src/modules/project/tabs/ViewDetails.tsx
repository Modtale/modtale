import React, { Suspense, lazy } from 'react';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import type { Project, ProjectDependency, User } from '@/types';
import { ProjectMetaSections } from '../components/ProjectMetaSections';

const CommentSection = lazy(() => import('../components/CommentSection').then((module) => ({ default: module.CommentSection })));

interface ViewDetailsProps {
    project: Project;
    authorProfile?: User | null;
    currentUser: User | null;
    canEdit: boolean;
    commentsRef: React.RefObject<HTMLDivElement | null>;
    setProject: React.Dispatch<React.SetStateAction<Project | null>>;
    setStatusModal: (data: { type: 'success' | 'error' | 'warning' | 'info'; title: string; message: string }) => void;
    onRefresh: () => Promise<void>;
    dependencies?: ProjectDependency[];
    incompatibleProjectIds?: string[];
    depMeta: Record<string, { icon: string, title: string, classification?: string, slug?: string }>;
    showMetaSections?: boolean;
}

export const ViewDetails: React.FC<ViewDetailsProps> = ({ project, authorProfile, currentUser, canEdit, commentsRef, setProject, setStatusModal, onRefresh, dependencies, incompatibleProjectIds, depMeta, showMetaSections = false }) => {
    return (
        <>
            <div className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden">
                <MarkdownRenderer content={project.about || "*No description.*"} />
            </div>
            {showMetaSections && (
                <div className="mt-8 pt-8 border-t border-slate-200 dark:border-white/5">
                    <ProjectMetaSections project={project} dependencies={dependencies} incompatibleProjectIds={incompatibleProjectIds} depMeta={depMeta} />
                </div>
            )}
            <Suspense fallback={<div ref={commentsRef} id="comments" className="mt-12 pt-10 scroll-mt-24 border-t border-slate-200 dark:border-white/5" />}>
                <CommentSection
                    projectId={project.id}
                    project={project}
                    authorProfile={authorProfile}
                    comments={project.comments || []}
                    currentUser={currentUser}
                    isCreator={canEdit}
                    commentsDisabled={project.allowComments === false}
                    onCommentsUpdated={(c) => { setProject(prev => prev ? { ...prev, comments: c } : null); if (onRefresh) onRefresh(); }}
                    onError={(msg) => setStatusModal({ type: 'error', title: 'Comment Action Failed', message: msg })}
                    onSuccess={(msg) => setStatusModal({ type: 'success', title: 'Action Complete', message: msg })}
                    innerRef={commentsRef}
                    onReport={() => setStatusModal({
                        type: 'info',
                        title: 'Comment Reporting Not Ready',
                        message: 'Comment-specific reporting is not wired up in this view yet. For now, please report the project and mention the comment details in your report notes.'
                    })}
                />
            </Suspense>
        </>
    );
};
