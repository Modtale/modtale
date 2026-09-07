import type { ReactNode } from 'react';

/** Decorative placeholders share one restrained treatment in both themes. */
export function Skeleton({ className = '' }: { className?: string }) {
    return <div aria-hidden="true" className={`skeleton rounded-md ${className}`} />;
}

export function SkeletonGroup({ children, className = '', label = 'Loading content' }: {
    children: ReactNode; className?: string; label?: string;
}) {
    return <div role="status" aria-busy="true" className={className}>
        <span className="sr-only">{label}</span>
        <div aria-hidden="true">{children}</div>
    </div>;
}

export function ContentSkeleton({ rows = 4, className = '', label = 'Loading content' }: {
    rows?: number; className?: string; label?: string;
}) {
    return <SkeletonGroup label={label} className={`w-full p-6 ${className}`}>
        <div className="space-y-5">
            {Array.from({ length: rows }, (_, index) => <div key={index} className="flex items-start gap-4">
                <Skeleton className="h-11 w-11 shrink-0 rounded-xl" />
                <div className="flex-1 space-y-3 py-1">
                    <Skeleton className={index % 2 ? 'h-4 w-2/5' : 'h-4 w-1/3'} />
                    <Skeleton className="h-3 w-5/6" />
                    <Skeleton className="h-3 w-3/5" />
                </div>
            </div>)}
        </div>
    </SkeletonGroup>;
}

export function PageSkeleton({ profile = false }: { profile?: boolean }) {
    return <SkeletonGroup className="mx-auto min-h-[65vh] w-full max-w-7xl px-4 py-8 sm:px-8" label={profile ? 'Loading profile' : 'Loading page'}>
        <div className="space-y-8">
            <Skeleton className="h-36 w-full rounded-2xl sm:h-48" />
            <div className="flex items-center gap-5">
                <Skeleton className={`h-20 w-20 shrink-0 ${profile ? 'rounded-full' : 'rounded-2xl'}`} />
                <div className="flex-1 space-y-3"><Skeleton className="h-6 w-2/5" /><Skeleton className="h-4 w-1/4" /></div>
            </div>
            <div className="flex gap-4 border-b border-slate-200 pb-4 dark:border-white/10">
                {[0, 1, 2].map(index => <Skeleton key={index} className="h-4 w-20" />)}
            </div>
            <div className="grid gap-8 md:grid-cols-[minmax(0,1fr)_16rem]">
                <ContentSkeleton rows={4} className="!p-0" />
                <div className="hidden space-y-4 md:block"><Skeleton className="h-5 w-1/2" /><Skeleton className="h-3 w-full" /><Skeleton className="h-3 w-4/5" /><Skeleton className="h-3 w-3/5" /></div>
            </div>
        </div>
    </SkeletonGroup>;
}
