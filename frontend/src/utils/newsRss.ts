import {
    NEWS_INDEX_PATH,
    NEWS_POSTS,
    NEWS_RSS_PATH,
    SITE_URL,
    getNewsPostUrl,
    getNewsPostPath,
    getLatestNewsPostDate,
} from '@/data/news';

const escapeXml = (value: string) => value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');

const cdata = (value: string) => `<![CDATA[${value.replace(/]]>/g, ']]]]><![CDATA[>')}]]>`;

export const buildNewsRssXml = (siteUrl = SITE_URL) => {
    const absoluteUrl = (path: string) => new URL(path, siteUrl).href;
    const lastUpdated = getLatestNewsPostDate();
    const lastBuildDate = typeof lastUpdated === 'number' && Number.isFinite(lastUpdated)
        ? new Date(lastUpdated).toUTCString()
        : new Date().toUTCString();

    const items = NEWS_POSTS.map((post) => {
        const postUrl = absoluteUrl(getNewsPostPath(post));
        const imageUrl = absoluteUrl(post.socialImage);
        const categories = post.tags
            .map((tag) => `      <category>${escapeXml(tag)}</category>`)
            .join('\n');

        const encodedContent = `
<figure>
  <img src="${escapeXml(imageUrl)}" alt="${escapeXml(post.socialImageAlt)}" width="1200" height="630" />
</figure>
<p>${escapeXml(post.excerpt)}</p>
<p><a href="${escapeXml(postUrl)}">Read the full post on Modtale</a></p>`.trim();

        return `    <item>
      <title>${escapeXml(post.title)}</title>
      <link>${escapeXml(postUrl)}</link>
      <guid isPermaLink="true">${escapeXml(getNewsPostUrl(post))}</guid>
      <description>${escapeXml(post.description)}</description>
      <pubDate>${new Date(post.publishedAt).toUTCString()}</pubDate>
      <dc:creator>${escapeXml(post.author)}</dc:creator>
${categories}
      <media:content url="${escapeXml(imageUrl)}" medium="image" width="1200" height="630" />
      <content:encoded>${cdata(encodedContent)}</content:encoded>
    </item>`;
    }).join('\n');

    return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:media="http://search.yahoo.com/mrss/">
  <channel>
    <title>Modtale News</title>
    <link>${escapeXml(absoluteUrl(NEWS_INDEX_PATH))}</link>
    <description>Product updates, creator notes, and feature tours from Modtale.</description>
    <language>en-us</language>
    <lastBuildDate>${lastBuildDate}</lastBuildDate>
    <atom:link href="${escapeXml(absoluteUrl(NEWS_RSS_PATH))}" rel="self" type="application/rss+xml" />
    <image>
      <url>${escapeXml(absoluteUrl("/assets/favicon.png"))}</url>
      <title>Modtale News</title>
      <link>${escapeXml(absoluteUrl(NEWS_INDEX_PATH))}</link>
    </image>
${items}
  </channel>
</rss>`;
};

const NEWS_RSS_HEADERS = {
    'Content-Type': 'application/rss+xml; charset=utf-8',
    'Cache-Control': 'public, max-age=900, s-maxage=3600, stale-while-revalidate=86400',
};

export const buildNewsRssHeadResponse = () => new Response(null, {
    headers: NEWS_RSS_HEADERS,
});

export const buildNewsRssResponse = (siteUrl = SITE_URL) => new Response(buildNewsRssXml(siteUrl), {
    headers: NEWS_RSS_HEADERS,
});
