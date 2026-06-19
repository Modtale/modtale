import { act } from 'react';
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
const mockHandleGalleryVideoAdd = vi.fn();
const mockHandleGalleryDelete = vi.fn();
const mockHandleGalleryCaptionChange = vi.fn();

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
        handleGalleryVideoAdd: mockHandleGalleryVideoAdd,
        handleGalleryDelete: mockHandleGalleryDelete,
        handleGalleryCaptionChange: mockHandleGalleryCaptionChange
    })
}));

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getMetaGameVersions: vi.fn(),
        getMetaGameVersionCatalog: vi.fn()
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
        mockedProjectClient.getMetaGameVersionCatalog.mockResolvedValue({
            releaseVersions: ['1.21.0'],
            preReleaseVersions: [],
            allVersions: ['1.21.0'],
            versions: [{ version: '1.21.0', preRelease: false, sourceUrl: 'test' }]
        });
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
        expect(mockedProjectClient.getMetaGameVersionCatalog).toHaveBeenCalledTimes(1);
        expect(mockedProjectClient.getMetaGameVersions).not.toHaveBeenCalled();
    });

    it('opens custom license inputs when the custom license option is selected', async () => {
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

        const licenseSectionButton = Array.from(container.querySelectorAll('button')).find((button) => (
            button.textContent?.trim() === 'License'
        ));

        expect(licenseSectionButton, 'expected the editor sidebar to render the License section').toBeDefined();

        await act(async () => {
            licenseSectionButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        await waitForText(container, 'Custom License');

        const customLicenseButton = Array.from(container.querySelectorAll('button')).find((button) => (
            button.textContent?.includes('Custom License')
        ));

        await act(async () => {
            customLicenseButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(container.querySelector('input[placeholder="License Name"]')).not.toBeNull();
        expect(container.querySelector('input[placeholder="License URL"]')).not.toBeNull();
    });

    it('expands the card preview without exposing project links', async () => {
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

        const previewTrigger = container.querySelector('[aria-label="Expand project card preview"]') as HTMLElement | null;
        expect(previewTrigger, 'expected the editor sidebar to render the card preview trigger').not.toBeNull();
        expect(previewTrigger?.querySelector('a[href]')).toBeNull();

        await act(async () => {
            previewTrigger?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        await waitForText(document.body, 'Project Card Preview');

        const previewDialog = document.body.querySelector('[role="dialog"][aria-label="Project card preview"]') as HTMLElement | null;
        expect(previewDialog).not.toBeNull();
        expect(previewDialog?.querySelector('a[href]')).toBeNull();
        expect(previewDialog?.textContent).toContain('Test Project');

        const previewFrame = previewDialog?.querySelector('[data-testid="project-card-preview-frame"]') as HTMLElement | null;
        expect(previewFrame).not.toBeNull();

        await act(async () => {
            previewFrame?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(document.body.querySelector('[role="dialog"][aria-label="Project card preview"]')).not.toBeNull();

        await act(async () => {
            previewDialog?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(document.body.querySelector('[role="dialog"][aria-label="Project card preview"]')).toBeNull();
    });
});
