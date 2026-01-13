package net.modtale.service.security;

import net.modtale.model.resources.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;

@Service
public class WardenClientService {

    private static final Logger logger = LoggerFactory.getLogger(WardenClientService.class);
    private final WebClient webClient;

    public WardenClientService(@Value("${app.warden.url}") String wardenUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(wardenUrl)
                .build();
    }

    public ScanResult scanFile(byte[] fileBytes, String filename) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            return webClient.post()
                    .uri("/api/v1/scan")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(ScanResult.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

        } catch (Exception e) {
            logger.error("Failed to communicate with Warden service: " + e.getMessage());
            ScanResult errorResult = new ScanResult();
            errorResult.setStatus("FAILED");
            errorResult.setRiskScore(-1);
            errorResult.setIssues(new ArrayList<>());
            return errorResult;
        }
    }
}