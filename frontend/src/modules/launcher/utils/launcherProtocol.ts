export interface LauncherInstallTarget {
    projectId: string;
    projectHandle?: string;
    versionNumber?: string;
    gameVersion?: string;
}

export interface LauncherListInstallTarget {
    listId: string;
    shareUrl?: string;
}

interface LauncherHandoffOptions {
    timeoutMs?: number;
    windowRef?: Window;
    documentRef?: Document;
    openUrl?: (url: string) => void;
}

const LAUNCHER_PROTOCOL_TIMEOUT_MS = 2200;

export const buildLauncherInstallUrl = ({
                                            projectId,
                                            projectHandle,
                                            versionNumber,
                                            gameVersion
                                        }: LauncherInstallTarget) => {
    const params = new URLSearchParams();
    const trimmedProjectHandle = projectHandle?.trim();

    params.set('projectId', projectId);
    params.set('project', trimmedProjectHandle || projectId);

    if (versionNumber) params.set('version', versionNumber);
    if (gameVersion) params.set('gameVersion', gameVersion);

    return `modtale://install?${params.toString()}`;
};

export const buildLauncherListInstallUrl = ({ listId, shareUrl }: LauncherListInstallTarget) => {
    const params = new URLSearchParams();
    params.set('listId', listId);
    if (shareUrl) params.set('url', shareUrl);
    return `modtale://install-list?${params.toString()}`;
};

const openLauncherUrlOrFallback = (
    launcherUrl: string,
    fallback: () => void,
    options: LauncherHandoffOptions = {}
) => {
    const windowRef = options.windowRef ?? (typeof window !== 'undefined' ? window : undefined);
    const documentRef = options.documentRef ?? (typeof document !== 'undefined' ? document : undefined);

    if (!windowRef || !documentRef) {
        fallback();
        return;
    }

    const activeWindow = windowRef;
    const activeDocument = documentRef;
    let handedOff = false;
    let timeoutId: number | undefined;

    const cleanup = () => {
        activeWindow.removeEventListener('blur', markHandedOff);
        activeWindow.removeEventListener('pagehide', markHandedOff);
        activeDocument.removeEventListener('visibilitychange', handleVisibilityChange);
        if (timeoutId !== undefined) activeWindow.clearTimeout(timeoutId);
    };

    const runFallback = () => {
        cleanup();
        if (!handedOff && !activeDocument.hidden) fallback();
    };

    function markHandedOff() {
        handedOff = true;
        cleanup();
    }

    function handleVisibilityChange() {
        if (activeDocument.hidden) markHandedOff();
    }

    activeWindow.addEventListener('blur', markHandedOff, { once: true });
    activeWindow.addEventListener('pagehide', markHandedOff, { once: true });
    activeDocument.addEventListener('visibilitychange', handleVisibilityChange, { once: true });

    timeoutId = activeWindow.setTimeout(runFallback, options.timeoutMs ?? LAUNCHER_PROTOCOL_TIMEOUT_MS);

    try {
        (options.openUrl ?? ((url: string) => { activeWindow.location.href = url; }))(launcherUrl);
    } catch {
        runFallback();
    }
};

export const openLauncherInstallOrFallback = (
    target: LauncherInstallTarget,
    fallback: () => void,
    options: LauncherHandoffOptions = {}
) => {
    openLauncherUrlOrFallback(buildLauncherInstallUrl(target), fallback, options);
};

export const openLauncherListInstallOrFallback = (
    target: LauncherListInstallTarget,
    fallback: () => void,
    options: LauncherHandoffOptions = {}
) => {
    openLauncherUrlOrFallback(buildLauncherListInstallUrl(target), fallback, options);
};
