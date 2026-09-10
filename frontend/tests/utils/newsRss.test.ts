import { describe, expect, it } from 'vitest';
import { NEWS_POSTS, getNewsPostUrl, getAbsoluteUrl } from '@/data/news';
import { buildNewsRssXml } from '@/utils/newsRss';

describe('news RSS utils', () => {
    it('builds an RSS feed with news post media and content entries', () => {
        const [post] = NEWS_POSTS;
        const xml = buildNewsRssXml();

        expect(xml).toContain('<rss version="2.0"');
        expect(xml).toContain('<title>Modtale News</title>');
        expect(xml).toContain(`<link>${getNewsPostUrl(post)}</link>`);
        expect(xml).toContain(`<media:content url="${getAbsoluteUrl(post.socialImage)}" medium="image" width="2400" height="1260" />`);
        expect(xml.match(/<item>/g)).toHaveLength(NEWS_POSTS.length);
        for (const entry of NEWS_POSTS) expect(xml).toContain(`<link>${getNewsPostUrl(entry)}</link>`);
        expect(xml).toContain('<dc:creator>Modtale Team</dc:creator>');
        expect(xml).toContain('<content:encoded><![CDATA[');
    });
});

describe('news RSS environment links', () => {
    it('keeps preview articles and images on the preview site with stable item identifiers', () => {
        const xml = buildNewsRssXml('https://dev.modtale.net');
        expect(xml).toContain('<link>https://dev.modtale.net/news/modtale-launcher</link>');
        expect(xml).toContain(`url="${new URL(NEWS_POSTS[0].socialImage, 'https://dev.modtale.net').href}"`);
        expect(xml).toContain('href="https://dev.modtale.net/rss.xml" rel="self"');
        expect(xml).toContain('<guid isPermaLink="true">https://modtale.net/news/modtale-launcher</guid>');
    });
});


describe('news RSS proxy origins', () => {
    it('uses HTTPS when TLS terminates before the frontend server', () => {
        const xml = buildNewsRssXml('http://dev.modtale.net');
        expect(xml).toContain('<link>https://dev.modtale.net/news/modtale-launcher</link>');
        expect(xml).not.toContain('http://dev.modtale.net');
    });

    it('preserves HTTP and the port for a local frontend', () => {
        expect(buildNewsRssXml('http://localhost:5173'))
            .toContain('<link>http://localhost:5173/news/modtale-launcher</link>');
    });
});
