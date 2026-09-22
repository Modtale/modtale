import type { ReactNode, MouseEventHandler } from 'react';
import { SkeletonSurface } from '@/components/ui/Skeleton';

/** Keeps a section's own flex/grid box when masking its real children. */
export function ProjectLoadingRegion({ loading, children, className = '', label = 'Loading project content', onClick }: {
    loading: boolean; children: ReactNode; className?: string; label?: string; onClick?: MouseEventHandler<HTMLDivElement>;
}) {
    return loading
        ? <SkeletonSurface label={label} className={`${className} [&>.skeleton-layout]:contents`}>{children}</SkeletonSurface>
        : <div className={className} onClick={onClick}>{children}</div>;
}
