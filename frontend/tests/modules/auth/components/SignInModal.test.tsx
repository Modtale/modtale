import React from 'react';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { SignInModal } from '@/modules/auth/components/SignInModal';
import { LAST_SIGN_IN_METHOD_STORAGE_KEY } from '@/modules/auth/api/authClient';
import { ToastProvider } from '@/components/ui/Toast';

describe('SignInModal last sign-in method hint', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        window.localStorage.clear();
        window.sessionStorage.clear();
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        document.body.innerHTML = '';
        window.localStorage.clear();
        window.sessionStorage.clear();
    });

    it('shows the saved provider in the sign-in modal', async () => {
        window.localStorage.setItem(LAST_SIGN_IN_METHOD_STORAGE_KEY, 'google');

        await act(async () => {
            root.render(
                <ToastProvider>
                    <MemoryRouter initialEntries={['/login']}>
                        <SignInModal isOpen onClose={vi.fn()} />
                    </MemoryRouter>
                </ToastProvider>
            );
        });

        expect(document.body.textContent).toContain('Last used: Google');
        expect(document.body.querySelector('button[aria-label="Last used: Sign in with Google"]')).not.toBeNull();
    });

    it('offers Hytale sign-in without a GitLab sign-in option', async () => {
        await act(async () => {
            root.render(
                <ToastProvider>
                    <MemoryRouter initialEntries={['/login']}>
                        <SignInModal isOpen onClose={vi.fn()} />
                    </MemoryRouter>
                </ToastProvider>
            );
        });

        expect(document.body.querySelector('button[aria-label="Sign in with Hytale"]')).not.toBeNull();
        expect(document.body.querySelector('button[aria-label="Sign in with GitLab"]')).toBeNull();
    });
});
