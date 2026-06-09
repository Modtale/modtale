import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { Files } from '@/modules/project/tabs/Files';
import type { VersionFormData } from '@/modules/project/components/FormShared';

vi.mock('@/modules/project/components/VersionFields', () => ({
    VersionFields: () => <div data-testid="version-fields" />
}));

const versionData: VersionFormData = {
    projectIds: [],
    versionNumber: '',
    gameVersions: [],
    changelog: '',
    file: null,
    dependencies: [],
    modIds: [],
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
});
