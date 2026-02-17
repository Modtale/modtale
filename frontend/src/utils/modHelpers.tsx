import React from 'react';
import { CheckCircle2, Beaker, Zap, Code, Database, Palette, Save, Layers, Layout } from 'lucide-react';

export const formatTimeAgo = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    let interval = seconds / 31536000;
    if (interval > 1) return Math.floor(interval) + "y ago";
    interval = seconds / 2592000;
    if (interval > 1) return Math.floor(interval) + "mo ago";
    interval = seconds / 86400;
    if (interval > 1) return Math.floor(interval) + "d ago";
    interval = seconds / 3600;
    if (interval > 1) return Math.floor(interval) + "h ago";
    interval = seconds / 60;
    if (interval > 1) return Math.floor(interval) + "m ago";
    return "Just now";
};

export const ChannelBadge = ({ channel }: { channel?: string }) => {
    const baseClasses = "inline-flex items-center gap-1 text-[9px] font-black uppercase px-1.5 py-0.5 rounded border w-fit whitespace-nowrap";

    if (!channel || channel === 'RELEASE') return <span className={`${baseClasses} bg-green-100 text-green-700 dark:bg-green-500/20 dark:text-green-400 border-green-200 dark:border-green-500/30`}><CheckCircle2 className="w-2.5 h-2.5" /> Release</span>;
    if (channel === 'BETA') return <span className={`${baseClasses} bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-400 border-blue-200 dark:border-blue-500/30`}><Beaker className="w-2.5 h-2.5" /> Beta</span>;
    return <span className={`${baseClasses} bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-400 border-orange-200 dark:border-orange-500/30`}><Zap className="w-2.5 h-2.5" /> Alpha</span>;
};

export const compareSemVer = (a: string, b: string) => {
    const semVerRegex = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/;

    const parse = (v: string) => {
        const match = v.match(semVerRegex);
        if (!match) return null;
        return {
            major: parseInt(match[1], 10),
            minor: parseInt(match[2], 10),
            patch: parseInt(match[3], 10),
            prerelease: match[4] ? match[4].split('.') : [],
            build: match[5]
        };
    };

    const va = parse(a);
    const vb = parse(b);

    if (!va || !vb) return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });

    if (va.major !== vb.major) return va.major > vb.major ? 1 : -1;
    if (va.minor !== vb.minor) return va.minor > vb.minor ? 1 : -1;
    if (va.patch !== vb.patch) return va.patch > vb.patch ? 1 : -1;

    if (va.prerelease.length === 0 && vb.prerelease.length > 0) return 1;
    if (va.prerelease.length > 0 && vb.prerelease.length === 0) return -1;
    if (va.prerelease.length === 0 && vb.prerelease.length === 0) return 0; // Both have no pre-release (equality, ignoring build)

    let i = 0;
    while (i < va.prerelease.length && i < vb.prerelease.length) {
        const idA = va.prerelease[i];
        const idB = vb.prerelease[i];
        const isNumA = /^\d+$/.test(idA);
        const isNumB = /^\d+$/.test(idB);

        if (isNumA && isNumB) {
            const numA = parseInt(idA, 10);
            const numB = parseInt(idB, 10);
            if (numA !== numB) return numA > numB ? 1 : -1;
        } else if (!isNumA && !isNumB) {
            if (idA !== idB) return idA.localeCompare(idB);
        } else {
            return isNumA ? -1 : 1;
        }
        i++;
    }

    if (va.prerelease.length !== vb.prerelease.length) {
        return va.prerelease.length > vb.prerelease.length ? 1 : -1;
    }

    return 0;
};

export const getClassificationIcon = (cls: string) => {
    switch(cls) {
        case 'PLUGIN': return <Code className="w-4 h-4" />;
        case 'ART': return <Palette className="w-4 h-4" />;
        case 'DATA': return <Database className="w-4 h-4" />;
        case 'SAVE': return <Save className="w-4 h-4" />;
        case 'MODPACK': return <Layers className="w-4 h-4" />;
        default: return <Layout className="w-4 h-4" />;
    }
};

export const DiscordIcon = ({ className }: { className?: string }) => (
    <svg className={className} fill="currentColor" viewBox="0 0 127.14 96.36" xmlns="http://www.w3.org/2000/svg"><path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" /></svg>
);

export const getLicenseInfo = (license: string) => {
    const l = license.toUpperCase().replace(/\s+/g, '');
    if (l.includes('MIT')) return { name: 'MIT', url: 'https://opensource.org/licenses/MIT' };
    if (l.includes('APACHE')) return { name: 'Apache 2.0', url: 'https://opensource.org/licenses/Apache-2.0' };
    if (l.includes('GPL')) return { name: 'GPL', url: 'https://www.gnu.org/licenses/gpl-3.0.en.html' };
    if (l.includes('CC0')) return { name: 'CC0', url: 'https://creativecommons.org/publicdomain/zero/1.0/' };
    return { name: license, url: null };
};