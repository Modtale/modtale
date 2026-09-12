package net.modtale.status;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatusPageController {

    private final Resource statusPage = new ClassPathResource("status-static/index.html");

    @GetMapping({"/", "/status"})
    public ResponseEntity<Resource> statusPage() throws IOException {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .contentLength(statusPage.contentLength())
                .cacheControl(CacheControl.noCache())
                .body(statusPage);
    }
}
