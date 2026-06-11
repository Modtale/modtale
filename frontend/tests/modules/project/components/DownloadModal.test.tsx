import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { DownloadModal } from '@/modules/project/components/dialogs/DownloadModal';

describe('DownloadModal Toggle Visibility', () => {
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
        expect(container.textContent).not.toContain('Show Beta/Alpha');
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

        expect(container.textContent).toContain('Show Beta/Alpha');
        expect(container.textContent).toContain('Show Pre-Release Game Versions');
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

        expect(container.textContent).not.toContain('Show Beta/Alpha');
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

        expect(container.textContent).not.toContain('Show Pre-Release Game Versions');
    });
});
