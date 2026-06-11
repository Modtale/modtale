import React from 'react';
import { CheckCircle2, Beaker, Zap, Code, Database, Paintbrush, Globe, Layers, Layout } from 'lucide-react';
import { DiscordBrandIcon } from '@/components/ui/icons/BrandIcons';

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

export const formatDateTime = (dateString?: string | null) => {
    if (!dateString) return 'Unknown';

    const date = new Date(dateString);
    if (Number.isNaN(date.getTime())) return dateString;

    return new Intl.DateTimeFormat(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit'
    }).format(date);
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
    if (va.prerelease.length === 0 && vb.prerelease.length === 0) return 0;

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

export const getClassificationIcon = (cls: string, className: string = "w-4 h-4") => {
    switch (cls) {
        case 'PLUGIN': return <Code className={className} />;
        case 'ART': return <Paintbrush className={className} />;
        case 'DATA': return <Database className={className} />;
        case 'SAVE': return <Globe className={className} />;
        case 'MODPACK': return <Layers className={className} />;
        default: return <Layout className={className} />;
    }
};

export const DiscordIcon = DiscordBrandIcon;

export const getLicenseInfo = (license: string) => {
    const l = license.toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (l.includes('MIT')) return { name: 'MIT', url: 'https://opensource.org/licenses/MIT' };
    if (l.includes('APACHE')) return { name: 'Apache 2.0', url: 'https://opensource.org/licenses/Apache-2.0' };
    if (l.includes('LGPL')) return { name: 'LGPL v3', url: 'https://www.gnu.org/licenses/lgpl-3.0.en.html' };
    if (l.includes('AGPL')) return { name: 'AGPL v3', url: 'https://www.gnu.org/licenses/agpl-3.0.en.html' };
    if (l.includes('GPL')) return { name: 'GPL v3', url: 'https://www.gnu.org/licenses/gpl-3.0.en.html' };
    if (l.includes('MPL')) return { name: 'MPL 2.0', url: 'https://opensource.org/licenses/MPL-2.0' };
    if (l.includes('BSD')) return { name: 'BSD 3-Clause', url: 'https://opensource.org/licenses/BSD-3-Clause' };
    if (l.includes('CC0')) return { name: 'CC0', url: 'https://creativecommons.org/publicdomain/zero/1.0/' };
    if (l.includes('CCBYNCND')) return { name: 'CC BY-NC-ND 4.0', url: 'https://creativecommons.org/licenses/by-nc-nd/4.0/' };
    if (l.includes('CCBYNCSA')) return { name: 'CC BY-NC-SA 4.0', url: 'https://creativecommons.org/licenses/by-nc-sa/4.0/' };
    if (l.includes('CCBYNC')) return { name: 'CC BY-NC 4.0', url: 'https://creativecommons.org/licenses/by-nc/4.0/' };
    if (l.includes('CCBYSA')) return { name: 'CC BY-SA 4.0', url: 'https://creativecommons.org/licenses/by-sa/4.0/' };
    if (l.includes('CCBY')) return { name: 'CC BY 4.0', url: 'https://creativecommons.org/licenses/by/4.0/' };
    if (l.includes('UNLICENSE')) return { name: 'The Unlicense', url: 'https://unlicense.org/' };
    if (l.includes('ARR') || l.includes('ALLRIGHTS')) return { name: 'All Rights Reserved', url: null };
    return { name: license, url: null };
};

export const toTitleCase = (str: string) => {
    if (!str) return '';
    if (str === 'SAVE') return 'World';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
};
