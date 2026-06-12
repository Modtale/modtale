package net.modtale.service.system;

import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.ProjectService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SitemapService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final String baseUrl;

    public SitemapService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            AppFrontendProperties frontendProperties
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.baseUrl = frontendProperties.url();
    }

    @Cacheable(value = "sitemapData", key = "'sitemap.xml'", sync = true)
    public String generateSitemap() {
        LocalDate today = LocalDate.now();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        addUrl(xml, baseUrl + "/", "1.0", today);
        addUrl(xml, baseUrl + "/plugins", "0.9", today);
        addUrl(xml, baseUrl + "/modpacks", "0.9", today);
        addUrl(xml, baseUrl + "/worlds", "0.9", today);
        addUrl(xml, baseUrl + "/data", "0.9", today);
        addUrl(xml, baseUrl + "/art", "0.9", today);
        addUrl(xml, baseUrl + "/api-docs", "0.8", today);

        Set<String> activeAuthors = new HashSet<>();
        List<Project> projects = projectRepository.findAllForSitemap();

        for (Project project : projects) {
            if (project.getUpdatedAt() != null) {
                addUrl(xml, baseUrl + projectService.getProjectLink(project), "0.8", parseDate(project.getUpdatedAt(), today));
                if (project.getAuthorId() != null && !project.getAuthorId().isBlank()) {
                    activeAuthors.add(project.getAuthorId());
                }
            }
        }

        for (String author : activeAuthors) {
            if (author != null && !author.isBlank()) {
                addUrl(xml, baseUrl + "/creator/" + author, "0.7", today);
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

    private LocalDate parseDate(String dateStr, LocalDate fallback) {
        try {
            if (dateStr == null) return fallback;
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
