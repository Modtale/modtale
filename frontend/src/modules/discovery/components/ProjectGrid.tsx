import React, { useEffect, useMemo, useRef, useState } from 'react';
import { PackageSearch } from 'lucide-react';
import { ProjectCard } from '@/modules/project/components/ProjectCard';
import { EmptyState } from '@/components/ui/EmptyState';
import { SiteRoutes } from '@/utils/routes';
import { BACKEND_URL } from '@/utils/api';
import { getCloudflareUrl } from '@/utils/images';
import type { Project } from '@/types';
import { BrowseSkeletons, CompactSkeletonCard, GridSkeletonCard, ListSkeletonCard } from './BrowseSkeletons';

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
    const itemIdsKey = useMemo(() => items.map((item) => item.id).join('|'), [items]);
    const [readyCount, setReadyCount] = useState(() => (loading ? 0 : items.length));
    const hasInitializedRef = useRef(false);

    useEffect(() => {
        if (!hasInitializedRef.current) {
            hasInitializedRef.current = true;
            return;
        }
        setReadyCount(0);
    }, [itemIdsKey]);

    useEffect(() => {
        if (readyCount >= items.length || typeof window === 'undefined') return;

        const nextItem = items[readyCount];
        const rawImage = nextItem?.imageUrl
            ? (nextItem.imageUrl.startsWith('/api') ? `${BACKEND_URL}${nextItem.imageUrl}` : nextItem.imageUrl)
            : '/assets/favicon.svg';
        const baseWidth = viewStyle === 'compact' ? 48 : viewStyle === 'list' ? 128 : 80;
        const preloadSrc = getCloudflareUrl(rawImage, baseWidth, 80);
        const img = new Image();
        let settled = false;

        const completeCurrentCard = () => {
            if (settled) return;
            settled = true;
            setReadyCount((current) => {
                if (current !== readyCount) return current;
                return Math.min(current + 1, items.length);
            });
        };

        img.onload = completeCurrentCard;
        img.onerror = completeCurrentCard;
        img.src = preloadSrc;

        if (img.complete && img.naturalWidth > 0) {
            completeCurrentCard();
        }

        return () => {
            settled = true;
            img.onload = null;
            img.onerror = null;
        };
    }, [items, readyCount, viewStyle]);

    const renderSkeletonCard = () => {
        if (viewStyle === 'grid') return <GridSkeletonCard />;
        if (viewStyle === 'list') return <ListSkeletonCard />;
        return <CompactSkeletonCard />;
    };

    if (loading && items.length === 0) {
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
                const isCardReady = index < readyCount;

                return (
                    <div key={item.id} className={isPriority ? "" : "animate-in fade-in zoom-in-95 duration-500 fill-mode-backwards"} style={isPriority ? {} : { animationDelay: `${(index - 6) * 50}ms` }}>
                        {isCardReady ? (
                            <ProjectCard
                                project={item}
                                path={SiteRoutes.project(item)}
                                isFavorite={likedProjectIds.includes(item.id)}
                                onToggleFavorite={onToggleFavorite}
                                isLoggedIn={isLoggedIn}
                                priority={isPriority}
                                viewStyle={viewStyle}
                                isVisible={true}
                            />
                        ) : (
                            renderSkeletonCard()
                        )}
                    </div>
                );
            })}
        </div>
    );
};
