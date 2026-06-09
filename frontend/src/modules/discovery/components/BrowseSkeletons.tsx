import React from 'react';

type ViewStyle = 'grid' | 'list' | 'compact';

interface BrowseSkeletonsProps {
    viewStyle: ViewStyle;
    count: number;
}

const shimmer = "absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/40 dark:via-white/10 to-transparent";
const pulse = "animate-pulse bg-slate-200/80 dark:bg-slate-800/60";

export const GridSkeletonCard = () => (
    <div className="relative overflow-hidden rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900">
        <div className={`aspect-[3/1] ${pulse}`} />
        <div className="px-6 pb-6">
            <div className="-mt-10 mb-3 h-20 w-20 rounded-2xl border-4 border-white dark:border-slate-800 bg-slate-300/80 dark:bg-slate-700/70" />
            <div className={`h-6 w-3/5 rounded-lg ${pulse}`} />
            <div className={`mt-2 h-4 w-2/5 rounded-md ${pulse}`} />
            <div className={`mt-4 h-4 w-full rounded-md ${pulse}`} />
            <div className={`mt-2 h-4 w-4/5 rounded-md ${pulse}`} />
            <div className="mt-5 flex items-center justify-between">
                <div className="flex gap-3">
                    <div className={`h-4 w-16 rounded-md ${pulse}`} />
                    <div className={`h-4 w-14 rounded-md ${pulse}`} />
                </div>
                <div className={`h-4 w-16 rounded-md ${pulse}`} />
            </div>
        </div>
        <div className={shimmer} />
    </div>
);

export const ListSkeletonCard = () => (
    <div className="relative overflow-hidden rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-4 sm:p-5">
        <div className="flex items-center sm:items-start gap-4 sm:gap-6">
            <div className={`h-24 w-24 sm:h-32 sm:w-32 rounded-xl shrink-0 ${pulse}`} />
            <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-4">
                    <div className="w-full">
                        <div className={`h-6 w-2/5 rounded-lg ${pulse}`} />
                        <div className={`mt-2 h-4 w-1/4 rounded-md ${pulse}`} />
                    </div>
                    <div className={`hidden sm:block h-8 w-24 rounded-lg ${pulse}`} />
                </div>
                <div className={`mt-3 h-4 w-full rounded-md ${pulse}`} />
                <div className={`mt-2 h-4 w-4/5 rounded-md ${pulse}`} />
                <div className="mt-5 flex gap-4 sm:gap-6">
                    <div className={`h-4 w-16 rounded-md ${pulse}`} />
                    <div className={`h-4 w-14 rounded-md ${pulse}`} />
                    <div className={`h-4 w-16 rounded-md ${pulse}`} />
                </div>
            </div>
        </div>
        <div className={shimmer} />
    </div>
);

export const CompactSkeletonCard = () => (
    <div className="relative overflow-hidden rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-slate-900 p-3">
        <div className="flex items-center gap-4">
            <div className={`h-12 w-12 rounded-lg shrink-0 ${pulse}`} />
            <div className="flex-1 min-w-0">
                <div className={`h-4 w-1/2 rounded-md ${pulse}`} />
                <div className={`mt-2 h-3 w-1/3 rounded-md ${pulse}`} />
            </div>
            <div className="hidden sm:flex flex-col gap-1.5 items-end">
                <div className={`h-3 w-20 rounded-md ${pulse}`} />
                <div className={`h-3 w-16 rounded-md ${pulse}`} />
            </div>
            <div className={`h-4 w-4 rounded ${pulse}`} />
        </div>
        <div className={shimmer} />
    </div>
);

export const BrowseSkeletons: React.FC<BrowseSkeletonsProps> = ({ viewStyle, count }) => {
    const containerClassName = viewStyle === 'grid'
        ? "grid grid-cols-1 md:grid-cols-2 min-[1800px]:grid-cols-3 gap-4 md:gap-6 mt-4"
        : viewStyle === 'compact'
            ? "grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mt-4"
            : "space-y-4 mt-4";

    return (
        <div className={containerClassName} aria-hidden="true">
            {[...Array(count)].map((_, i) => (
                <div key={i}>
                    {viewStyle === 'grid' && <GridSkeletonCard />}
                    {viewStyle === 'list' && <ListSkeletonCard />}
                    {viewStyle === 'compact' && <CompactSkeletonCard />}
                </div>
            ))}
        </div>
    );
};
