package net.modtale.controller.resources;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import net.modtale.model.resources.Mod;
import net.modtale.service.resources.ModService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/og")
public class OgImageController {

    private final ModService modService;
    private final Cache<String, CachedRender> renderCache;
    private final Cache<String, BufferedImage> assetCache;
    private final SVGDocument logoDocument;

    private static final Color BRAND_ACCENT = new Color(59, 130, 246);
    private static final Color BRAND_DARK = new Color(15, 23, 42);
    private static final Color CARD_BG = new Color(30, 41, 59);
    private static final Color TEXT_PRIMARY = Color.WHITE;
    private static final Color TEXT_SECONDARY = new Color(148, 163, 184);
    private static final Color TEXT_DESC = new Color(203, 213, 225);
    private static final Color BORDER_COLOR = new Color(255, 255, 255, 25);
    private static final Color BADGE_BG = new Color(15, 23, 42, 204);

    private static final String LOGO_SVG = """
        <svg
                version="1.1"
                id="svg1"
                width="852.65533"
                height="128.03186"
                viewBox="0 0 852.65534 128.03186"
                xml:space="preserve"
                xmlns="http://www.w3.org/2000/svg">
        <g transform="translate(94.97958,-321.34393)">
        <g transform="translate(1.2421913,154.00018)">
        <path style="fill:#f8fafc;fill-opacity:1" d="m -88.979583,167.40448 v 0.006 h -7.242188 l 7.234375,12.11132 h 0.002 c 0.0012,1.4e-4 0.0041,9e-5 0.0059,0 v 115.8418 h 39.023437 v -55.04492 c 5.805598,10.36668 11.609363,20.73438 17.414063,31.10156 H -3.7745049 C 2.0301955,261.05292 7.8339592,250.68522 13.639558,240.31854 v 55.04492 h 39.023437 v -115.8457 l 7.232422,-12.10742 h -7.232422 v -0.006 H 16.280183 13.590729 c -10.5829104,19.578 -21.1638977,39.15704 -31.748046,58.73437 -10.584626,-19.57742 -21.166466,-39.15636 -31.75,-58.73437 h -2.699219 z" />
            <path style="fill:#f8fafc;fill-opacity:1" d="m 241.51651,167.39666 -43.61523,0.006 7.23437,12.11133 h 0.002 l 0.002,0.002 h 0.002 c 1e-5,34.58041 0,69.15975 0,103.74023 h -0.002 -0.002 -0.002 v 0.002 l -7.23437,12.10938 43.53711,0.008 h 0.0781 c 12.40466,2.2e-4 24.80869,-0.0124 37.21289,-0.0684 8.49123,-0.29698 17.18546,-2.16025 24.33985,-6.93359 7.61685,-5.00497 12.71894,-13.26895 14.7539,-22.06641 1.06842,-4.28969 1.45685,-8.71585 1.58203,-13.125 0.0337,-1.61815 0.0476,-3.45043 0.0254,-5.1543 -0.0624,-13.51029 0.0754,-27.02405 -0.10351,-40.5332 -0.36648,-8.45123 -2.36592,-17.09064 -7.27344,-24.10937 -4.97838,-7.21861 -12.81208,-12.24922 -21.30078,-14.27735 -0.86909,-0.21398 -1.83257,-0.43385 -2.57617,-0.57812 -4.82011,-0.95634 -9.75019,-1.19093 -14.6543,-1.1211 H 241.4833 c 0.0225,-0.008 0.0332,-0.0117 0.0332,-0.0117 z m 2.64649,32.19336 h 2 c 7.97837,0.0194 15.95788,-0.0389 23.93554,0.0293 2.91845,0.0887 6.11728,0.77647 8.07617,3.11914 2.12418,2.66141 2.25689,6.22814 2.21875,9.48242 -0.0243,13.88971 0.0695,27.78218 -0.084,41.66993 -0.21234,2.89103 -1.21879,6.06661 -3.83984,7.63867 -3.13238,1.87642 -6.90978,1.673 -10.42188,1.65234 -7.2949,0.0108 -14.58984,9.1e-4 -21.88476,0.004 z" />
            <path style="fill:#f8fafc;fill-opacity:1" d="m 312.97461,167.63672 c 4.10414,2.92023 7.67244,6.6112 10.27308,10.93653 4.2144,6.77326 6.57252,14.5723 7.56872,22.45214 9.01497,0 18.02994,0 27.04492,0 0,31.43229 0,62.86458 0,94.29688 13.12695,0 26.2539,0 39.38086,0 0,-31.4323 0,-62.86459 0,-94.29688 8.35091,0 16.70182,0 25.05273,0 4.44662,-11.22005 8.89323,-22.4401 13.33985,-33.66016 -41.02409,-0.007 -82.04818,-0.013 -123.07227,-0.0195 0.13737,0.097 0.27474,0.19401 0.41211,0.29102 z" />
            <path style="fill:#f8fafc;fill-opacity:1" d="m 460.41992,174.35938 c 0.45282,0.51534 0.54614,1.06433 0.17275,1.65701 -14.37528,39.76863 -28.75059,79.53725 -43.12587,119.30588 12.47591,0 24.95182,0 37.42773,0 1.85436,-5.51556 3.70702,-11.0317 5.5625,-16.54688 17.79037,0 35.58073,0 53.37109,0 2.36301,5.51574 4.72661,11.03123 7.08985,16.54688 12.65495,0 25.30989,0 37.96484,0 -14.53999,-40.23233 -29.07775,-80.46546 -43.61914,-120.69727 1.45875,-2.42449 2.91523,-4.85034 4.37305,-7.27539 -21.11979,0 -42.23958,0 -63.35938,0 1.38086,2.33659 2.76172,4.67318 4.14258,7.00977 z M 490.5,206.38672 c 4.63146,13.43686 9.26333,26.87359 13.89453,40.31055 -10.66211,0 -21.32422,0 -31.98633,0 5.40843,-15.23867 10.81957,-30.47637 16.22852,-45.71485 0.62109,1.80143 1.24219,3.60287 1.86328,5.4043 z" />
            <path style="fill:#f8fafc;fill-opacity:1" d="m 561.625,167.34961 c 2.41168,4.03698 4.82158,8.07502 7.23438,12.11133 0,38.62044 -10e-6,77.24088 0,115.86133 h 87.38281 c -4.39974,-11.26368 -8.79948,-22.52735 -13.19922,-33.79102 h -37.80274 v -94.1875 l -43.61523,0.006 z" />
            <path style="fill:#f8fafc;fill-opacity:1" d="m 665.08398,295.32227 h 87.3913 V 263.4043 h -55.88739 v -19.82422 h 28.22266 c 2.76172,-8.20834 5.52343,-16.41667 8.28515,-24.625 h -37.33398 v -21.06446 c 16.57357,10e-6 33.14713,0 49.7207,0 3.65039,-10.18033 7.30078,-20.36067 10.95117,-30.54101 h -91.34961 z" />
            <g transform="matrix(0.77142635,0,0,0.77142635,-179.66965,52.927569)">
        <path style="fill:#f8fafc;fill-opacity:1" d="m 395.64453,148.49805 c -3.94941,0.25606 -7.8891,0.70088 -11.76954,1.49094 -3.57847,0.70463 -7.11197,1.65331 -10.56425,2.82935 -4.06943,1.37759 -8.02998,3.07772 -11.83207,5.07691 -3.85267,2.02217 -7.54948,4.34295 -11.03565,6.94599 -3.44691,2.56687 -6.69332,5.40301 -9.69919,8.47377 -3.03831,3.10963 -5.84065,6.45068 -8.35513,9.99703 -2.51784,3.54894 -4.75919,7.29382 -6.69219,11.19169 -1.90871,3.84945 -3.51632,7.84724 -4.80647,11.94499 -1.29814,4.14826 -2.28058,8.39506 -2.92033,12.69403 -0.63207,4.26597 -0.93331,8.57916 -0.90073,12.891 0.0351,3.92472 0.35499,7.84197 0.93158,11.72389 0.65171,4.23079 1.61824,8.41207 2.90052,12.49693 1.29564,4.10313 2.90619,8.10679 4.82164,11.9606 1.9189,3.86075 4.13321,7.5755 6.63192,11.08999 2.51074,3.54812 5.31021,6.89095 8.34646,10.00155 3.00864,3.07836 6.25626,5.9247 9.70989,8.49523 3.53678,2.64828 7.29773,4.99442 11.20952,7.0483 3.84256,2.00438 7.84295,3.70494 11.94992,5.09041 4.12203,1.38435 8.35287,2.44309 12.64063,3.16909 4.23923,0.71119 8.53318,1.10131 12.83232,1.14663 4.34361,0.0535 8.69134,-0.24055 12.98963,-0.87211 4.30008,-0.62645 8.54927,-1.59988 12.69764,-2.89498 4.14422,-1.29089 8.18379,-2.91644 12.07495,-4.84068 3.90324,-1.92927 7.64785,-4.17756 11.20203,-6.69276 3.55289,-2.52086 6.90392,-5.3253 10.01768,-8.37271 3.11265,-3.04888 5.9817,-6.34564 8.58156,-9.84256 2.55881,-3.4526 4.85357,-7.10139 6.84468,-10.91066 2.02091,-3.84725 3.73357,-7.85614 5.12278,-11.97437 1.39025,-4.11892 2.45464,-8.348 3.18378,-12.63418 0.71906,-4.23777 1.116,-8.53175 1.16363,-12.83079 0.0598,-4.30791 -0.22096,-8.62164 -0.84369,-12.88541 -0.60404,-4.23736 -1.5436,-8.42887 -2.80876,-12.51841 -1.06342,-3.51585 -2.38742,-6.95445 -3.92052,-10.29167 -1.79973,-3.92013 -3.91113,-7.69637 -6.29796,-11.28837 -2.38438,-3.58804 -5.05175,-6.98621 -7.96276,-10.1609 -2.91234,-3.17077 -6.07263,-6.11104 -9.43634,-8.79698 -3.37845,-2.68305 -6.96388,-5.1011 -10.71021,-7.2391 -3.44893,-1.94946 -7.03089,-3.65483 -10.71589,-3.98563,-1.54405 -8.08234,-2.80101 -12.25552,-3.72958 -4.24306,-0.93958 -8.55932,-1.5419 -12.89682,-1.81617 -3.13994,-0.20076 -6.2858,-0.1728 -9.42874,-0.0566 z" />
                <path style="fill:#3b82f6;fill-opacity:1;stroke-width:0.710258" d="m 437.43623,210.62198 v 44.64745 l -38.66583,22.32373 -38.66583,-22.32373 v -44.64745 l 38.66583,-22.32372 z" />
        </g>
        </g>
        </g>
        </svg>
    """;

    public OgImageController(ModService modService) {
        this.modService = modService;
        this.renderCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(4, TimeUnit.HOURS)
                .recordStats()
                .build();
        this.assetCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();

        SVGLoader loader = new SVGLoader();
        this.logoDocument = loader.load(new ByteArrayInputStream(LOGO_SVG.getBytes(StandardCharsets.UTF_8)));
    }

    private static class CachedRender {
        final byte[] data;
        final String versionHash;
        final long timestamp;

        CachedRender(byte[] data, String versionHash) {
            this.data = data;
            this.versionHash = versionHash;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @GetMapping(value = {"/project/{id}", "/project/{id}.png"})
    public ResponseEntity<ByteArrayResource> generateOgImage(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        try {
            Mod mod = modService.getModById(id);
            if (mod == null) return ResponseEntity.notFound().build();

            String versionKey = generateEtag(mod);

            if (ifNoneMatch != null && (ifNoneMatch.equals(versionKey) || ifNoneMatch.equals("\"" + versionKey + "\""))) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(versionKey)
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                        .build();
            }

            CachedRender cached = renderCache.getIfPresent(mod.getId());
            if (cached != null && cached.versionHash.equals(versionKey)) {
                return serveImage(cached.data, versionKey);
            }

            CompletableFuture<BufferedImage> bannerFuture = CompletableFuture.supplyAsync(() ->
                    getOrFetchImage(mod.getBannerUrl())
            );
            CompletableFuture<BufferedImage> iconFuture = CompletableFuture.supplyAsync(() ->
                    getOrFetchImage(mod.getImageUrl())
            );

            BufferedImage banner = bannerFuture.get(2, TimeUnit.SECONDS);
            BufferedImage icon = iconFuture.get(2, TimeUnit.SECONDS);

            byte[] imageBytes = renderImage(mod, banner, icon);
            renderCache.put(mod.getId(), new CachedRender(imageBytes, versionKey));

            return serveImage(imageBytes, versionKey);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    private String generateEtag(Mod mod) {
        try {
            String raw = mod.getId() + "|" + mod.getUpdatedAt() + "|" + mod.getTitle() + "|" + mod.getDownloadCount() + "|" + mod.getFavoriteCount();
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "\"" + hexString + "\"";
        } catch (Exception e) {
            return "\"" + mod.getId() + "_" + System.currentTimeMillis() + "\"";
        }
    }

    private ResponseEntity<ByteArrayResource> serveImage(byte[] bytes, String etag) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .eTag(etag)
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private BufferedImage getOrFetchImage(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            String cacheKey = url;
            BufferedImage cached = assetCache.getIfPresent(cacheKey);
            if (cached != null) return cached;

            String fetchUrl = url.startsWith("/") ? "http://localhost:8080" + url : url;
            URL targetUrl = new URL(fetchUrl);
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.connect();

            try (var is = connection.getInputStream()) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    assetCache.put(cacheKey, img);
                }
                return img;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] renderImage(Mod mod, BufferedImage banner, BufferedImage icon) throws Exception {
        int width = 1200;
        int height = 630;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        setupRenderingHints(g2d);
        drawBackground(g2d, width, height);

        int margin = 60;
        int cardW = width - (margin * 2);
        int cardH = height - (margin * 2);
        int arc = 40;
        RoundRectangle2D cardShape = new RoundRectangle2D.Float(margin, margin, cardW, cardH, arc, arc);

        drawCardBackgroundWithBanner(g2d, mod, banner, cardShape, margin, margin, cardW, cardH);
        drawProjectContent(g2d, mod, icon, margin, margin, cardW, cardH);
        drawBranding(g2d, width, height);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private void setupRenderingHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private void drawBackground(Graphics2D g2d, int width, int height) {
        g2d.setColor(BRAND_DARK);
        g2d.fillRect(0, 0, width, height);
    }

    private void drawCardBackgroundWithBanner(Graphics2D g2d, Mod mod, BufferedImage banner, RoundRectangle2D cardShape, int x, int y, int w, int h) {
        g2d.setColor(CARD_BG);
        g2d.fill(cardShape);

        int headerH = 180;

        try {
            if (banner == null) {
                banner = createGradientBanner(w, headerH);
            }

            if (banner != null) {
                Area clipArea = new Area(cardShape);
                clipArea.intersect(new Area(new Rectangle2D.Float(x, y, w, headerH)));

                g2d.setClip(clipArea);

                double scale = Math.max((double) w / banner.getWidth(), (double) headerH / banner.getHeight());
                int scaledW = (int) (banner.getWidth() * scale);
                int scaledH = (int) (banner.getHeight() * scale);

                g2d.drawImage(banner, x + (w - scaledW) / 2, y + (headerH - scaledH) / 2, scaledW, scaledH, null);

                g2d.setClip(null);
            }
        } catch (Exception ignored) {
            BufferedImage gradient = createGradientBanner(w, headerH);
            Area clipArea = new Area(cardShape);
            clipArea.intersect(new Area(new Rectangle2D.Float(x, y, w, headerH)));
            g2d.setClip(clipArea);
            g2d.drawImage(gradient, x, y, w, headerH, null);
            g2d.setClip(null);
        }

        String type = mod.getClassification() != null ? mod.getClassification() : "PROJECT";
        drawCategoryBadge(g2d, type, x + w - 16, y + 16);

        g2d.setColor(BORDER_COLOR);
        g2d.setStroke(new BasicStroke(2f));
        g2d.draw(cardShape);

        g2d.setColor(new Color(255, 255, 255, 15));
        g2d.drawLine(x, y + headerH, x + w, y + headerH);
    }

    private BufferedImage createGradientBanner(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        GradientPaint gp = new GradientPaint(0, 0, new Color(30, 41, 59), w, h, new Color(15, 23, 42));
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private void drawProjectContent(Graphics2D g2d, Mod mod, BufferedImage icon, int cardX, int cardY, int cardW, int cardH) {
        int padding = 50;
        int iconSize = 180;
        int headerH = 180;

        int iconX = cardX + padding;
        int iconY = cardY + headerH - (iconSize / 2) + 20;
        drawIcon(g2d, mod, icon, iconX, iconY, iconSize);

        int textX = iconX + iconSize + 40;
        int textStartY = cardY + headerH + 30;
        int maxTextWidth = (cardX + cardW) - textX - padding;

        g2d.setFont(new Font("SansSerif", Font.BOLD, 48));
        g2d.setColor(TEXT_PRIMARY);
        String title = truncateText(g2d, mod.getTitle(), maxTextWidth);
        g2d.drawString(title, textX, textStartY + 30);

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g2d.setColor(TEXT_SECONDARY);
        g2d.drawString("by " + mod.getAuthor(), textX, textStartY + 75);

        int descY = textStartY + 140;
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 22));
        g2d.setColor(TEXT_DESC);

        String desc = mod.getDescription() != null ? mod.getDescription() : "";
        int descWidth = cardW - (padding * 2);
        drawWrappedText(g2d, desc, cardX + padding, descY, descWidth, 2);

        int statY = cardY + cardH - 45;
        int statX = cardX + padding;

        drawStatWithIcon(g2d, statX, statY, "download", formatNumber(mod.getDownloadCount()));
        drawStatWithIcon(g2d, statX + 160, statY, "heart", formatNumber(mod.getFavoriteCount()));
    }

    private void drawIcon(Graphics2D g2d, Mod mod, BufferedImage img, int x, int y, int size) {
        g2d.setColor(new Color(0,0,0,80));
        g2d.fillRoundRect(x + 5, y + 5, size, size, 30, 30);

        Shape clip = new RoundRectangle2D.Float(x, y, size, size, 30, 30);
        g2d.setClip(clip);

        try {
            if (img != null) {
                g2d.drawImage(img, x, y, size, size, null);
            } else {
                g2d.setColor(BRAND_ACCENT);
                g2d.fillRect(x, y, size, size);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 80));
                String l = mod.getTitle().substring(0, 1).toUpperCase();
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString(l, x + (size - fm.stringWidth(l)) / 2, y + (size + fm.getAscent()) / 2 - 15);
            }
        } catch (Exception ignored) {
            g2d.setColor(BRAND_ACCENT);
            g2d.fillRect(x, y, size, size);
        }

        g2d.setClip(null);

        g2d.setColor(new Color(255, 255, 255, 25));
        g2d.setStroke(new BasicStroke(4));
        g2d.drawRoundRect(x, y, size, size, 30, 30);
    }

    private void drawCategoryBadge(Graphics2D g2d, String text, int rightX, int topY) {
        String display = text.length() > 1
                ? text.charAt(0) + text.substring(1).toLowerCase()
                : text;

        g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
        FontMetrics fm = g2d.getFontMetrics();

        int iconW = 20;
        int gap = 8;
        int hPadding = 12;
        int vPadding = 8;

        int textW = fm.stringWidth(display);
        int w = hPadding + iconW + gap + textW + hPadding;
        int h = fm.getHeight() + vPadding;

        int x = rightX - w;
        int y = topY;

        g2d.setColor(BADGE_BG);
        g2d.fillRoundRect(x, y, w, h, 12, 12);

        int iconX = x + hPadding;
        int iconY = y + (h/2);

        g2d.setColor(BRAND_ACCENT);
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D iconPath = new Path2D.Float();
        iconPath.moveTo(iconX + 6, iconY - 4);
        iconPath.lineTo(iconX + 1, iconY);
        iconPath.lineTo(iconX + 6, iconY + 4);

        iconPath.moveTo(iconX + 10, iconY - 4);
        iconPath.lineTo(iconX + 15, iconY);
        iconPath.lineTo(iconX + 10, iconY + 4);
        g2d.draw(iconPath);

        g2d.setColor(Color.WHITE);
        g2d.drawString(display, iconX + iconW + gap, y + fm.getAscent() + (vPadding/2) + 2);
    }

    private void drawStatWithIcon(Graphics2D g2d, int x, int y, String iconType, String value) {
        g2d.setColor(TEXT_SECONDARY);
        Graphics2D iconG = (Graphics2D) g2d.create();

        iconG.translate(x, y - 21);
        iconG.scale(1.1, 1.1);
        iconG.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if ("download".equals(iconType)) {
            Path2D path = new Path2D.Float();
            path.moveTo(21, 15);
            path.lineTo(21, 19);
            path.curveTo(21, 20, 20, 21, 19, 21);
            path.lineTo(5, 21);
            path.curveTo(4, 21, 3, 20, 3, 19);
            path.lineTo(3, 15);
            iconG.draw(path);

            Path2D arrow = new Path2D.Float();
            arrow.moveTo(7, 10);
            arrow.lineTo(12, 15);
            arrow.lineTo(17, 10);
            arrow.moveTo(12, 15);
            arrow.lineTo(12, 3);
            iconG.draw(arrow);
        } else {
            Path2D heart = new Path2D.Float();
            heart.moveTo(12, 21.35);

            heart.curveTo(12, 21.35, 2.5, 13, 2.5, 8.5);
            heart.curveTo(2.5, 5, 5, 3, 8, 3);
            heart.curveTo(10, 3, 11, 4, 12, 5.5);
            heart.curveTo(13, 4, 14, 3, 16, 3);
            heart.curveTo(19, 3, 21.5, 5, 21.5, 8.5);
            heart.curveTo(21.5, 13, 12, 21.35, 12, 21.35);

            iconG.draw(heart);
        }
        iconG.dispose();

        g2d.setColor(TEXT_PRIMARY);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
        g2d.drawString(value, x + 35, y + 4);
    }

    private void drawBranding(Graphics2D g2d, int width, int height) {
        if (logoDocument == null) return;

        float logoHeight = 40f;
        float logoWidth = (float) (logoHeight * (logoDocument.size().width / logoDocument.size().height));

        float x = width - 100 - logoWidth;
        float y = height - 98 - logoHeight;

        Graphics2D logoG = (Graphics2D) g2d.create();
        logoG.translate(x, y);

        double scale = logoHeight / logoDocument.size().height;
        logoG.scale(scale, scale);

        logoDocument.render(null, logoG);
        logoG.dispose();
    }

    private String truncateText(Graphics2D g2d, String text, int maxWidth) {
        if (text == null) return "";
        FontMetrics fm = g2d.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisW = fm.stringWidth(ellipsis);
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 0 && fm.stringWidth(sb.toString()) + ellipsisW > maxWidth) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString() + ellipsis;
    }

    private void drawWrappedText(Graphics2D g2d, String text, int x, int y, int maxWidth, int maxLines) {
        if (text == null || text.isEmpty()) return;

        FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight() + 4;
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        int lineCount = 0;

        for (String word : words) {
            String separator = currentLine.length() == 0 ? "" : " ";
            String testLine = currentLine + separator + word;

            if (fm.stringWidth(testLine) <= maxWidth) {
                currentLine.append(separator).append(word);
            } else {
                if (lineCount < maxLines - 1) {
                    g2d.drawString(currentLine.toString(), x, y + (lineCount * lineHeight));
                    currentLine = new StringBuilder(word);
                    lineCount++;
                } else {
                    String line = currentLine.toString();
                    String ellipsis = "...";
                    while (fm.stringWidth(line + ellipsis) > maxWidth && line.length() > 0) {
                        line = line.substring(0, line.length() - 1);
                    }
                    g2d.drawString(line + ellipsis, x, y + (lineCount * lineHeight));
                    return;
                }
            }
        }
        if (currentLine.length() > 0 && lineCount < maxLines) {
            g2d.drawString(currentLine.toString(), x, y + (lineCount * lineHeight));
        }
    }

    private String formatNumber(long count) {
        if (count < 1000) return String.valueOf(count);
        int exp = (int) (Math.log(count) / Math.log(1000));
        return String.format("%.1f%c", count / Math.pow(1000, exp), "kMGTPE".charAt(exp - 1));
    }
}