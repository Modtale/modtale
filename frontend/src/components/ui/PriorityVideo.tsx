import React, { useEffect, useRef, useState } from 'react';
import { registerPriorityVideo } from '@/utils/media/videoPriority';

type Props = Omit<React.VideoHTMLAttributes<HTMLVideoElement>, 'src' | 'preload' | 'children'> & {
    src: string;
    revealOnPlayback?: boolean;
};

export function PriorityVideo({ src, autoPlay = false, revealOnPlayback = false, style, onPlaying, ...props }: Props) {
    const ref = useRef<HTMLVideoElement>(null);
    const [ready, setReady] = useState(false);
    useEffect(() => {
        setReady(false);
        if (!ref.current || !src) return;
        return registerPriorityVideo(ref.current, src, autoPlay, () => setReady(false));
    }, [src, autoPlay]);
    return <video {...props} ref={ref} preload="none"
        style={{ ...style, ...(revealOnPlayback ? { opacity: ready ? 1 : 0 } : {}) }}
        onPlaying={event => { setReady(true); onPlaying?.(event); }} />;
}
