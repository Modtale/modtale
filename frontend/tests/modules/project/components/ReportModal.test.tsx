import React, { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { ReportModal } from '@/modules/project/components/dialogs/ReportModal';
import { projectClient } from '@/modules/project/api/projectClient';

vi.mock('@/modules/project/api/projectClient', () => ({
    projectClient: {
        submitReport: vi.fn()
    }
}));

const mockedProjectClient = vi.mocked(projectClient);

const settle = async () => {
    await act(async () => {
        await Promise.resolve();
    });
};

const findButton = (text: string) => Array.from(document.body.querySelectorAll('button')).find((button) => (
    button.textContent?.trim() === text
));

describe('ReportModal', () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        mockedProjectClient.submitReport.mockResolvedValue({ id: 'report-1' } as any);
    });

    afterEach(async () => {
        await act(async () => {
            root.unmount();
        });
        container.remove();
        document.body.innerHTML = '';
        vi.clearAllMocks();
    });

    it('submits inaccurate license as a project report reason', async () => {
        await act(async () => {
            root.render(
                <ReportModal
                    isOpen
                    onClose={vi.fn()}
                    targetId="project-1"
                    targetType="PROJECT"
                    targetTitle="Sky Tools"
                />
            );
        });

        await act(async () => {
            findButton('Malware / Virus')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        const inaccurateLicenseButton = findButton('Inaccurate License');
        expect(inaccurateLicenseButton).toBeDefined();

        await act(async () => {
            inaccurateLicenseButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement;
        await act(async () => {
            Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')?.set?.call(
                textarea,
                'The listed license does not match the published source.'
            );
            textarea.dispatchEvent(new Event('input', { bubbles: true }));
        });

        await act(async () => {
            findButton('Submit Report')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });
        await settle();

        expect(mockedProjectClient.submitReport).toHaveBeenCalledWith(
            'project-1',
            'PROJECT',
            'INACCURATE_LICENSE',
            'The listed license does not match the published source.'
        );
    });

    it('does not show inaccurate license for user reports', async () => {
        await act(async () => {
            root.render(
                <ReportModal
                    isOpen
                    onClose={vi.fn()}
                    targetId="user-1"
                    targetType="USER"
                    targetTitle="Ada"
                />
            );
        });

        await act(async () => {
            findButton('Malware / Virus')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        });

        expect(document.body.textContent).not.toContain('Inaccurate License');
    });
});
