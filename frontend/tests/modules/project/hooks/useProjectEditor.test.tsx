import { act, useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { useProjectEditor } from '@/modules/project/hooks/useProjectEditor';
import { projectClient } from '@/modules/project/api/projectClient';
import { api, extractApiErrorMessage } from '@/utils/api';
import type { MetadataFormData } from '@/modules/project/components/FormShared';

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getGitRepos: vi.fn(),
        updateRole: vi.fn(),
        cancelInvite: vi.fn(),
        getProjectFull: vi.fn()
    }
}));

vi.mock('@/utils/api', () => ({
    api: {
        put: vi.fn(),
        post: vi.fn(),
        delete: vi.fn()
    },
    extractApiErrorMessage: vi.fn()
}));

const mockedProjectClient = vi.mocked(projectClient);
type MockApi = {
    put: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
};

const mockedApi = api as unknown as MockApi;
const mockedExtractApiErrorMessage = vi.mocked(extractApiErrorMessage);

const settle = async (times = 8) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

type HookSnapshot = ReturnType<typeof useProjectEditor> & {
    projectData: any;
    metaData: MetadataFormData;
    bannerFile: File | null;
    bannerPreview: string | null;
};

type RenderHookOptions = {
    currentUser?: any;
    projectData?: any;
    metaData?: MetadataFormData;
    bannerFile?: File | null;
    bannerPreview?: string | null;
};

const Probe = ({
    initialProjectData,
    currentUser,
    initialMetaData,
    initialBannerFile,
    initialBannerPreview,
    onShowStatus,
    onRender
}: {
    initialProjectData: any;
    currentUser: any;
    initialMetaData: MetadataFormData;
    initialBannerFile: File | null;
    initialBannerPreview: string | null;
    onShowStatus: ReturnType<typeof vi.fn>;
    onRender: (snapshot: HookSnapshot) => void;
}) => {
    const [projectData, setProjectData] = useState(initialProjectData);
    const [metaData, setMetaData] = useState(initialMetaData);
    const [bannerFile, setBannerFile] = useState(initialBannerFile);
    const [bannerPreview, setBannerPreview] = useState<string | null>(initialBannerPreview);

    const snapshot = useProjectEditor(
        projectData,
        currentUser,
        metaData,
        bannerFile,
        setMetaData,
        setBannerFile,
        setBannerPreview,
        setProjectData,
        onShowStatus
    );

    onRender({
        ...snapshot,
        projectData,
        metaData,
        bannerFile,
        bannerPreview
    });

    return (
        <div
            id="probe"
            data-repos={String(snapshot.repos.length)}
            data-dirty={String(snapshot.isDirty)}
            data-saving={String(snapshot.isSaving)}
            data-provider={snapshot.provider}
            data-slug-error={snapshot.slugError ?? ''}
            data-banner-preview={bannerPreview ?? ''}
            data-title={projectData?.title ?? ''}
        />
    );
};

describe('useProjectEditor', () => {
    let container: HTMLDivElement;
    let root: Root;
    let latestSnapshot: HookSnapshot;
    let showStatus: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        latestSnapshot = undefined as unknown as HookSnapshot;
        showStatus = vi.fn();

        vi.clearAllMocks();
        mockedApi.put.mockResolvedValue({ data: null } as any);
        mockedApi.post.mockResolvedValue({ data: null } as any);
        mockedApi.delete.mockResolvedValue({ data: null } as any);
        mockedProjectClient.getGitRepos.mockResolvedValue([]);
        mockedProjectClient.updateRole.mockResolvedValue(undefined);
        mockedProjectClient.cancelInvite.mockResolvedValue(undefined);
        mockedProjectClient.getProjectFull.mockResolvedValue(null as any);
        mockedExtractApiErrorMessage.mockImplementation((error: any, fallback?: string) => {
            return error?.response?.data?.message ?? error?.response?.data ?? fallback ?? 'Unknown error';
        });
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    const renderHook = async ({
        currentUser = { id: 'user-1', username: 'Ada', connectedAccounts: [] },
        projectData = {
            id: 'project-1',
            title: 'Sky Tools',
            slug: 'sky-tools',
            allowModpacks: true,
            allowComments: true,
            hmWikiEnabled: false,
            hmWikiSlug: null,
            teamMembers: [],
            teamInvites: []
        },
        metaData = {
            title: 'Sky Tools',
            slug: 'sky-tools',
            summary: 'Old summary',
            description: 'Old description',
            tags: ['magic'],
            links: { discord: 'https://discord.gg/sky' },
            repositoryUrl: 'https://github.com/sky/tools',
            iconFile: null,
            iconPreview: '/old-icon.png',
            license: 'MIT'
        } satisfies MetadataFormData,
        bannerFile = null as File | null,
        bannerPreview = '/old-banner.png'
    }: RenderHookOptions = {}) => {
        await act(async () => {
            root.render(
                <Probe
                    initialProjectData={projectData}
                    currentUser={currentUser}
                    initialMetaData={metaData}
                    initialBannerFile={bannerFile}
                    initialBannerPreview={bannerPreview}
                    onShowStatus={showStatus}
                    onRender={snapshot => {
                        latestSnapshot = snapshot;
                    }}
                />
            );
        });
        await settle();
    };

    it('validates repository urls against the allowed hosts', async () => {
        await renderHook();

        await act(async () => {
            expect(latestSnapshot.checkRepoUrl('https://github.com/modtale/repo')).toBe(true);
            expect(latestSnapshot.checkRepoUrl('https://example.com/modtale/repo')).toBe(false);
        });
        await settle();

        expect(latestSnapshot.repoValid).toBe(false);
    });

    it('fetches repositories for the connected provider and stores them', async () => {
        mockedProjectClient.getGitRepos.mockResolvedValue([{ id: 'repo-1', name: 'Sky Repo' }] as any);

        await renderHook({
            currentUser: {
                id: 'user-1',
                username: 'Ada',
                connectedAccounts: [{ provider: 'github' }]
            }
        });

        await act(async () => {
            latestSnapshot.fetchRepos();
        });
        await settle();

        expect(mockedProjectClient.getGitRepos).toHaveBeenCalledWith('github');
        expect(latestSnapshot.repos).toEqual([{ id: 'repo-1', name: 'Sky Repo' }]);
        expect(latestSnapshot.loadingRepos).toBe(false);
    });

    it('surfaces repository fetch failures through the shared status handler', async () => {
        mockedProjectClient.getGitRepos.mockRejectedValue({
            response: { data: { message: 'GitHub is unavailable' } }
        });

        await renderHook({
            currentUser: {
                id: 'user-1',
                username: 'Ada',
                connectedAccounts: [{ provider: 'github' }]
            }
        });

        await act(async () => {
            latestSnapshot.fetchRepos();
        });
        await settle();

        expect(showStatus).toHaveBeenCalledWith('error', 'Repository Error', 'GitHub is unavailable');
        expect(latestSnapshot.loadingRepos).toBe(false);
    });

    it('saves metadata uploads icon and banner assets and refreshes project data', async () => {
        const iconFile = new File(['icon'], 'icon.png', { type: 'image/png' });
        const bannerFile = new File(['banner'], 'banner.png', { type: 'image/png' });

        mockedProjectClient.getProjectFull.mockResolvedValue({
            id: 'project-1',
            title: 'Sky Tools Reloaded',
            slug: 'sky-tools',
            imageUrl: '/new-icon.png',
            bannerUrl: '/new-banner.png'
        } as any);

        await renderHook({
            metaData: {
                title: 'Sky Tools Reloaded',
                slug: 'sky-tools',
                summary: 'Updated summary',
                description: 'Updated description',
                tags: ['magic', 'utility'],
                links: { website: 'https://modtale.net' },
                repositoryUrl: 'https://github.com/sky/tools',
                iconFile,
                iconPreview: '/old-icon.png',
                license: 'Apache-2.0'
            },
            bannerFile
        });

        await act(async () => {
            latestSnapshot.markDirty();
        });
        await settle();

        await act(async () => {
            await latestSnapshot.handleSave();
        });
        await settle();

        expect(mockedApi.put).toHaveBeenNthCalledWith(1, '/projects/project-1', {
            title: 'Sky Tools Reloaded',
            slug: 'sky-tools',
            description: 'Updated summary',
            about: 'Updated description',
            tags: ['magic', 'utility'],
            links: { website: 'https://modtale.net' },
            repositoryUrl: 'https://github.com/sky/tools',
            license: 'Apache-2.0',
            allowModpacks: true,
            allowComments: true,
            hmWikiEnabled: false,
            hmWikiSlug: null
        });

        const iconUpload = mockedApi.put.mock.calls[1];
        expect(iconUpload[0]).toBe('/projects/project-1/icon');
        expect(iconUpload[1]).toBeInstanceOf(FormData);
        expect((iconUpload[1] as FormData).get('file')).toBe(iconFile);

        const bannerUpload = mockedApi.put.mock.calls[2];
        expect(bannerUpload[0]).toBe('/projects/project-1/banner');
        expect(bannerUpload[1]).toBeInstanceOf(FormData);
        expect((bannerUpload[1] as FormData).get('file')).toBe(bannerFile);

        expect(mockedProjectClient.getProjectFull).toHaveBeenCalledWith('project-1');
        expect(latestSnapshot.isDirty).toBe(false);
        expect(latestSnapshot.metaData.iconFile).toBeNull();
        expect(latestSnapshot.metaData.iconPreview).toBe('/new-icon.png');
        expect(latestSnapshot.bannerFile).toBeNull();
        expect(latestSnapshot.bannerPreview).toBe('/new-banner.png');
        expect(latestSnapshot.projectData.title).toBe('Sky Tools Reloaded');
        expect(showStatus).toHaveBeenLastCalledWith('success', 'Saved', 'Project details saved successfully.');
    });

    it('blocks saves when the gallery carousel marker is used more than once', async () => {
        await renderHook({
            metaData: {
                title: 'Sky Tools',
                slug: 'sky-tools',
                summary: 'Summary',
                description: 'Intro\n\n{{gallery-carousel}}\n\nMiddle\n\n{{ gallery-carousel }}',
                tags: [],
                links: {},
                repositoryUrl: '',
                iconFile: null,
                iconPreview: null,
                license: 'MIT'
            }
        });

        await act(async () => {
            await latestSnapshot.handleSave();
        });
        await settle();

        expect(mockedApi.put).not.toHaveBeenCalled();
        expect(showStatus).toHaveBeenCalledWith('error', 'Gallery Carousel Marker', 'Use {{gallery-carousel}} only once in the project description.');
    });

    it('records slug validation errors from failed saves', async () => {
        mockedApi.put.mockRejectedValue({
            response: { data: 'Slug already exists' }
        });
        mockedExtractApiErrorMessage.mockReturnValue('Slug already exists');

        await renderHook();

        await act(async () => {
            await latestSnapshot.handleSave();
        });
        await settle();

        expect(latestSnapshot.slugError).toBe('Slug already exists');
        expect(showStatus).toHaveBeenCalledWith('error', 'Save Failed', 'Slug already exists');
        expect(latestSnapshot.isSaving).toBe(false);
    });

    it('submits the project after saving dirty changes and marks it pending', async () => {
        mockedProjectClient.getProjectFull.mockResolvedValue({
            id: 'project-1',
            title: 'Sky Tools',
            slug: 'sky-tools'
        } as any);

        await renderHook();

        await act(async () => {
            latestSnapshot.markDirty();
        });
        await settle();

        await act(async () => {
            await latestSnapshot.handleSubmit();
        });
        await settle();

        expect(mockedApi.put).toHaveBeenCalledWith('/projects/project-1', expect.any(Object));
        expect(mockedApi.post).toHaveBeenCalledWith('/projects/project-1/submit');
        expect(mockedApi.put.mock.invocationCallOrder[0]).toBeLessThan(mockedApi.post.mock.invocationCallOrder[0]);
        expect(latestSnapshot.projectData.status).toBe('PENDING');
        expect(showStatus).toHaveBeenLastCalledWith('success', 'Submitted', 'Project submitted for review successfully.');
    });

    it('uploads gallery images as multipart form data and replaces the project payload', async () => {
        const file = new File(['gallery'], 'gallery.png', { type: 'image/png' });
        mockedApi.post.mockResolvedValue({
            data: { id: 'project-1', title: 'Sky Tools', galleryImages: ['/gallery.png'] }
        } as any);

        await renderHook();

        await act(async () => {
            await latestSnapshot.handleGalleryUpload(file);
        });
        await settle();

        const [url, formData, config] = mockedApi.post.mock.calls[0];
        expect(url).toBe('/projects/project-1/gallery');
        expect(formData).toBeInstanceOf(FormData);
        expect((formData as FormData).get('file')).toBe(file);
        expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } });
        expect(latestSnapshot.projectData.galleryImages).toEqual(['/gallery.png']);
        expect(showStatus).toHaveBeenLastCalledWith('success', 'Uploaded', 'Image added to gallery.');
    });

    it('adds youtube gallery videos and replaces the project payload', async () => {
        mockedApi.post.mockResolvedValue({
            data: { id: 'project-1', title: 'Sky Tools', galleryImages: ['https://www.youtube.com/watch?v=dQw4w9WgXcQ'] }
        } as any);

        await renderHook();

        await act(async () => {
            await latestSnapshot.handleGalleryVideoAdd('https://youtu.be/dQw4w9WgXcQ');
        });
        await settle();

        expect(mockedApi.post).toHaveBeenCalledWith('/projects/project-1/gallery/youtube', {
            videoUrl: 'https://youtu.be/dQw4w9WgXcQ'
        });
        expect(latestSnapshot.projectData.galleryImages).toEqual(['https://www.youtube.com/watch?v=dQw4w9WgXcQ']);
        expect(showStatus).toHaveBeenLastCalledWith('success', 'Added', 'Video added to gallery.');
    });

    it('deletes gallery images using the request body payload and updates project state', async () => {
        mockedApi.delete.mockResolvedValue({
            data: { id: 'project-1', title: 'Sky Tools', galleryImages: [] }
        } as any);

        await renderHook();

        await act(async () => {
            await latestSnapshot.handleGalleryDelete('/gallery.png');
        });
        await settle();

        expect(mockedApi.delete).toHaveBeenCalledWith('/projects/project-1/gallery', {
            data: { imageUrl: '/gallery.png' }
        });
        expect(latestSnapshot.projectData.galleryImages).toEqual([]);
        expect(showStatus).toHaveBeenLastCalledWith('success', 'Deleted', 'Image removed from gallery.');
    });

    it('updates gallery captions and refreshes project state', async () => {
        mockedApi.put.mockResolvedValue({
            data: {
                id: 'project-1',
                title: 'Sky Tools',
                galleryImages: ['/gallery.png'],
                galleryImageCaptions: { '/gallery.png': 'Opening shot' }
            }
        } as any);

        await renderHook();

        await act(async () => {
            await latestSnapshot.handleGalleryCaptionChange('/gallery.png', 'Opening shot');
        });
        await settle();

        expect(mockedApi.put).toHaveBeenCalledWith('/projects/project-1/gallery/caption', {
            imageUrl: '/gallery.png',
            caption: 'Opening shot'
        });
        expect(latestSnapshot.projectData.galleryImageCaptions).toEqual({ '/gallery.png': 'Opening shot' });
        expect(showStatus).toHaveBeenLastCalledWith('success', 'Saved', 'Gallery caption updated.');
    });
});
