import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { DownloadModal } from '@/modules/project/components/dialogs/DownloadModal';
import { HistoryModal } from '@/modules/project/components/dialogs/HistoryModal';

const settle = async () => {
    await act(async () => {
        await Promise.resolve();
    });
};

describe('DownloadModal Toggle Visibility', () => {
    let container: HTMLDivElement;
    let root: Root;
    const pageText = () => document.body.textContent ?? '';

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

    it('hides Alpha/Beta toggle if the project has no alpha/beta versions at all', async () => {
        const versionsByGame = {
            '0.5.4': [
                { id: 'v1', versionNumber: '1.0.0', channel: 'RELEASE', gameVersion: '0.5.4', releaseDate: new Date().toISOString() }
            ]
        };

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <DownloadModal
                        show={true}
                        onClose={vi.fn()}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={[]}
                        orderedGameVersions={['0.5.4']}
                        onDownload={vi.fn()}
                        showExperimental={false}
                        onToggleExperimental={vi.fn()}
                        onViewHistory={vi.fn()}
                    />
                </MemoryRouter>
            );
        });

        // "Show Beta/Alpha" toggle text should not be present
        expect(pageText()).not.toContain('Show Beta/Alpha');
    });

    it('shows both toggles when project has both release and experimental versions and pre-releases are available', async () => {
        const versionsByGame = {
            '0.5.4': [
                { id: 'v1', versionNumber: '1.0.0', channel: 'RELEASE', gameVersion: '0.5.4', releaseDate: new Date().toISOString() },
                { id: 'v2', versionNumber: '1.1.0-alpha', channel: 'ALPHA', gameVersion: '0.5.4', releaseDate: new Date().toISOString() }
            ],
            '0.5.4-pre.1': [
                { id: 'v3', versionNumber: '1.1.0-pre.1', channel: 'RELEASE', gameVersion: '0.5.4-pre.1', releaseDate: new Date().toISOString() }
            ]
        };

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <DownloadModal
                        show={true}
                        onClose={vi.fn()}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={['0.5.4-pre.1']}
                        orderedGameVersions={['0.5.4', '0.5.4-pre.1']}
                        onDownload={vi.fn()}
                        showExperimental={false}
                        onToggleExperimental={vi.fn()}
                        onViewHistory={vi.fn()}
                    />
                </MemoryRouter>
            );
        });

        expect(pageText()).toContain('Show Beta/Alpha');
        expect(pageText()).toContain('Show Pre-Release Game Versions');
    });

    it('hides Alpha/Beta toggle if the project only has alpha/beta versions globally', async () => {
        const versionsByGame = {
            '0.5.4': [
                { id: 'v2', versionNumber: '1.1.0-alpha', channel: 'ALPHA', gameVersion: '0.5.4', releaseDate: new Date().toISOString() }
            ]
        };

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <DownloadModal
                        show={true}
                        onClose={vi.fn()}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={[]}
                        orderedGameVersions={['0.5.4']}
                        onDownload={vi.fn()}
                        showExperimental={true}
                        onToggleExperimental={vi.fn()}
                        onViewHistory={vi.fn()}
                    />
                </MemoryRouter>
            );
        });

        expect(pageText()).not.toContain('Show Beta/Alpha');
    });

    it('hides Pre-Release toggle if the project has no pre-release versions at all', async () => {
        const versionsByGame = {
            '0.5.4': [
                { id: 'v1', versionNumber: '1.0.0', channel: 'RELEASE', gameVersion: '0.5.4', releaseDate: new Date().toISOString() }
            ]
        };

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <DownloadModal
                        show={true}
                        onClose={vi.fn()}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={['0.5.4-pre.1']} // pre-releases configured, but no builds in versionsByGame
                        orderedGameVersions={['0.5.4']}
                        onDownload={vi.fn()}
                        showExperimental={false}
                        onToggleExperimental={vi.fn()}
                        onViewHistory={vi.fn()}
                    />
                </MemoryRouter>
            );
        });

        expect(pageText()).not.toContain('Show Pre-Release Game Versions');
    });

    it('uses buttons instead of hash links for download actions', async () => {
        const onDownload = vi.fn();
        const versionsByGame = {
            '0.5.4': [
                {
                    id: 'v1',
                    versionNumber: '1.0.0',
                    channel: 'RELEASE',
                    gameVersion: '0.5.4',
                    gameVersions: ['0.5.4'],
                    fileUrl: '/files/skyforge.jar',
                    dependencies: [],
                    releaseDate: new Date().toISOString()
                }
            ]
        };

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <DownloadModal
                        show={true}
                        onClose={vi.fn()}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={[]}
                        orderedGameVersions={['0.5.4']}
                        onDownload={onDownload}
                        showExperimental={false}
                        onToggleExperimental={vi.fn()}
                        onViewHistory={vi.fn()}
                    />
                </MemoryRouter>
            );
        });
        await settle();

        expect(document.body.querySelector('a[href="#"]')).toBeNull();

        const latestButton = Array.from(document.body.querySelectorAll('button'))
            .find((button) => button.textContent?.includes('Download Latest')) as HTMLButtonElement;

        await act(async () => {
            latestButton.click();
        });

        expect(onDownload).toHaveBeenCalledWith('/files/skyforge.jar', '1.0.0', '0.5.4', [], 'RELEASE');
    });

    it('uses a button for changelog download actions', async () => {
        const onDownload = vi.fn();

        await act(async () => {
            root.render(
                <HistoryModal
                    show={true}
                    onClose={vi.fn()}
                    history={[{
                        id: 'v1',
                        versionNumber: '1.0.0',
                        channel: 'RELEASE',
                        gameVersions: ['0.5.4'],
                        fileUrl: '/files/skyforge.jar',
                        dependencies: [],
                        downloadCount: 0,
                        releaseDate: new Date().toISOString(),
                        changelog: 'Stable release.'
                    }]}
                    showExperimental={false}
                    onToggleExperimental={vi.fn()}
                    onDownload={onDownload}
                />
            );
        });

        expect(document.body.querySelector('a[href="#"]')).toBeNull();

        const downloadButton = Array.from(document.body.querySelectorAll('button'))
            .find((button) => button.textContent?.trim() === 'Download') as HTMLButtonElement;

        await act(async () => {
            downloadButton.click();
        });

        expect(onDownload).toHaveBeenCalledWith('/files/skyforge.jar', '1.0.0', '0.5.4', [], 'RELEASE');
    });

    it('warns when a modpack version includes external mods', async () => {
        const versionsByGame = {
            '0.5.4': [
                {
                    id: 'v1',
                    versionNumber: '1.0.0',
                    channel: 'RELEASE',
                    gameVersion: '0.5.4',
                    releaseDate: new Date().toISOString(),
                    dependencies: [
                        {
                            projectId: 'external-shader',
                            projectTitle: 'External Shader',
                            versionNumber: '2.0.0',
                            source: 'CURSEFORGE'
                        }
                    ]
                }
            ]
        };

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <DownloadModal
                        show={true}
                        onClose={vi.fn()}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={[]}
                        orderedGameVersions={['0.5.4']}
                        onDownload={vi.fn()}
                        showExperimental={false}
                        onToggleExperimental={vi.fn()}
                        onViewHistory={vi.fn()}
                        isModpack={true}
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).toContain('This modpack uses external mods');
        expect(container.textContent).toContain('External Shader');
    });

    it('does not show the external mod warning for non-modpack projects', async () => {
        const versionsByGame = {
            '0.5.4': [
                {
                    id: 'v1',
                    versionNumber: '1.0.0',
                    channel: 'RELEASE',
                    gameVersion: '0.5.4',
                    releaseDate: new Date().toISOString(),
                    dependencies: [
                        {
                            projectId: 'external-library',
                            projectTitle: 'External Library',
                            versionNumber: '2.0.0',
                            source: 'CURSEFORGE'
                        }
                    ]
                }
            ]
        };

        await act(async () => {
            root.render(
                <MemoryRouter>
                    <DownloadModal
                        show={true}
                        onClose={vi.fn()}
                        versionsByGame={versionsByGame}
                        preReleaseGameVersions={[]}
                        orderedGameVersions={['0.5.4']}
                        onDownload={vi.fn()}
                        showExperimental={false}
                        onToggleExperimental={vi.fn()}
                        onViewHistory={vi.fn()}
                    />
                </MemoryRouter>
            );
        });

        expect(container.textContent).not.toContain('This modpack uses external mods');
        expect(container.textContent).not.toContain('External Library');
    });
});
