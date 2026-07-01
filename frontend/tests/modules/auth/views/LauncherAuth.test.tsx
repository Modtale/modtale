import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { LauncherAuth } from '@/modules/auth/views/LauncherAuth';
import { authClient } from '@/modules/auth/api/authClient';
import type { User } from '@/types';

vi.mock('@/modules/auth/api/authClient', () => ({
    authClient: {
        issueLauncherAuthCode: vi.fn()
    }
}));

const mockedAuthClient = vi.mocked(authClient);

const signedInUser: User = {
    id: 'user-1',
    username: 'ada',
    displayName: 'Ada Lovelace',
    avatarUrl: '/avatar.png',
    likedProjectIds: []
};

describe('LauncherAuth', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        vi.clearAllMocks();
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        mockedAuthClient.issueLauncherAuthCode.mockReturnValue(
            new Promise(() => {}) as ReturnType<typeof authClient.issueLauncherAuthCode>
        );
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    it('asks for launcher consent before issuing an auth code', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/launcher/auth?redirect_uri=http%3A%2F%2F127.0.0.1%3A49152%2Fcallback&state=state-123&app_name=Modtale%20Launcher']}>
                    <LauncherAuth user={signedInUser} loadingAuth={false} />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Do you want to authenticate with Modtale Launcher?');
        expect(container.textContent).toContain('Ada Lovelace');
        expect(mockedAuthClient.issueLauncherAuthCode).not.toHaveBeenCalled();

        const authenticateButton = Array.from(container.querySelectorAll('button'))
            .find(button => button.textContent?.includes('Authenticate'));
        expect(authenticateButton).toBeTruthy();

        await act(async () => {
            authenticateButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(mockedAuthClient.issueLauncherAuthCode).toHaveBeenCalledWith({
            redirectUri: 'http://127.0.0.1:49152/callback',
            state: 'state-123'
        });
    });

    it('rejects non-local launcher callback URLs before consent', async () => {
        await act(async () => {
            root.render(
                <MemoryRouter initialEntries={['/launcher/auth?redirect_uri=https%3A%2F%2Fexample.com%2Fcallback&app_name=Sketchy%20Launcher']}>
                    <LauncherAuth user={signedInUser} loadingAuth={false} />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('Launcher Sign-In Failed');
        expect(container.textContent).toContain('The launcher callback URL must point to this device.');
        expect(mockedAuthClient.issueLauncherAuthCode).not.toHaveBeenCalled();
    });
});
