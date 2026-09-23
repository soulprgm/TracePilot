package com.tracepilot.backend.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoScenarioServiceTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void triggersKnownScenarioAndReportsExpectedStatus() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/order/success", exchange -> {
            byte[] body = "{\"status\":\"completed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        DemoScenarioService service = new DemoScenarioService(
                "http://localhost:" + server.getAddress().getPort(),
                HttpClient.newHttpClient()
        );

        var response = service.trigger("SUCCESS");

        assertThat(response.scenario()).isEqualTo("success");
        assertThat(response.upstreamStatus()).isEqualTo(200);
        assertThat(response.expectedOutcome()).isTrue();
    }

    @Test
    void rejectsUnknownScenario() {
        DemoScenarioService service = new DemoScenarioService(
                "http://localhost:8081",
                HttpClient.newHttpClient()
        );

        assertThatThrownBy(() -> service.trigger("other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("success, slow, or fail");
    }
}
