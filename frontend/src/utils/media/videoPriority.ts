type VideoEntry = {
    element: HTMLVideoElement;
    src: string;
    autoPlay: boolean;
    visible: boolean;
    onDeactivate: () => void;
};

// Shared across React roots: only the primary on-screen video owns a source.
const entries = new Set<VideoEntry>();
let current: VideoEntry | undefined;
let observer: IntersectionObserver | undefined;
let resizeObserver: ResizeObserver | undefined;
let motion: MediaQueryList | undefined;
let frame: number | undefined;

function activate(next?: VideoEntry) {
    if (current === next) return;
    const previous = current;
    current = next;
    if (previous) {
        previous.element.pause();
        previous.element.removeAttribute('src');
        previous.element.preload = 'none';
        // pause() alone does not stop buffering. Reset to cancel the old request.
        previous.element.load();
        previous.onDeactivate();
    }
    if (next) {
        next.element.preload = 'auto';
        next.element.src = next.src;
        // A fresh load starts each appearance at the beginning.
        next.element.load();
        if (next.autoPlay) void next.element.play().catch(() => {});
    }
}

function choose() {
    if (frame !== undefined) cancelAnimationFrame(frame);
    frame = undefined;
    let best: VideoEntry | undefined;
    let bestDistance = Infinity;
    for (const entry of entries) {
        const { element } = entry;
        // Explicit playback in a floating/fullscreen player takes precedence.
        if (document.pictureInPictureElement === element || document.fullscreenElement === element) {
            activate(entry);
            return;
        }
        if (document.hidden || !entry.visible || (entry.autoPlay && motion?.matches)) continue;
        const rect = element.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0 || rect.bottom <= 0 || rect.top >= innerHeight || rect.right <= 0 || rect.left >= innerWidth) continue;
        if (current === entry && !entry.autoPlay && !element.paused && !element.ended) {
            activate(entry);
            return;
        }
        const distance = Math.hypot((rect.top + rect.bottom) / 2 - innerHeight / 2, (rect.left + rect.right) / 2 - innerWidth / 2);
        // Keep the current selection across small layout/scroll fluctuations.
        const score = distance - (entry === current ? 40 : 0);
        if (score < bestDistance) {
            best = entry;
            bestDistance = score;
        }
    }
    activate(best);
}

function schedule() {
    if (frame === undefined) frame = requestAnimationFrame(choose);
}

export function registerPriorityVideo(element: HTMLVideoElement, src: string, autoPlay: boolean, onDeactivate: () => void) {
    if (!entries.size) {
        motion = window.matchMedia('(prefers-reduced-motion: reduce)');
        motion.addEventListener('change', choose);
        if (typeof IntersectionObserver !== 'undefined') {
            observer = new IntersectionObserver(changes => {
                for (const change of changes) {
                    const entry = [...entries].find(candidate => candidate.element === change.target);
                    if (entry) entry.visible = change.isIntersecting;
                }
                schedule();
            });
        }
        if (typeof ResizeObserver !== 'undefined') {
            resizeObserver = new ResizeObserver(schedule);
            resizeObserver.observe(document.documentElement);
        }
        document.addEventListener('scroll', schedule, { capture: true, passive: true });
        window.addEventListener('resize', schedule, { passive: true });
        document.addEventListener('visibilitychange', choose);
        document.addEventListener('fullscreenchange', schedule);
    }
    const entry: VideoEntry = { element, src, autoPlay, visible: !observer, onDeactivate };
    const interact = () => {
        if (!autoPlay && !document.hidden) activate(entry);
    };
    entries.add(entry);
    observer?.observe(element);
    resizeObserver?.observe(element);
    element.addEventListener('pointerdown', interact);
    element.addEventListener('focus', interact);
    element.addEventListener('pause', schedule);
    element.addEventListener('ended', schedule);
    element.addEventListener('enterpictureinpicture', schedule);
    element.addEventListener('leavepictureinpicture', schedule);
    schedule();
    return () => {
        entries.delete(entry);
        observer?.unobserve(element);
        resizeObserver?.unobserve(element);
        element.removeEventListener('pointerdown', interact);
        element.removeEventListener('focus', interact);
        element.removeEventListener('pause', schedule);
        element.removeEventListener('ended', schedule);
        element.removeEventListener('enterpictureinpicture', schedule);
        element.removeEventListener('leavepictureinpicture', schedule);
        if (current === entry) activate();
        if (entries.size) schedule();
        else {
            observer?.disconnect();
            observer = undefined;
            resizeObserver?.disconnect();
            resizeObserver = undefined;
            motion?.removeEventListener('change', choose);
            motion = undefined;
            document.removeEventListener('scroll', schedule, true);
            window.removeEventListener('resize', schedule);
            document.removeEventListener('visibilitychange', choose);
            document.removeEventListener('fullscreenchange', schedule);
            if (frame !== undefined) cancelAnimationFrame(frame);
            frame = undefined;
        }
    };
}
