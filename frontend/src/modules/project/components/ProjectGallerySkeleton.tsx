import { X } from 'lucide-react';
import { SkeletonSurface } from '@/components/ui/Skeleton';
import { ModalPortal } from '@/components/ui/ModalPortal';
import { theme } from '@/styles/theme';
import { useScrollLock } from '@/hooks/useScrollLock';
import { GalleryCarouselViewer } from './GalleryCarouselViewer';
import { PROJECT_LOADING_IMAGE, loadingNoop } from './projectLoadingData';

/** Same viewer and media/thumbnail dimensions as the routed gallery. */
export function ProjectGallerySkeleton({ isInline = false, onClose = loadingNoop }: { isInline?: boolean; onClose?: () => void }) {
    useScrollLock(!isInline);
    const content = <div className="relative w-full max-w-6xl" onClick={e => e.stopPropagation()}>
        <button type="button" aria-label="Close gallery" onClick={onClose}
            className="absolute right-3 top-3 z-20 flex h-10 w-10 items-center justify-center rounded-full border border-blue-200/50 bg-blue-950/75 text-white shadow-lg backdrop-blur-sm transition-colors hover:bg-blue-900 focus:outline-none focus:ring-2 focus:ring-modtale-accent"><X className="h-5 w-5" aria-hidden="true" /></button>
        <SkeletonSurface label="Loading gallery"><GalleryCarouselViewer
            images={[1, 2, 3].map(index => ({ url: `${PROJECT_LOADING_IMAGE}#${index}`, caption: 'Gallery image caption' }))}
            title="Project" autoAdvance={false} activeIndex={0}
            className="mb-0 overflow-hidden rounded-2xl border border-blue-200 bg-slate-50 shadow-xl shadow-blue-950/20 dark:border-blue-400/20 dark:bg-[#0B1120]"
            mediaClassName="relative aspect-video max-h-[calc(90dvh-8rem)] bg-slate-200 outline-none dark:bg-slate-950" />
        </SkeletonSurface>
    </div>;
    return isInline ? content : <ModalPortal><div className={theme.components.modalOverlay} onClick={onClose}>{content}</div></ModalPortal>;
}
