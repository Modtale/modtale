import type { ReactNode } from 'react';
import { SkeletonSurface } from '@/components/ui/Skeleton';
import { ManagedProjectCard } from '@/components/shared/ManagedProjectCard';
import type { Project, User } from '@/types';

// Neutral, network-free fixtures: no invented badges, social accounts, or review states.
export const skeletonImage = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/%3E';
export const skeletonUser: User = {
    id: 'loading-user', username: 'Creator name', avatarUrl: skeletonImage,
    likedProjectIds: [],
};
export const skeletonProject: Project = {
    id: 'loading-project', title: 'Project title', description: '',
    authorId: skeletonUser.id, author: skeletonUser.username, imageUrl: skeletonImage,
    classification: 'PLUGIN', downloadCount: 0, favoriteCount: 0,
    updatedAt: '2000-01-01T00:00:00Z', status: 'PUBLISHED', versions: [],
};

export function LoadingSurface({ loading, children, className, label }: {
    loading: boolean; children: ReactNode; className?: string; label?: string;
}) {
    return loading ? <SkeletonSurface className={className} label={label}>{children}</SkeletonSurface> : <>{children}</>;
}

export function ManagedProjectsSkeleton({ count = 3, canManage = true, isOwner = false, showAuthor = false, cardClassName }: {
    count?: number; canManage?: boolean; isOwner?: boolean; showAuthor?: boolean; cardClassName: string;
}) {
    return <SkeletonSurface label="Loading projects">
        <div className="grid grid-cols-1 gap-4">
            {Array.from({ length: count }, (_, index) => <div key={index} className={cardClassName}>
                <ManagedProjectCard project={skeletonProject} canManage={canManage} isOwner={isOwner}
                    showAuthor={showAuthor} onTransfer={() => {}} onDelete={() => {}} />
            </div>)}
        </div>
    </SkeletonSurface>;
}
