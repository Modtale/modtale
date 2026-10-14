package net.modtale.service.security.scan;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.modtale.config.properties.AppWardenProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WardenInspectionWindowClientTest {
    @Test void sendsAuthenticatedWindowParametersAndDecodesTheBoundedResponse() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var body=new AtomicReference<String>();var key=new AtomicReference<String>();
        var response=new WardenClientService.InspectionWindow("a".repeat(64),"nested.jar!/Entry.class","b".repeat(64),"policy",
                "c".repeat(64),"JVM_BYTECODE",0,4,4,1,true,true,"text",List.of());
        byte[] json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(response);
        server.createContext("/api/v1/inspect-window",exchange->{
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-Warden-Api-Key"));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,json.length);
            try(var output=exchange.getResponseBody()){output.write(json);}
        });server.start();
        try {
            var client=new WardenClientService(new AppWardenProperties("http://127.0.0.1:"+server.getAddress().getPort(),"test-only",true,1,90));
            assertEquals(response,client.inspectWindow("artifact".getBytes(),"nested.jar!/Entry.class",0,32000,42));
            assertEquals("test-only",key.get());
            for(String field:List.of("file","path","offset","characters","sourceLine"))assertTrue(body.get().contains("name=\""+field+"\""));
            for(String value:List.of("artifact","nested.jar!/Entry.class","32000","42"))assertTrue(body.get().contains(value));
        } finally {server.stop(0);}
    }
}
