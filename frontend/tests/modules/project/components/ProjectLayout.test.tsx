import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ProjectLayout } from '@/modules/project/components/ProjectLayout';

const layoutProps = {
    iconUrl: '/icon.png',
    headerContent: <h1>Project Title</h1>,
    mainContent: <div>Main content</div>,
    sidebarContent: <div>Sidebar content</div>
};

describe('ProjectLayout banner rendering', () => {
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

    it('does not render a public banner area when the project has no banner', async () => {
        await act(async () => {
            root.render(<ProjectLayout {...layoutProps} bannerUrl={null} />);
        });

        expect(container.querySelector('.modtale-project-banner-parallax')).toBeNull();
        expect(container.textContent).not.toContain('Upload Banner');
    });

    it('renders the banner area when the project has a banner', async () => {
        await act(async () => {
            root.render(<ProjectLayout {...layoutProps} bannerUrl="/uploads/banner.png" />);
        });

        expect(container.querySelector('.modtale-project-banner-parallax')).not.toBeNull();
        expect(container.querySelector('img[alt="Project Banner"]')).not.toBeNull();
    });

    it('keeps the banner upload target visible while editing without a banner', async () => {
        const onBannerUpload = vi.fn();

        await act(async () => {
            root.render(
                <ProjectLayout
                    {...layoutProps}
                    bannerUrl={null}
                    isEditing={true}
                    onBannerUpload={onBannerUpload}
                />
            );
        });

        expect(container.querySelector('.modtale-project-banner-parallax')).not.toBeNull();
        expect(container.textContent).toContain('Upload Banner');
    });
});
