import { Skeleton, SkeletonGroup } from '@/components/ui/Skeleton';
import '../styles/news-card.css';
import '../styles/news-skeleton.css';

export function NewsFeedSkeleton() {
    return <SkeletonGroup label="Loading news" className="news-skeleton">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {[0, 1, 2, 3].map(index => <div key={index} className="news-card news-skeleton-card">
                <Skeleton className="news-skeleton-media aspect-[40/21] !rounded-none" />
                <div className="news-card-copy">
                    <Skeleton className="h-3 w-28 my-[3px]" />
                    <div className="mt-3 space-y-2.5">
                        <Skeleton className={`h-5 ${index % 2 ? 'w-4/5' : 'w-11/12'}`} />
                        <Skeleton className={`h-5 ${index % 2 ? 'w-1/2' : 'w-2/3'}`} />
                    </div>
                </div>
            </div>)}
        </div>
    </SkeletonGroup>;
}

export function NewsArticleSkeleton() {
    return <SkeletonGroup label="Loading article" className="news-skeleton">
        <div className="news-article-intro">
            <div className="news-heading">
                <div className="news-skeleton-title space-y-3">
                    <Skeleton className="w-11/12 mx-auto" />
                    <Skeleton className="w-2/3 mx-auto" />
                </div>
                <div className="news-deck space-y-3 py-1.5">
                    <Skeleton className="h-[18px] w-full" />
                    <Skeleton className="h-[18px] w-4/5" />
                </div>
                <div className="news-byline">
                    <Skeleton className="size-7 shrink-0 !rounded-lg" />
                    <Skeleton className="h-3 w-20" />
                    <i />
                    <Skeleton className="h-3 w-28" />
                    <i />
                    <Skeleton className="h-3 w-16" />
                </div>
            </div>
            <Skeleton className="news-skeleton-media aspect-[40/21] !rounded-2xl border border-slate-200 dark:border-white/10" />
        </div>
        <div className="news-reading-layout">
            <div className="news-copy news-prose space-y-8">
                <div className="space-y-4">
                    <Skeleton className="h-5 w-full" />
                    <Skeleton className="h-5 w-11/12" />
                    <Skeleton className="h-5 w-3/4" />
                </div>
                <div className="space-y-4">
                    <Skeleton className="h-7 w-1/2 mb-6" />
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-5/6" />
                </div>
            </div>
        </div>
    </SkeletonGroup>;
}
