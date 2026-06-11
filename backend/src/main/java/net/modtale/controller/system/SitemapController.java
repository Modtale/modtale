package net.modtale.controller.system;

import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.project.Project;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.SearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
public class SitemapController {

    private final SearchService searchService;
    private final ProjectService projectService;
    private final String baseUrl;

    public SitemapController(
            SearchService searchService,
            ProjectService projectService,
            AppFrontendProperties frontendProperties
    ) {
        this.searchService = searchService;
        this.projectService = projectService;
        this.baseUrl = frontendProperties.url();
    }

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

        addUrl(xml, baseUrl + "/api-docs", "0.8", LocalDate.now());

        Set<String> activeAuthors = new HashSet<>();

        List<Project> projects = searchService.getPublishedProjects();

        for (Project p : projects) {
            if (p.getUpdatedAt() != null) {
                addUrl(xml, baseUrl + projectService.getProjectLink(p), "0.8", parseDate(p.getUpdatedAt()));
                if (p.getAuthorId() != null && !p.getAuthorId().isBlank()) {
                    activeAuthors.add(p.getAuthorId());
                }
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
            if (dateStr == null) return LocalDate.now();
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}
