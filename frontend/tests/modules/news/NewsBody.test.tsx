import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { NewsBody } from '@/modules/news/components/NewsBody';
vi.mock('@/modules/news/components/FeatureDemo', () => ({ FeatureDemo: ({ clip, alt }: any) => <figure data-clip={clip}>{alt}</figure> }));

describe('Published news formatting', () => {
    it('preserves rich formatting, tables, anchored headings, and existing demos', () => {
        const html = renderToStaticMarkup(<NewsBody content={'<h2 id="creators">Build</h2><p style="text-align: center"><strong>Bold</strong><u>Underlined</u></p><table><tbody><tr><td colspan="2">Cell</td></tr></tbody></table><div data-demo-clip="modpack-creation" data-demo-alt="Create a pack"></div><video src="/demo.mp4" controls></video>'} />);
        expect(html).toContain('id="creators"'); expect(html).toContain('text-align:center'); expect(html).toContain('<strong>Bold</strong>');
        expect(html).toContain('<u>Underlined</u>'); expect(html).toContain('colSpan="2"'); expect(html).toContain('data-clip="modpack-creation"'); expect(html).toContain('<video');
    });
    it('strips executable markup and unsafe links', () => {
        const html = renderToStaticMarkup(<NewsBody content={'<script>alert(1)</script><img src="/cover.png" onerror="alert(1)"><a href="javascript:alert(1)">Link</a><iframe src="https://evil.example"></iframe>'} />);
        expect(html).not.toContain('<script'); expect(html).not.toContain('onerror'); expect(html).not.toContain('javascript:'); expect(html).not.toContain('<iframe');
    });
});
