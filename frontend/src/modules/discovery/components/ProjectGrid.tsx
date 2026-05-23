import React from 'react';
import { PackageSearch } from 'lucide-react';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { SiteRoutes } from '@/utils/routes';
import type { Project } from '@/types';
import { BrowseSkeletons } from './BrowseSkeletons';

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
    if (loading) {
        return <BrowseSkeletons viewStyle={viewStyle} count={itemsPerPage} />;
    }

    if (items.length === 0) {
        return (
            <div className="mt-8 animate-in fade-in zoom-in-95 duration-500">
                <EmptyState icon={PackageSearch} title="No matches found" message="Try adjusting your search terms or filters to find what you're looking for." />
            </div>
        );
    }

    return (
        <div className={viewStyle === 'grid' ? "grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-6 mt-4" : viewStyle === 'compact' ? "grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4" : "space-y-4 mt-4"}>
            {items.map((item, index) => {
                const isPriority = index < 6;
                return (
                    <div key={item.id} className={isPriority ? "" : "animate-in fade-in zoom-in-95 duration-500 fill-mode-backwards"} style={isPriority ? {} : { animationDelay: `${(index - 6) * 50}ms` }}>
                        <ProjectCard
                            project={item}
                            path={SiteRoutes.project(item)}
                            isFavorite={likedProjectIds.includes(item.id)}
                            onToggleFavorite={onToggleFavorite}
                            isLoggedIn={isLoggedIn}
                            priority={isPriority}
                            viewStyle={viewStyle}
                        />
                    </div>
                );
            })}
        </div>
    );
};
