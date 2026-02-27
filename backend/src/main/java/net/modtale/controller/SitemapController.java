package net.modtale.controller;

import net.modtale.model.jam.Modjam;
import net.modtale.model.resources.Mod;
import net.modtale.service.ModjamService;
import net.modtale.service.resources.ModService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
public class SitemapController {

    @Autowired private ModService modService;
    @Autowired private ModjamService modjamService;

    @Value("${app.frontend.url:https://modtale.net}")
    private String baseUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String generateSitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        addUrl(xml, baseUrl + "/", "1.0", LocalDate.now());
        addUrl(xml, baseUrl + "/plugins", "0.9", LocalDate.now());
        addUrl(xml, baseUrl + "/modpacks", "0.9", LocalDate.now());
        addUrl(xml, baseUrl + "/worlds", "0.9", LocalDate.now());
        addUrl(xml, baseUrl + "/data", "0.9", LocalDate.now());
        addUrl(xml, baseUrl + "/art", "0.9", LocalDate.now());
        addUrl(xml, baseUrl + "/jams", "0.9", LocalDate.now());

        addUrl(xml, baseUrl + "/api-docs", "0.8", LocalDate.now());

        Set<String> activeAuthors = new HashSet<>();

        List<Modjam> jams = modjamService.getAllJams();
        for (Modjam jam : jams) {
            if ("DRAFT".equals(jam.getStatus())) continue;

            String priority = "COMPLETED".equals(jam.getStatus()) ? "0.6" : "0.8";
            LocalDate lastMod = jam.getUpdatedAt() != null ? jam.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDate() : LocalDate.now();

            addUrl(xml, baseUrl + "/jam/" + jam.getSlug(), priority, lastMod);

            if (jam.getHostName() != null) {
                activeAuthors.add(jam.getHostName());
            }
        }

        List<Mod> projects = modService.getPublishedMods();

        for (Mod p : projects) {
            String prefix = "/mod/";
            if ("MODPACK".equals(p.getClassification())) prefix = "/modpack/";
            else if ("SAVE".equals(p.getClassification())) prefix = "/world/";

            String slug = (p.getSlug() != null && !p.getSlug().isBlank()) ? p.getSlug() : createSlug(p.getTitle(), p.getId());

            if (p.getUpdatedAt() != null) {
                addUrl(xml, baseUrl + prefix + slug, "0.8", parseDate(p.getUpdatedAt()));
                activeAuthors.add(p.getAuthor());
            }
        }

        for (String author : activeAuthors) {
            if (author != null && !author.isBlank()) {
                addUrl(xml, baseUrl + "/creator/" + author, "0.7", LocalDate.now());
            }
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    private void addUrl(StringBuilder xml, String loc, String priority, LocalDate lastMod) {
        xml.append("\t<url>\n");
        xml.append("\t\t<loc>").append(loc).append("</loc>\n");
        xml.append("\t\t<lastmod>").append(lastMod.format(DateTimeFormatter.ISO_DATE)).append("</lastmod>\n");
        xml.append("\t\t<priority>").append(priority).append("</priority>\n");
        xml.append("\t</url>\n");
    }

    private LocalDate parseDate(String dateStr) {
        try {
            if (dateStr == null || dateStr.length() < 10) return LocalDate.now();
            return LocalDate.parse(dateStr.substring(0, 10));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private String createSlug(String title, String id) {
        if (title == null) return id;
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 30) slug = slug.substring(0, 30);
        return slug.isEmpty() ? id : slug + "-" + id;
    }
}