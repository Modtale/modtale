package net.modtale.config.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.catalina.startup.Tomcat;
import org.apache.el.ExpressionFactoryImpl;
import org.apache.tomcat.websocket.server.WsServerContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

class EmbeddedTomcatCompatibilityTest {

  @TempDir Path baseDirectory;

  @Test
  void embeddedTomcatModulesUseAnAlignedPatchedVersion() {
    String version = Tomcat.class.getPackage().getImplementationVersion();
    assertNotNull(version);
    assertTrue(
        Runtime.Version.parse(version).compareTo(Runtime.Version.parse("11.0.26")) >= 0,
        "Embedded Tomcat must include the fixes released in 11.0.26");
    assertEquals(version, ExpressionFactoryImpl.class.getPackage().getImplementationVersion());
    assertEquals(version, WsServerContainer.class.getPackage().getImplementationVersion());
  }

  @Test
  void springBootCanStartAndServeRepeatedRequestsWithPatchedTomcat() throws Exception {
    var factory = new TomcatServletWebServerFactory(0);
    factory.setAddress(InetAddress.getByName("127.0.0.1"));
    factory.setBaseDirectory(baseDirectory.toFile());
    var server =
        factory.getWebServer(
            context -> {
              var servlet =
                  context.addServlet(
                      "tomcat-smoke",
                      new HttpServlet() {
                        @Override
                        protected void doGet(
                            HttpServletRequest request, HttpServletResponse response)
                            throws IOException {
                          response.setContentType("text/plain");
                          response.getWriter().write("tomcat-smoke-ok");
                        }
                      });
              servlet.addMapping("/tomcat-smoke");
            });
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      var request =
          HttpRequest.newBuilder(
                  URI.create("http://127.0.0.1:" + server.getPort() + "/tomcat-smoke"))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();
      for (int attempt = 0; attempt < 3; attempt++) {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("tomcat-smoke-ok", response.body());
      }
    } finally {
      server.stop();
      server.destroy();
    }
  }
}
