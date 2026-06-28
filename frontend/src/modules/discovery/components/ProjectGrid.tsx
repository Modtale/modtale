import React, { useMemo } from 'react';
import { PackageSearch } from 'lucide-react';
import { ProjectCard, ProjectCardSkeletons } from '@/modules/project/components/ProjectCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { SiteRoutes } from '@/utils/routes';
import type { Project } from '@/types';

interface ProjectGridProps {
    items: Project[];
    loading: boolean;
    viewStyle: 'grid' | 'list' | 'compact';
    itemsPerPage: number;
    likedProjectIds: string[];
    onToggleFavorite: (id: string) => void;
    isLoggedIn: boolean;
}

export const ProjectGrid: React.FC<ProjectGridProps> = ({
                                                            items, loading, viewStyle, itemsPerPage, likedProjectIds, onToggleFavorite, isLoggedIn
                                                        }) => {
    const likedProjectIdSet = useMemo(() => new Set(likedProjectIds), [likedProjectIds]);

    if (loading && items.length === 0) {
        return <ProjectCardSkeletons viewStyle={viewStyle} count={itemsPerPage} />;
    }

    if (items.length === 0) {
        return (
            <div className="mt-8 animate-in fade-in zoom-in-95 duration-500">
                <EmptyState icon={PackageSearch} title="No matches found" message="Try adjusting your search terms or filters to find what you're looking for." />
            </div>
        );
    }

    return (
        <div className={viewStyle === 'grid' ? "browse-project-grid" : viewStyle === 'compact' ? "grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4" : "space-y-4 mt-4"}>
            {items.map((item, index) => {
                const priorityLimit = viewStyle === 'compact' ? 4 : 2;
                const isPriority = index < priorityLimit;

                return (
                    <div key={item.id}>
                        <ProjectCard
                            project={item}
                            path={SiteRoutes.project(item)}
                            isFavorite={likedProjectIdSet.has(item.id)}
                            onToggleFavorite={onToggleFavorite}
                            isLoggedIn={isLoggedIn}
                            priority={isPriority}
                            viewStyle={viewStyle}
                            isVisible={true}
                        />
                    </div>
                );
            })}
        </div>
    );
};
