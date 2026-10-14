package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import net.modtale.config.properties.AppWardenProperties;
import net.modtale.model.project.ScanResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WardenScannerEvidenceClientTest {
    @Test void scannerOnlyResponseUsesDedicatedRouteAndVerifiesOriginalBytes() throws Exception {
        byte[] original="reviewed original".getBytes();
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        var served=ScanEvidenceFixtures.complete(false);var evidence=served.getSecurityEvidence();
        served.setSecurityEvidence(new ScanResult.SecurityEvidence(evidence.policyVersion(),digest,evidence.contentSha256(),
                true,false,"SCANNER_EVIDENCE_ONLY",evidence.entryHashes()));
        var mapper=new ObjectMapper();var calls=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/scan-evidence",exchange->{
            try {
                assertEquals("POST",exchange.getRequestMethod());
                assertEquals("test-key",exchange.getRequestHeaders().getFirst("X-Warden-Api-Key"));
                exchange.getRequestBody().readAllBytes();calls.incrementAndGet();
                byte[] body=mapper.writeValueAsBytes(served);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
            } finally {exchange.close();}
        });
        server.start();
        try {
            var client=new WardenClientService(new AppWardenProperties("http://127.0.0.1:"+server.getAddress().getPort(),
                    "test-key",true,1,30),true);
            var matched=client.scanEvidenceFile(original,"mod.zip");
            assertTrue(matched.isArtifactVerified());
            assertFalse(matched.getSecurityEvidence().clearanceGranted());
            assertEquals("SCANNER_EVIDENCE_ONLY",matched.getSecurityEvidence().reviewState());
            assertFalse(client.scanEvidenceFile("changed bytes".getBytes(),"mod.zip").isArtifactVerified());
            assertEquals(2,calls.get());
        } finally {server.stop(0);}
    }
}
