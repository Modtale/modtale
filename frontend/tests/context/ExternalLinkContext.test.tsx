import React, { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ExternalLinkProvider } from '@/context/ExternalLinkContext';

const releaseBase = 'https://github.com/Modtale/modtale/releases/download/';
const installer = `${releaseBase}launcher-stable-v1.2.3/Modtale-1.2.3.exe`;

describe('external link protection', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        localStorage.clear();
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
    });

    async function clickLink(href: string) {
        await act(async () => {
            root.render(<ExternalLinkProvider><a href={href} download="installer.exe"><span>Download</span></a></ExternalLinkProvider>);
        });
        const click = new MouseEvent('click', { bubbles: true, cancelable: true });
        let intercepted = false;
        // Observe capture-phase interception, then suppress jsdom's native navigation.
        document.addEventListener('click', (event) => {
            intercepted = event.defaultPrevented;
            event.preventDefault();
        }, { capture: true, once: true });
        await act(async () => { container.querySelector('span')!.dispatchEvent(click); });
        return intercepted;
    }

    it.each([
        installer,
        `${releaseBase}launcher-v1.2.3/Modtale.msi`,
        `${releaseBase}launcher-stable-v1.2.3/Modtale.dmg`,
        `${releaseBase}launcher-stable-v1.2.3/Modtale.pkg`,
        `${releaseBase}launcher-develop-v1.2.4-develop.10.1/Modtale.AppImage`,
        `${releaseBase}launcher-v0.2.175/modtale-launcher-0.2.175-x86_64.AppImage`,
        `${releaseBase}launcher-develop-v0.2.193-develop.193.1/modtale-launcher-0.2.193-develop.193.1-x86_64.AppImage`,
        'https://modtale.net/launcher',
        'https://dev.modtale.net/launcher',
    ])('allows official downloads and first-party links: %s', async (href) => {
        expect(await clickLink(href)).toBe(false);
        expect(document.body.textContent).not.toContain('Leaving Modtale');
        expect(localStorage.getItem('modtale_skip_external_warning')).toBeNull();
    });

    it.each([
        installer.replace('https:', 'http:'),
        installer.replace('github.com', 'github.com.evil.example'),
        installer.replace('github.com', 'github.com@evil.example'),
        installer.replace('github.com', 'user:password@github.com'),
        installer.replace('github.com', 'github.com:444'),
        installer.replace('/Modtale/', '/another-owner/'),
        installer.replace('/modtale/', '/modtale-fake/'),
        installer.replace('launcher-stable-v', 'unrelated-v'),
        installer.replace('.exe', '.html'),
        `${installer}/extra.exe`,
        `${installer}?redirect=https://evil.example`,
        `${installer}#fragment`,
        `${releaseBase}launcher-stable-v1.2.3/evil%2Ffile.exe`,
        'https://github.com/Modtale/modtale/issues',
        'https://github.com/another-owner/project/releases/download/v1/setup.exe',
        'https://evilmodtale.net/download.exe',
        'https://modtale.net.evil.example/download.exe',
        'https://evil.example/download.exe',
    ])('warns for untrusted destinations even with a download attribute: %s', async (href) => {
        expect(await clickLink(href)).toBe(true);
        expect(document.body.textContent).toContain('Leaving Modtale');
        expect(document.body.textContent).toContain(href);
    });
});
