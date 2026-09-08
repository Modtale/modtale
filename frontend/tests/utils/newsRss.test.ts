import { describe, expect, it } from 'vitest';
import { NEWS_POSTS, getNewsPostUrl } from '@/data/news';
import { buildNewsRssXml } from '@/utils/newsRss';

describe('news RSS utils', () => {
    it('builds an RSS feed with news post media and content entries', () => {
        const [post] = NEWS_POSTS;
        const xml = buildNewsRssXml();

        expect(xml).toContain('<rss version="2.0"');
        expect(xml).toContain('<title>Modtale News</title>');
        expect(xml).toContain(`<link>${getNewsPostUrl(post)}</link>`);
        expect(xml).toContain('<media:content url="https://modtale.net/assets/news/fresh-feature-showcase-og.png" medium="image" width="1200" height="630" />');
        expect(xml).toContain('<dc:creator>Modtale Team</dc:creator>');
        expect(xml).toContain('<content:encoded><![CDATA[');
    });
});
