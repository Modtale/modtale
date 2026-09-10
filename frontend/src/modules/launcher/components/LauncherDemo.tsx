import React, { useEffect, useRef, useState } from 'react';
import versions from '@/data/newsMediaVersions.json';

export type LauncherDemoClip = keyof typeof versions;
const screenshots = new Set<LauncherDemoClip>(['pack-contents', 'pack-unlock']);

/** Decode only visible walkthroughs; preserve the poster until a frame is ready. */
export const LauncherDemo = ({ clip, alt, still = false }: { clip: LauncherDemoClip; alt: string; still?: boolean }) => {
    const container = useRef<HTMLDivElement>(null);
    const video = useRef<HTMLVideoElement>(null);
    const [loaded, setLoaded] = useState(false);
    const [ready, setReady] = useState(false);
    const [active, setActive] = useState(false);
    const staticImage = still || screenshots.has(clip);
    useEffect(() => {
        setLoaded(false);
        setReady(false);
        setActive(false);
        if (staticImage) return;
        const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
        let visible = false;
        const update = () => {
            const play = visible && !preference.matches && !document.hidden;
            setActive(play);
            if (play) setLoaded(true);
        };
        const observer = new IntersectionObserver(([entry]) => {
            visible = entry.isIntersecting && entry.intersectionRatio >= 0.2;
            update();
        }, { threshold: [0, 0.2] });
        if (container.current) observer.observe(container.current);
        preference.addEventListener('change', update);
        document.addEventListener('visibilitychange', update);
        return () => {
            observer.disconnect();
            preference.removeEventListener('change', update);
            document.removeEventListener('visibilitychange', update);
        };
    }, [clip, staticImage]);
    useEffect(() => {
        const element = video.current;
        if (!element) return;
        element.defaultPlaybackRate = 1.15;
        element.playbackRate = 1.15;
        if (active && loaded) void element.play().catch(() => setReady(false));
        else element.pause();
    }, [active, loaded, clip]);
    const poster = `/assets/news/${clip}.jpg?v=${versions[clip]}`;
    return <div ref={container} className="launcher-demo-media" style={{ position: 'relative', aspectRatio: '16 / 10', overflow: 'hidden' }}>
        <img src={poster} alt={alt} width={2560} height={1600} loading="lazy" decoding="async"
            style={{ display: 'block', width: '100%', height: '100%' }} />
        {!staticImage && <video ref={video} key={clip} src={loaded ? `/assets/news/${clip}.mp4?v=${versions[clip]}` : undefined}
            poster={poster} muted loop playsInline preload="none" disablePictureInPicture disableRemotePlayback
            aria-label={alt} onPlaying={() => setReady(true)}
            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', display: 'block', opacity: ready && active ? 1 : 0, pointerEvents: 'none' }} />}
    </div>;
};
