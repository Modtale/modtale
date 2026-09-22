import React from 'react';
import { PriorityVideo } from '@/components/ui/PriorityVideo';
import versions from '@/data/newsMediaVersions.json';

export type LauncherDemoClip = keyof typeof versions;
const screenshots = new Set<LauncherDemoClip>(['pack-contents', 'pack-unlock']);

/** Decode only visible walkthroughs; preserve the poster until a frame is ready. */
export const LauncherDemo = ({ clip, alt, still = false }: { clip: LauncherDemoClip; alt: string; still?: boolean }) => {
    const staticImage = still || screenshots.has(clip);
    const poster = `/assets/news/${clip}.jpg?v=${versions[clip]}`;
    return <div className="launcher-demo-media" style={{ position: 'relative', aspectRatio: '16 / 10', overflow: 'hidden' }}>
        <img src={poster} alt={alt} width={2560} height={1600} loading="lazy" decoding="async"
            style={{ display: 'block', width: '100%', height: '100%' }} />
        {!staticImage && <PriorityVideo key={clip} src={`/assets/news/${clip}.mp4?v=${versions[clip]}`}
            poster={poster} muted loop playsInline autoPlay revealOnPlayback disablePictureInPicture disableRemotePlayback
            aria-label={alt}
            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', display: 'block', pointerEvents: 'none' }} />}
    </div>;
};
