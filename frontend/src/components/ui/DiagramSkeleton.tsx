import { Skeleton } from './Skeleton';

/** A diagram occupies a media frame, never the avatar/list layout used by feeds. */
export function DiagramSkeleton() {
    return <div role="status" aria-label="Loading diagram" aria-busy="true"
        className="my-6 p-4 bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-white/10 shadow-sm overflow-x-auto flex justify-center mermaid-container">
        <Skeleton className="h-40 w-full max-w-2xl" />
    </div>;
}
