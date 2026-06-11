import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '@/components/ui/Toast';

const mockProject = {
    id: 'project-1',
    title: 'Test Project',
    slug: 'test-project',
    description: 'A short summary that is long enough for the editor preview.',
    about: 'Long form project description for the editor test.',
    tags: ['Adventure'],
    links: {},
    repositoryUrl: '',
    imageUrl: null,
    bannerUrl: null,
    license: 'MIT',
    status: 'DRAFT',
    classification: 'PLUGIN',
    versions: [],
    galleryImages: [],
    hmWikiEnabled: false,
    projectRoles: []
} as any;

const mockSetProject = vi.fn();
const mockSetIsDirty = vi.fn();
const mockSetSlugError = vi.fn();
const mockSetUserSearchResults = vi.fn();
const mockSetProvider = vi.fn();
const mockSetManualRepo = vi.fn();
const mockHandleSave = vi.fn();
const mockHandleSubmit = vi.fn();
const mockHandleRoleUpdate = vi.fn();
const mockHandleCancelInvite = vi.fn();
const mockHandleGalleryUpload = vi.fn();
const mockHandleGalleryDelete = vi.fn();

vi.mock('@/modules/project/hooks/useProjectDetail', () => ({
    useProjectDetail: () => ({
        project: mockProject,
        setProject: mockSetProject,
        loading: false,
        contributors: []
    })
}));

vi.mock('@/modules/project/hooks/useProjectEditor', () => ({
    useProjectEditor: () => ({
        repos: [],
        loadingRepos: false,
        manualRepo: '',
        setManualRepo: mockSetManualRepo,
        repoValid: true,
        isDirty: false,
        setIsDirty: mockSetIsDirty,
        slugError: null,
        setSlugError: mockSetSlugError,
        userSearchResults: [],
        setUserSearchResults: mockSetUserSearchResults,
        provider: 'github',
        setProvider: mockSetProvider,
        markDirty: vi.fn(),
        checkRepoUrl: vi.fn(),
        fetchRepos: vi.fn(),
        handleRoleUpdate: mockHandleRoleUpdate,
        handleCancelInvite: mockHandleCancelInvite,
        handleSave: mockHandleSave,
        handleSubmit: mockHandleSubmit,
        isSaving: false,
        handleGalleryUpload: mockHandleGalleryUpload,
        handleGalleryDelete: mockHandleGalleryDelete
    })
}));

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getMetaGameVersions: vi.fn()
    }
}));

import { projectClient } from '@/modules/project/api/projectClient';
import { ProjectEditorView } from '@/modules/project/views/ProjectEditor';

const mockedProjectClient = vi.mocked(projectClient);

const waitForText = async (container: HTMLElement, expected: string) => {
    for (let attempt = 0; attempt < 30; attempt += 1) {
        if ((container.textContent || '').includes(expected)) {
            return;
        }

        await act(async () => {
            await new Promise((resolve) => setTimeout(resolve, 0));
        });
    }

    throw new Error(`Timed out waiting for "${expected}"`);
};

describe('ProjectEditorView route smoke test', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        mockedProjectClient.getMetaGameVersions.mockResolvedValue(['1.21.0']);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        vi.clearAllMocks();
    });

    it('loads the editor route and renders the Files tab form without crashing', async () => {
        await act(async () => {
            root.render(
                <ToastProvider>
                    <MemoryRouter initialEntries={['/projects/project-1/edit']}>
                        <Routes>
                            <Route
                                path="/projects/:id/edit"
                                element={
                                    <ProjectEditorView
                                        currentUser={{ id: 'user-1', username: 'tester' } as any}
                                        onShowStatus={vi.fn()}
                                    />
                                }
                            />
                        </Routes>
                    </MemoryRouter>
                </ToastProvider>
            );
        });

        await waitForText(container, 'Test Project');

        const filesTab = Array.from(container.querySelectorAll('button')).find((button) => (
            button.textContent?.includes('Files (0)')
        ));

        expect(filesTab, 'expected the editor route to render its Files tab control').toBeDefined();

        await act(async () => {
            filesTab?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        await waitForText(container, 'Version Number');
        expect(container.textContent).toContain('Game Versions');
        expect(mockedProjectClient.getMetaGameVersions).toHaveBeenCalledTimes(1);
    });
});
