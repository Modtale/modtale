import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { PriorityVideo } from '@/components/ui/PriorityVideo';
import { LauncherDemo } from '@/modules/launcher/components/LauncherDemo';

let host: HTMLDivElement;
let root: Root;
let intersect: IntersectionObserverCallback;
let preference: EventTarget & { matches: boolean };
let play: ReturnType<typeof vi.spyOn>;
let pause: ReturnType<typeof vi.spyOn>;
beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ top: 200, bottom: 600, left: 100, right: 900, width: 800, height: 400 } as DOMRect);
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {});
    vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    preference = Object.assign(new EventTarget(), { matches: false });
    vi.stubGlobal('matchMedia', () => preference);
    vi.stubGlobal('IntersectionObserver', class {
        constructor(callback: IntersectionObserverCallback) { intersect = callback; }
        observe() {} unobserve() {} disconnect() {}
    });
    play = vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    pause = vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
});
afterEach(async () => {
    await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers();
});
async function visible(value: boolean) {
    await act(async () => {
        intersect([...host.querySelectorAll('video')].map(target => ({ target, isIntersecting: value, intersectionRatio: value ? 1 : 0, boundingClientRect: target.getBoundingClientRect(), intersectionRect: target.getBoundingClientRect(), rootBounds: null, time: 0 })), {} as IntersectionObserver);
        vi.advanceTimersByTime(20);
    });
}
it('loads and plays only on screen, cancels buffering off screen, and restores the playback position', async () => {
    await act(async () => root.render(<LauncherDemo clip="world-library" alt="World mod selection" />));
    const video = host.querySelector('video')!;
    expect(video.hasAttribute('src')).toBe(false);
    expect(play).not.toHaveBeenCalled();
    await visible(true);
    expect(video.src).toContain('world-library.mp4');
    expect(video.playbackRate).toBe(1);
    expect(video.muted).toBe(true); expect(video.loop).toBe(true); expect(video.controls).toBe(false);
    expect(play).toHaveBeenCalled();
    video.currentTime = 5;
    pause.mockClear(); await visible(false);
    expect(pause).toHaveBeenCalled(); expect(video.hasAttribute('src')).toBe(false);
    video.currentTime = 0;
    await visible(true);
    video.dispatchEvent(new Event('loadedmetadata'));
    expect(video.currentTime).toBe(5);
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

it('gives only the central demo a source and transfers priority on scroll', async () => {
    await act(async () => root.render(<><LauncherDemo clip="world-library" alt="Worlds" /><LauncherDemo clip="wardrobe" alt="Wardrobe" /></>));
    const [first, second] = host.querySelectorAll('video');
    let top = 600;
    second.getBoundingClientRect = () => ({ top, bottom: top + 400, left: 100, right: 900, width: 800, height: 400 } as DOMRect);
    await visible(true);
    expect(first.hasAttribute('src')).toBe(true);
    expect(second.hasAttribute('src')).toBe(false);
    top = 180;
    await act(async () => { document.dispatchEvent(new Event('scroll')); vi.advanceTimersByTime(20); });
    // Hysteresis avoids switching for an insignificant change in distance.
    expect(first.hasAttribute('src')).toBe(true);
    first.getBoundingClientRect = () => ({ top: -200, bottom: 200, left: 100, right: 900, width: 800, height: 400 } as DOMRect);
    await act(async () => { document.dispatchEvent(new Event('scroll')); vi.advanceTimersByTime(20); });
    expect(first.hasAttribute('src')).toBe(false);
    expect(second.hasAttribute('src')).toBe(true);
});
it('keeps user-started article playback ahead of demos, even with reduced motion', async () => {
    preference.matches = true;
    await act(async () => root.render(<><LauncherDemo clip="world-library" alt="Worlds" /><PriorityVideo src="/article.mp4" controls /></>));
    await visible(true);
    const [demo, article] = host.querySelectorAll('video');
    expect(demo.hasAttribute('src')).toBe(false);
    expect(article.src).toContain('/article.mp4');
    expect(play).not.toHaveBeenCalled();
    vi.spyOn(article, 'paused', 'get').mockReturnValue(false);
    await act(async () => { preference.matches = false; preference.dispatchEvent(new Event('change')); });
    expect(demo.hasAttribute('src')).toBe(false);
    expect(article.hasAttribute('src')).toBe(true);
});
it('supports explicit interaction with a secondary article video', async () => {
    await act(async () => root.render(<><PriorityVideo src="/one.mp4" controls /><PriorityVideo src="/two.mp4" controls /></>));
    await visible(true);
    const [first, second] = host.querySelectorAll('video');
    await act(async () => second.dispatchEvent(new Event('pointerdown')));
    expect(first.hasAttribute('src')).toBe(false);
    expect(second.src).toContain('/two.mp4');
});
it('falls back to viewport geometry without IntersectionObserver', async () => {
    vi.stubGlobal('IntersectionObserver', undefined);
    await act(async () => { root.render(<LauncherDemo clip="world-library" alt="Worlds" />); });
    await act(async () => { vi.advanceTimersByTime(20); });
    expect(host.querySelector('video')?.src).toContain('world-library.mp4');
});
