package net.modtale.controller.project;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.service.project.WikiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wiki")
public class WikiProxyController {

    private final WikiService wikiService;

    public WikiProxyController(WikiService wikiService) {
        this.wikiService = wikiService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getWikiProject(@PathVariable String slug) {
        return wikiService.getWikiProject(slug);
    }

    @GetMapping("/{slug}/**")
    public ResponseEntity<?> getWikiPage(@PathVariable String slug, HttpServletRequest request) {
        String path = request.getRequestURI();
        String searchStr = "/wiki/" + slug + "/";
        int index = path.indexOf(searchStr);
        if (index == -1) return ResponseEntity.badRequest().body("Invalid path");
        String pagePath = path.substring(index + searchStr.length());
        return wikiService.getWikiPage(slug, pagePath);
    }
}
