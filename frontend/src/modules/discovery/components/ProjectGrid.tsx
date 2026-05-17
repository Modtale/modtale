import React from 'react';
import { PackageSearch } from 'lucide-react';
import { theme } from '@/styles/theme';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { getProjectUrl } from '@/utils/slug';
import type { Project } from '@/types';

interface ProjectGridProps {
    items: Project[];
    loading: boolean;
    viewStyle: 'grid' | 'list' | 'compact';
    itemsPerPage: number;
    likedProjectIds: string[];
    onToggleFavorite: (id: string) => void;
    isLoggedIn: boolean;
    onProjectSelect: (item: Project) => void;
}

export const ProjectGrid: React.FC<ProjectGridProps> = ({
                                                            items, loading, viewStyle, itemsPerPage, likedProjectIds, onToggleFavorite, isLoggedIn, onProjectSelect
                                                        }) => {
    if (loading) {
        return (
            <div className={viewStyle === 'grid' ? "grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-5 mt-4" : viewStyle === 'compact' ? "grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4" : "space-y-4 mt-4"}>
                {[...Array(itemsPerPage)].map((_, i) => (
                    <div key={i} className={`${viewStyle === 'grid' ? 'h-[154px]' : viewStyle === 'list' ? 'h-32' : 'h-16'} bg-white/40 dark:bg-white/5 backdrop-blur-md rounded-2xl animate-pulse border ${theme.colors.border} relative overflow-hidden`}>
                        <div className="absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/20 to-transparent"></div>
                    </div>
                ))}
            </div>
        );
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
                            path={getProjectUrl(item)}
                            isFavorite={likedProjectIds.includes(item.id)}
                            onToggleFavorite={onToggleFavorite}
                            isLoggedIn={isLoggedIn}
                            onClick={() => onProjectSelect(item)}
                            priority={isPriority}
                            viewStyle={viewStyle}
                        />
                    </div>
                );
            })}
        </div>
    );
};