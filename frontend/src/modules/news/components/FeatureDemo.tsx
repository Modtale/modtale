import React from 'react';
import { LauncherDemo, type LauncherDemoClip } from '@/modules/launcher/components/LauncherDemo';

export function FeatureDemo({ clip, alt, still = false }: { clip: LauncherDemoClip; alt: string; still?: boolean }) {
    return (
        <figure className="news-demo">
            <LauncherDemo clip={clip} alt={alt} still={still} />
        </figure>
    );
}
