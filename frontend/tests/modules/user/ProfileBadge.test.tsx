import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { Badge } from '@/modules/user/components/ProfileLayout';

describe('Profile badge', () => {
    it('renders a data-defined image badge without badge-specific UI code', () => {
        const markup = renderToStaticMarkup(<Badge type={{
            id: 'community-team',
            label: 'Community Team',
            tooltip: 'Community Team Member',
            imageUrl: 'https://cdn.example.test/community-team-dark.svg',
            darkImageUrl: 'https://cdn.example.test/community-team-light.svg'
        }} />);

        expect(markup).toContain('title="Community Team Member"');
        expect(markup).toContain('src="https://cdn.example.test/community-team-dark.svg"');
        expect(markup).toContain('src="https://cdn.example.test/community-team-light.svg"');
        expect(markup).toContain('alt="Community Team"');
    });
});
