import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { BrowseFilters } from '@/modules/discovery/components/BrowseFilters';
import { projectClient } from '@/modules/project/api/projectClient';

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        getMetaGameVersionCatalog: vi.fn(),
        getProjectGameVersions: vi.fn()
    }
}));

const mockedProjectClient = vi.mocked(projectClient);

const settle = async (times = 4) => {
    for (let i = 0; i < times; i += 1) {
        await act(async () => {
            await Promise.resolve();
        });
    }
};

const renderFilters = (
    isFilterOpen: boolean,
    selectedVersion = 'Any',
    setSelectedVersion = vi.fn(),
    onResetFilters = vi.fn(),
    totalItems = 12,
    itemsPerPage = 12,
    onItemsPerPageChange = vi.fn()
) => (
    <BrowseFilters
        pageTitle="All Projects"
        totalItems={totalItems}
        loading={false}
        sortBy="relevance"
        onSortChange={vi.fn()}
        selectedTags={[]}
        onToggleTag={vi.fn()}
        onClearTags={vi.fn()}
        activeFilterCount={0}
        onResetFilters={onResetFilters}
        isFilterOpen={isFilterOpen}
        onToggleFilterMenu={vi.fn()}
        searchTerm=""
        onSearchChange={vi.fn()}
        selectedVersion={selectedVersion}
        setSelectedVersion={setSelectedVersion}
        minFavorites={0}
        setMinFavorites={vi.fn()}
        minDownloads={0}
        setMinDownloads={vi.fn()}
        filterDate={null}
        setFilterDate={vi.fn()}
        setPage={vi.fn()}
        isMobile={false}
        viewStyle="grid"
        onViewStyleChange={vi.fn()}
        itemsPerPage={itemsPerPage}
        onItemsPerPageChange={onItemsPerPageChange}
        isScrolled={false}
    />
);

const openGameVersionDropdown = async (container: HTMLDivElement) => {
    const dropdownButton = Array.from(container.querySelectorAll('button')).find(
        (button) => button.getAttribute('aria-label') === 'Game version filter'
    );

    expect(dropdownButton).toBeTruthy();

    await act(async () => {
        dropdownButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
};

describe('BrowseFilters performance behavior', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        vi.clearAllMocks();
        mockedProjectClient.getMetaGameVersionCatalog.mockResolvedValue({
            orderedVersions: ['2026.03.11', '0.6.0', '0.5.4', '0.5.3', '0.5.2-pre.1'],
            allVersions: ['2026.03.11', '0.6.0', '0.5.4', '0.5.3', '0.5.2-pre.1'],
            releaseVersions: ['2026.03.11', '0.6.0', '0.5.4', '0.5.3'],
            preReleaseVersions: ['0.5.2-pre.1']
        } as any);
        mockedProjectClient.getProjectGameVersions.mockResolvedValue(['2026.03.11', '0.6.0', '0.5.4', '0.5.3'] as any);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
    });

    it('defers game version metadata until the filter menu is opened', async () => {
        await act(async () => {
            root.render(renderFilters(false));
        });
        await settle();

        expect(mockedProjectClient.getMetaGameVersionCatalog).not.toHaveBeenCalled();
        expect(mockedProjectClient.getProjectGameVersions).not.toHaveBeenCalled();

        await act(async () => {
            root.render(renderFilters(true));
        });
        await settle();

        expect(mockedProjectClient.getMetaGameVersionCatalog).toHaveBeenCalledTimes(1);
    });

    it("reselects Any when filters are reset", async () => {
        const setSelectedVersion = vi.fn();
        const onResetFilters = vi.fn();

        await act(async () => {
            root.render(renderFilters(true, '2026.03.11', setSelectedVersion, onResetFilters));
        });
        await settle();

        const resetButton = Array.from(container.querySelectorAll('button')).find(
            (button) => button.textContent?.includes('Reset Filters')
        );

        expect(resetButton).toBeTruthy();
        setSelectedVersion.mockClear();

        await act(async () => {
            resetButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(onResetFilters).toHaveBeenCalledTimes(1);
        expect(setSelectedVersion).toHaveBeenCalledWith('Any');
    });

    it('adds individual game versions to the active filter', async () => {
        const setSelectedVersion = vi.fn();

        await act(async () => {
            root.render(renderFilters(true, '0.5.4', setSelectedVersion));
        });
        await settle();
        await openGameVersionDropdown(container);

        const expandRangeButton = Array.from(container.querySelectorAll('button')).find(
            (button) => button.getAttribute('aria-label') === 'Expand 0.5.x versions'
        );

        expect(expandRangeButton).toBeTruthy();

        await act(async () => {
            expandRangeButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        const versionButton = Array.from(container.querySelectorAll('button')).find(
            (button) => button.textContent?.trim() === '0.5.3'
        );

        expect(versionButton).toBeTruthy();

        await act(async () => {
            versionButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(setSelectedVersion).toHaveBeenCalledWith('0.5.4,0.5.3');
    });

    it('selects a whole minor version group from the nested group row', async () => {
        const setSelectedVersion = vi.fn();

        await act(async () => {
            root.render(renderFilters(true, 'Any', setSelectedVersion));
        });
        await settle();
        await openGameVersionDropdown(container);

        expect(container.textContent).not.toContain('Ranges');

        const rangeButton = Array.from(container.querySelectorAll('button')).find(
            (button) => button.getAttribute('aria-label') === 'Select all 0.5.x versions'
        );

        expect(rangeButton).toBeTruthy();

        await act(async () => {
            rangeButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(setSelectedVersion).toHaveBeenCalledWith('0.5.4,0.5.3');
    });

    it('shows a singular result count when there is exactly one result', async () => {
        await act(async () => {
            root.render(renderFilters(false, 'Any', vi.fn(), vi.fn(), 1));
        });

        expect(container.textContent).toContain('1 result');
        expect(container.textContent).not.toContain('1 results');
    });

    it('offers compact results-per-page choices from the toolbar dropdown', async () => {
        const onItemsPerPageChange = vi.fn();

        await act(async () => {
            root.render(renderFilters(false, 'Any', vi.fn(), vi.fn(), 12, 12, onItemsPerPageChange));
        });

        const pageSizeButton = Array.from(container.querySelectorAll('button')).find(
            (button) => button.getAttribute('aria-label') === 'Results per page'
        );

        expect(pageSizeButton).toBeTruthy();

        await act(async () => {
            pageSizeButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        const optionLabels = Array.from(container.querySelectorAll('button'))
            .map(button => button.textContent?.trim())
            .filter(Boolean);

        expect(optionLabels).toEqual(expect.arrayContaining([
            '6',
            '12',
            '24',
            '48',
            '96'
        ]));

        const option = Array.from(container.querySelectorAll('button')).find(
            (button) => button.textContent?.trim() === '96'
        );

        await act(async () => {
            option?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(onItemsPerPageChange).toHaveBeenCalledWith(96);
    });
});
