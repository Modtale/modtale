import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';

import { Files } from '@/modules/project/tabs/Files';
import { VersionFields } from '@/modules/project/components/VersionFields';
import { projectClient } from '@/modules/project/api/projectClient';
import { Permission } from '@/modules/permissions/permissions';
import { ToastProvider } from '@/components/ui/Toast';

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getMetaGameVersions: vi.fn(),
        getMetaGameVersionCatalog: vi.fn()
    }
}));

const mockedProjectClient = vi.mocked(projectClient);

const projectData = {
    id: 'project-1',
    status: 'DRAFT',
    classification: 'PLUGIN',
    versions: [
        {
            id: 'version-1',
            versionNumber: '1.0.0',
            gameVersions: ['2026.03.11'],
            releaseDate: '2026-06-01T00:00:00Z',
            channel: 'RELEASE'
        }
    ]
} as any;

const versionData = {
    versionNumber: '1.0.1',
    gameVersions: ['2026.03.11'],
    changelog: '',
    file: null,
    dependencies: [],
    channel: 'RELEASE'
} as any;

const waitForText = async (container: HTMLElement, expected: string) => {
    for (let attempt = 0; attempt < 20; attempt += 1) {
        if ((container.textContent || '').includes(expected)) {
            return;
        }

        await act(async () => {
            await new Promise((resolve) => setTimeout(resolve, 0));
        });
    }

    throw new Error(`Timed out waiting for "${expected}"`);
};

describe('Files tab loadability', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        mockedProjectClient.getMetaGameVersions.mockResolvedValue(['2026.03.11']);
        mockedProjectClient.getMetaGameVersionCatalog.mockResolvedValue({
            releaseVersions: ['2026.03.11'],
            preReleaseVersions: [],
            allVersions: ['2026.03.11'],
            versions: [{ version: '2026.03.11', preRelease: false, sourceUrl: 'test' }]
        });
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        vi.clearAllMocks();
    });

    it('renders VersionFields with the expected form controls', async () => {
        await act(async () => {
            root.render(
                <ToastProvider>
                    <VersionFields
                        data={versionData}
                        onChange={vi.fn()}
                        projectType="PLUGIN"
                        currentProjectId="project-1"
                        hideFilePicker={true}
                    />
                </ToastProvider>
            );
        });

        await waitForText(container, 'Version Number');
        expect(container.textContent).toContain('Game Versions');
        expect(mockedProjectClient.getMetaGameVersionCatalog).toHaveBeenCalledTimes(1);
        expect(mockedProjectClient.getMetaGameVersions).not.toHaveBeenCalled();
    });

    it('defaults to the latest release game version when prereleases are newer', async () => {
        const onChange = vi.fn();
        mockedProjectClient.getMetaGameVersionCatalog.mockResolvedValue({
            releaseVersions: ['2026.03.11'],
            preReleaseVersions: ['2026.04.01-pre.1'],
            allVersions: ['2026.04.01-pre.1', '2026.03.11'],
            versions: [
                { version: '2026.04.01-pre.1', preRelease: true, sourceUrl: 'test' },
                { version: '2026.03.11', preRelease: false, sourceUrl: 'test' }
            ]
        });

        await act(async () => {
            root.render(
                <ToastProvider>
                    <VersionFields
                        data={{ ...versionData, gameVersions: [] }}
                        onChange={onChange}
                        projectType="PLUGIN"
                        currentProjectId="project-1"
                        hideFilePicker={true}
                    />
                </ToastProvider>
            );
        });

        for (let attempt = 0; attempt < 20 && onChange.mock.calls.length === 0; attempt += 1) {
            await act(async () => {
                await new Promise((resolve) => setTimeout(resolve, 0));
            });
        }

        expect(onChange).toHaveBeenCalledWith(expect.objectContaining({
            gameVersions: ['2026.03.11']
        }));
        expect(mockedProjectClient.getMetaGameVersions).not.toHaveBeenCalled();
    });

    it('still renders version history controls when the upload form is hidden', async () => {
        await act(async () => {
            root.render(
                <Files
                    projectData={projectData}
                    versionData={versionData}
                    setVersionData={vi.fn()}
                    readOnly={false}
                    hasProjectPermission={(perm) => perm !== Permission.VERSION_CREATE}
                    classification="PLUGIN"
                    handleUploadVersion={vi.fn()}
                    handleEditVersion={vi.fn()}
                    isLoading={false}
                />
            );
        });

        expect(container.textContent).toContain('1.0.0');
    });
});
