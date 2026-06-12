import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { Files } from '@/modules/project/tabs/Files';
import type { VersionFormData } from '@/modules/project/components/FormShared';

const mockVersionFields = vi.fn((props: any) => <div data-testid="version-fields" />);

vi.mock('@/modules/project/components/VersionFields', () => ({
    VersionFields: (props: any) => mockVersionFields(props)
}));

const versionData: VersionFormData = {
    versionNumber: '',
    gameVersions: [],
    changelog: '',
    file: null,
    dependencies: [],
    incompatibleProjectIds: [],
    channel: 'RELEASE'
};

const renderFiles = (projectData: any) => (
    <Files
        projectData={projectData}
        versionData={versionData}
        setVersionData={vi.fn()}
        readOnly={false}
        hasProjectPermission={() => true}
        classification="PLUGIN"
        handleUploadVersion={vi.fn()}
        handleEditVersion={vi.fn()}
        handleDeleteVersion={vi.fn()}
        isLoading={false}
    />
);

describe('Files upload rules for drafts', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        mockVersionFields.mockClear();
    });

    it('hides the upload form and explains why when a draft already has a version', async () => {
        await act(async () => {
            root.render(renderFiles({
                id: 'project-1',
                status: 'DRAFT',
                versions: [{
                    id: 'version-1',
                    versionNumber: '1.0.0',
                    gameVersions: ['1.21'],
                    fileUrl: '/download.jar',
                    downloadCount: 0,
                    releaseDate: '2026-06-09T12:00:00'
                }]
            }));
        });

        expect(container.textContent).toContain('This draft already has one uploaded version.');
        expect(container.textContent).toContain('New version uploads are hidden until the project leaves draft status.');
        expect(container.querySelector('[data-testid="version-fields"]')).toBeNull();
        expect(container.textContent).not.toContain('Upload Version');
    });

    it('shows the upload form for drafts that do not have a version yet', async () => {
        await act(async () => {
            root.render(renderFiles({
                id: 'project-1',
                status: 'DRAFT',
                versions: []
            }));
        });

        expect(container.querySelector('[data-testid="version-fields"]')).not.toBeNull();
        expect(container.textContent).toContain('Upload Version');
    });

    it('keeps the upload form visible for private projects even after a version exists', async () => {
        await act(async () => {
            root.render(renderFiles({
                id: 'project-1',
                status: 'PRIVATE',
                versions: [{
                    id: 'version-1',
                    versionNumber: '1.0.0',
                    gameVersions: ['1.21'],
                    fileUrl: '/download.jar',
                    downloadCount: 0,
                    releaseDate: '2026-06-09T12:00:00'
                }]
            }));
        });

        expect(container.querySelector('[data-testid="version-fields"]')).not.toBeNull();
        expect(container.textContent).toContain('This project is private.');
        expect(container.textContent).toContain('Upload Version');
    });

    it('passes existing versions and previous dependencies into the upload form', async () => {
        await act(async () => {
            root.render(renderFiles({
                id: 'project-1',
                status: 'PRIVATE',
                versions: [{
                    id: 'version-1',
                    versionNumber: '1.0.0',
                    gameVersions: ['1.21'],
                    dependencies: [{
                        projectId: 'dep-1',
                        projectTitle: 'Dependency One',
                        versionNumber: '2.0.0'
                    }],
                    fileUrl: '/download.jar',
                    downloadCount: 0,
                    releaseDate: '2026-06-09T12:00:00'
                }]
            }));
        });

        expect(mockVersionFields).toHaveBeenCalled();
        const props = mockVersionFields.mock.calls.at(-1)?.[0] as any;
        expect(props?.existingVersions).toEqual(['1.0.0']);
        expect(props?.previousDependencies).toEqual([{
            projectId: 'dep-1',
            projectTitle: 'Dependency One',
            versionNumber: '2.0.0'
        }]);
    });

    it('hides previous dependency import when dependencies are already selected', async () => {
        const populatedVersionData: VersionFormData = {
            ...versionData,
            dependencies: [{
                projectId: 'dep-2',
                projectTitle: 'Dependency Two',
                versionNumber: '3.0.0',
                dependencyType: 'REQUIRED',
                source: 'MODTALE'
            }]
        };

        await act(async () => {
            root.render(
                <Files
                    projectData={{
                        id: 'project-1',
                        status: 'PRIVATE',
                        versions: [{
                            id: 'version-1',
                            versionNumber: '1.0.0',
                            gameVersions: ['1.21'],
                            dependencies: [{
                                projectId: 'dep-1',
                                projectTitle: 'Dependency One',
                                versionNumber: '2.0.0'
                            }],
                            fileUrl: '/download.jar',
                            downloadCount: 0,
                            releaseDate: '2026-06-09T12:00:00'
                        }]
                    } as any}
                    versionData={populatedVersionData}
                    setVersionData={vi.fn()}
                    readOnly={false}
                    hasProjectPermission={() => true}
                    classification="PLUGIN"
                    handleUploadVersion={vi.fn()}
                    handleEditVersion={vi.fn()}
                    handleDeleteVersion={vi.fn()}
                    isLoading={false}
                />
            );
        });

        const props = mockVersionFields.mock.calls.at(-1)?.[0] as any;
        expect(props?.previousDependencies).toBeUndefined();
    });

    it('lets creators reuse the latest setup in one click', async () => {
        const setVersionData = vi.fn();

        await act(async () => {
            root.render(
                <Files
                    projectData={{
                        id: 'project-1',
                        status: 'PRIVATE',
                        versions: [{
                            id: 'version-1',
                            versionNumber: '1.0.0',
                            gameVersions: ['1.21'],
                            dependencies: [{
                                projectId: 'dep-1',
                                projectTitle: 'Dependency One',
                                versionNumber: '2.0.0',
                                dependencyType: 'OPTIONAL',
                                source: 'MODTALE'
                            }],
                            channel: 'BETA',
                            fileUrl: '/download.jar',
                            downloadCount: 0,
                            releaseDate: '2026-06-09T12:00:00'
                        }]
                    } as any}
                    versionData={versionData}
                    setVersionData={setVersionData}
                    readOnly={false}
                    hasProjectPermission={() => true}
                    classification="PLUGIN"
                    handleUploadVersion={vi.fn()}
                    handleEditVersion={vi.fn()}
                    handleDeleteVersion={vi.fn()}
                    isLoading={false}
                />
            );
        });

        expect(container.textContent).toContain('Reuse Latest Setup');

        await act(async () => {
            const target = Array.from(container.querySelectorAll('button')).find((button) => button.textContent?.includes('Reuse Latest Setup'));
            target?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(setVersionData).toHaveBeenCalledTimes(1);
        expect(container.textContent).not.toContain('Reuse Latest Setup');
        const updater = setVersionData.mock.calls[0][0];
        expect(updater(versionData)).toMatchObject({
            gameVersions: ['1.21'],
            channel: 'BETA',
            dependencies: [{
                projectId: 'dep-1',
                projectTitle: 'Dependency One',
                versionNumber: '2.0.0',
                dependencyType: 'OPTIONAL',
                source: 'MODTALE'
            }]
        });
    });
});
