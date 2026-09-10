package net.modtale.launcher.news;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import net.modtale.launcher.settings.LauncherConfig;

public final class ModtaleNewsClient {
    private static final int MAX_FEED_BYTES = 2 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final URI feed;

    public ModtaleNewsClient() { this(URI.create(LauncherConfig.siteBaseUrl() + "/rss.xml")); }
    public ModtaleNewsClient(URI feed) { this.feed = feed; }

    public List<LauncherNewsPost> fetch() {
        var request = HttpRequest.newBuilder(feed).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/rss+xml, application/xml")
                .header("User-Agent", "ModtaleLauncher").GET().build();
        try {
            var response = client.send(request, info -> limitedBody());
            if (response.statusCode() != 200) throw new IOException("News feed returned " + response.statusCode());
            return parse(response.body(), feed);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Modtale news request interrupted", error);
        } catch (IOException error) {
            throw new IllegalStateException("Could not load Modtale news", error);
        }
    }

    private static HttpResponse.BodySubscriber<byte[]> limitedBody() {
        return new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
            private java.util.concurrent.Flow.Subscription subscription;
            private long size;
            private boolean failed;

            @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            @Override public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
                subscription = value;
                delegate.onSubscribe(value);
            }
            @Override public void onNext(List<java.nio.ByteBuffer> buffers) {
                if (failed) return;
                for (var buffer : buffers) size += buffer.remaining();
                if (size > MAX_FEED_BYTES) {
                    failed = true;
                    subscription.cancel();
                    delegate.onError(new IOException("News feed is too large"));
                } else {
                    delegate.onNext(buffers);
                }
            }
            @Override public void onError(Throwable error) { if (!failed) delegate.onError(error); }
            @Override public void onComplete() { if (!failed) delegate.onComplete(); }
        };
    }

    static List<LauncherNewsPost> parse(byte[] bytes, URI base) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            if (!"rss".equals(document.getDocumentElement().getTagName()))
                throw new IllegalArgumentException("Expected an RSS feed");
            var items = document.getElementsByTagName("item");
            List<LauncherNewsPost> posts = new ArrayList<>();
            for (int i = 0; i < items.getLength(); i++) {
                var item = (Element) items.item(i);
                String title = text(item, "title");
                String url = webUrl(base, text(item, "link"));
                if (title.isBlank() || url.isBlank()) continue;
                String image = attribute(item, "media:content", "url");
                if (image.isBlank()) image = attribute(item, "enclosure", "url");
                Instant published;
                try { published = ZonedDateTime.parse(text(item, "pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
                catch (java.time.format.DateTimeParseException invalid) { published = Instant.EPOCH; }
                posts.add(new LauncherNewsPost(title, url, webUrl(base, image), published, "Modtale"));
            }
            return List.copyOf(posts);
        } catch (Exception error) {
            throw new IllegalArgumentException("Could not parse Modtale news feed", error);
        }
    }

    private static String text(Element item, String name) {
        var nodes = item.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }
    private static String attribute(Element item, String name, String attribute) {
        var nodes = item.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : ((Element) nodes.item(0)).getAttribute(attribute).trim();
    }
    private static String webUrl(URI base, String raw) {
        if (raw.isBlank()) return "";
        try {
            URI uri = base.resolve(raw);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null
                    ? uri.toString() : "";
        } catch (IllegalArgumentException invalid) { return ""; }
    }
}
