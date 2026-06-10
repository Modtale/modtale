package net.modtale.controller.project;

import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import net.modtale.exception.ErrorMessageUtils;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.service.project.WikiService;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getWikiProject(@PathVariable String id) {
        return ResponseEntity.ok(wikiService.getWikiProject(id));
    }

    @GetMapping("/{id}/**")
    public ResponseEntity<JsonNode> getWikiPage(@PathVariable String id, HttpServletRequest request) {
        String path = request.getRequestURI();
        String searchStr = "/wiki/" + id + "/";
        int index = path.indexOf(searchStr);
        if (index == -1) {
            throw new InvalidProjectRequestException("Invalid wiki path.");
        }
        String pagePath = path.substring(index + searchStr.length());
        return ResponseEntity.ok(wikiService.getWikiPage(id, pagePath));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ProblemDetail> handleWikiUpstream(UpstreamServiceException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorMessageUtils.problemDetail(ex.getStatus(), ErrorMessageUtils.describe(ex, "Wiki upstream request failed.")));
    }
}
