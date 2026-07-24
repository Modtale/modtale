import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    buildLauncherInstallUrl,
    buildLauncherListInstallUrl,
    openLauncherInstallOrFallback,
    openLauncherListInstallOrFallback
} from '@/modules/launcher/utils/launcherProtocol';

describe('launcherProtocol', () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it('builds launcher install URLs with project and version context', () => {
        expect(buildLauncherInstallUrl({
            projectId: 'project-123',
            projectHandle: 'cool-mod',
            versionNumber: '1.2.3',
            gameVersion: '0.5.4'
        })).toBe('modtale://install?projectId=project-123&project=cool-mod&version=1.2.3&gameVersion=0.5.4');
    });

    it('falls back to the project id when no project handle is available', () => {
        expect(buildLauncherInstallUrl({ projectId: 'project-123' }))
            .toBe('modtale://install?projectId=project-123&project=project-123');
    });

    it('builds launcher list install URLs with share context', () => {
        expect(buildLauncherListInstallUrl({
            listId: 'list-123',
            shareUrl: 'https://modtale.net/lists/list-123'
        })).toBe('modtale://install-list?listId=list-123&url=https%3A%2F%2Fmodtale.net%2Flists%2Flist-123');
    });

    it('runs the fallback if the protocol handoff does not leave the page', () => {
        vi.useFakeTimers();
        const openUrl = vi.fn();
        const fallback = vi.fn();

        openLauncherInstallOrFallback(
            { projectId: 'project-123', projectHandle: 'cool-mod' },
            fallback,
            { openUrl, timeoutMs: 25 }
        );

        expect(openUrl).toHaveBeenCalledWith('modtale://install?projectId=project-123&project=cool-mod');

        vi.advanceTimersByTime(25);

        expect(fallback).toHaveBeenCalledOnce();
    });

    it('does not run fallback after a browser handoff signal', () => {
        vi.useFakeTimers();
        const openUrl = vi.fn();
        const fallback = vi.fn();

        openLauncherInstallOrFallback(
            { projectId: 'project-123' },
            fallback,
            { openUrl, timeoutMs: 25 }
        );

        window.dispatchEvent(new Event('blur'));
        vi.advanceTimersByTime(25);

        expect(fallback).not.toHaveBeenCalled();
    });

    it('opens list install URLs through the same handoff path', () => {
        vi.useFakeTimers();
        const openUrl = vi.fn();
        const fallback = vi.fn();

        openLauncherListInstallOrFallback(
            { listId: 'list-123' },
            fallback,
            { openUrl, timeoutMs: 25 }
        );

        expect(openUrl).toHaveBeenCalledWith('modtale://install-list?listId=list-123');

        vi.advanceTimersByTime(25);

        expect(fallback).toHaveBeenCalledOnce();
    });
});
