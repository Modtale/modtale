import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { LauncherDemo } from '@/modules/launcher/components/LauncherDemo';

let host: HTMLDivElement;
let root: Root;
let intersect: IntersectionObserverCallback;
let preference: EventTarget & { matches: boolean };
let play: ReturnType<typeof vi.spyOn>;
let pause: ReturnType<typeof vi.spyOn>;
beforeEach(() => {
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    preference = Object.assign(new EventTarget(), { matches: false });
    vi.stubGlobal('matchMedia', () => preference);
    vi.stubGlobal('IntersectionObserver', class {
        constructor(callback: IntersectionObserverCallback) { intersect = callback; }
        observe() {} disconnect() {}
    });
    play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
});
afterEach(async () => {
    await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals();
});
async function visible(value: boolean) {
    await act(async () => intersect([{ isIntersecting: value, intersectionRatio: value ? 1 : 0 } as IntersectionObserverEntry], {} as IntersectionObserver));
}
it('loads and plays only on screen, pauses off screen, and retains the decoded source', async () => {
    await act(async () => root.render(<LauncherDemo clip="world-library" alt="World mod selection" />));
    const video = host.querySelector('video')!;
    expect(video.hasAttribute('src')).toBe(false);
    expect(play).not.toHaveBeenCalled();
    await visible(true);
    expect(video.src).toContain('world-library.mp4');
    expect(video.muted).toBe(true); expect(video.loop).toBe(true); expect(video.controls).toBe(false);
    expect(play).toHaveBeenCalled();
    pause.mockClear(); await visible(false);
    expect(pause).toHaveBeenCalled(); expect(video.src).toContain('world-library.mp4');
});
it('keeps the poster visible until playback and respects reduced motion', async () => {
    await act(async () => root.render(<LauncherDemo clip="wardrobe" alt="Customize a look" />));
    await visible(true); const video = host.querySelector('video')!;
    expect(video.style.opacity).toBe('0');
    await act(async () => video.dispatchEvent(new Event('playing')));
    expect(video.style.opacity).toBe('1');
    await act(async () => { preference.matches = true; preference.dispatchEvent(new Event('change')); });
    expect(video.style.opacity).toBe('0'); expect(pause).toHaveBeenCalled();
});
it('keeps explanatory screenshots static', async () => {
    await act(async () => root.render(<LauncherDemo clip="pack-contents" alt="Included mods" />));
    expect(host.querySelector('video')).toBeNull(); expect(host.querySelector('img')?.src).toContain('pack-contents.jpg');
});
it('suspends playback while the tab is hidden', async () => {
    await act(async () => root.render(<LauncherDemo clip="world-library" alt="Worlds" />));
    await visible(true); pause.mockClear();
    const hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
    await act(async () => document.dispatchEvent(new Event('visibilitychange')));
    expect(pause).toHaveBeenCalled();
    play.mockClear(); hidden.mockReturnValue(false);
    await act(async () => document.dispatchEvent(new Event('visibilitychange')));
    expect(play).toHaveBeenCalled();
});
