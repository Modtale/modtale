import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

describe('Layout Alignment Verification', () => {
    const EXPECTED_PADDING_CLASSES = 'px-6 sm:px-12 md:px-16 lg:px-20 xl:px-28';

    const filesToVerify = [
        'src/modules/admin/views/AdminPanel.tsx',
        'src/modules/core/views/ApiDocs.tsx',
        'src/modules/core/views/PrivacyPolicy.tsx',
        'src/modules/core/views/TermsOfService.tsx',
        'src/modules/discovery/views/Browse.tsx',
        'src/modules/project/components/ProjectLayout.tsx',
        'src/modules/project/views/CreateProject.tsx',
        'src/modules/user/components/ProfileLayout.tsx',
        'src/modules/user/views/Dashboard.tsx',
        'src/modules/core/components/Navbar.tsx',
        'src/modules/core/components/Footer.tsx',
        'src/modules/home/views/Home.tsx',
    ];

    it('ensures all main page layout containers use the correct horizontal padding classes matching the navbar', () => {
        filesToVerify.forEach((relativeFilePath) => {
            const absolutePath = path.resolve(__dirname, '../../', relativeFilePath);
            expect(fs.existsSync(absolutePath)).toBe(true);

            const content = fs.readFileSync(absolutePath, 'utf8');

            expect(content).toContain(EXPECTED_PADDING_CLASSES);
        });
    });

    it('keeps navbar profile avatars from stretching or shrinking at narrow desktop widths', () => {
        const navbarPath = path.resolve(__dirname, '../../src/modules/core/components/Navbar.tsx');
        const content = fs.readFileSync(navbarPath, 'utf8');

        expect(content).toContain('flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full');
        expect(content).toContain('h-full w-full shrink-0 rounded-full object-cover');
        expect(content).toContain('h-6 w-6 shrink-0 rounded-full object-cover');
    });
});
