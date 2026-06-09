import React from 'react';
import { MarkdownRenderer } from '@/components/ui/MarkdownRenderer';
import { CommentSection } from '../components/CommentSection';
import type { Project, User } from '@/types';

interface ViewDetailsProps {
    project: Project;
    currentUser: User | null;
    canEdit: boolean;
    commentsRef: React.RefObject<HTMLDivElement | null>;
    setProject: React.Dispatch<React.SetStateAction<Project | null>>;
    setStatusModal: (data: { type: 'success' | 'error' | 'warning' | 'info'; title: string; message: string }) => void;
    onRefresh: () => Promise<void>;
}

export const ViewDetails: React.FC<ViewDetailsProps> = ({ project, currentUser, canEdit, commentsRef, setProject, setStatusModal, onRefresh }) => {
    return (
        <>
            <div className="prose dark:prose-invert prose-lg max-w-none prose-code:before:hidden prose-code:after:hidden">
                <MarkdownRenderer content={project.about || "*No description.*"} />
            </div>
            <CommentSection
                projectId={project.id}
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
        </>
    );
};
