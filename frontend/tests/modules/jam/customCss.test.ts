import { describe, expect, it } from 'vitest';
import { getScopedJamCss } from '@/modules/jam/utils/customCss';

describe('jam CSS scope', () => {
    it('scopes simple Markdown typography and removes comments', () => {
        expect(getScopedJamCss('/* Theme */ h1, h2 { color: #aabbcc; margin: 1rem 0; }'))
            .toBe('.jam-custom-content h1, .jam-custom-content h2 { color: #aabbcc; margin: 1rem 0; }');
    });
    it.each([
        'body { color: red; }', 'h1 { background-color: url(https://example.test/track); }',
        '@import "https://example.test/track";', 'h1 { position: fixed; }',
        'h1 { color: var(--secret); }', 'h1 { color: red !important; }',
        'h1 { color: red; } trailing', 'h1 { color: red; } </style>',
        'h1,,h2 { color: red; }'
    ])('fails closed for unsafe or malformed CSS: %s', css => {
        expect(getScopedJamCss(css)).toBe('');
    });
});
