package org.example.parser.wb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Data collection module tests")
class DiplomaDataCollectionReliabilityTest {

    @Test
    @DisplayName("Data collector receives JSON catalog and extracts rewarded products")
    void dataCollectionLayerFetchesCatalogJsonAndExtractsRewardedProducts() throws Exception {
        String catalogJson = """
                {
                  "data": {
                    "total": 2,
                    "products": [
                      {
                        "id": 1001001,
                        "name": "Report product one",
                        "feedbackPoints": "250",
                        "totalQuantity": "11",
                        "supplier": "Report Store",
                        "supplierId": 501,
                        "sizes": [{"price": {"product": 100000}}]
                      },
                      {
                        "id": 1001002,
                        "name": "Report product two",
                        "feedbackPoints": "180",
                        "totalQuantity": "7",
                        "supplier": "Report Store",
                        "supplierId": 501,
                        "sizes": [{"price": {"product": 90000}}]
                      }
                    ]
                  }
                }
                """;

        try (TestJsonServer server = startJsonServer(catalogJson)) {
            URI catalogUri = URI.create("http://127.0.0.1:" + server.port() + "/catalog.json");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(catalogUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("[REPORT] GET demo JSON catalog: " + catalogUri
                    + " -> HTTP " + response.statusCode()
                    + ", chars=" + response.body().length());

            WildberriesResponseParser parser = new WildberriesResponseParser(new ObjectMapper());
            WildberriesPage page = parser.parseCatalogPage(response.body());

            System.out.println("[REPORT] Products with feedback reward extracted from JSON:");
            for (WildberriesParsedProduct product : page.products()) {
                System.out.println("[REPORT]   WB " + product.article()
                        + " | " + product.name()
                        + " | price=" + product.price()
                        + " | reward=" + product.feedbackReward()
                        + " | benefit=" + product.benefitPercent()
                        + "% | stock=" + product.stockQuantity());
            }

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(page.total()).isEqualTo(2);
            assertThat(page.products()).hasSize(2);
            assertThat(page.products().get(0).feedbackReward()).isEqualByComparingTo(new BigDecimal("250"));
            assertThat(page.products().get(1).feedbackReward()).isEqualByComparingTo(new BigDecimal("180"));
        }
    }

    @Test
    @DisplayName("Failed proxy is temporarily excluded from the available pool")
    void proxyPoolTemporarilyExcludesFailedProxyAndReturnsItAfterSuccess() {
        WildberriesProxy firstProxy = new WildberriesProxy("net-1.example.test", 8444, "user", "password", true);
        WildberriesProxy secondProxy = new WildberriesProxy("net-2.example.test", 8444, "user", "password", true);
        WildberriesProxyPool proxyPool = new WildberriesProxyPool(
                List.of(firstProxy, secondProxy),
                Duration.ofMinutes(10)
        );

        assertThat(proxyPool.size()).isEqualTo(2);
        assertThat(proxyPool.availableCount()).isEqualTo(2);
        System.out.println("[REPORT] Proxy pool before failure: available="
                + proxyPool.availableCount() + ", coolingDown=" + proxyPool.coolingDownCount());

        proxyPool.recordFailure(firstProxy);
        System.out.println("[REPORT] Failed proxy registered: " + firstProxy.host() + ":" + firstProxy.port());
        System.out.println("[REPORT] Proxy pool after failure: available="
                + proxyPool.availableCount() + ", coolingDown=" + proxyPool.coolingDownCount());

        assertThat(proxyPool.availableCount()).isEqualTo(1);
        assertThat(proxyPool.coolingDownCount()).isEqualTo(1);

        proxyPool.recordSuccess(firstProxy);
        System.out.println("[REPORT] Proxy returned to pool after successful request: "
                + firstProxy.host() + ":" + firstProxy.port());
        System.out.println("[REPORT] Proxy pool after restore: available="
                + proxyPool.availableCount() + ", coolingDown=" + proxyPool.coolingDownCount());

        assertThat(proxyPool.availableCount()).isEqualTo(2);
        assertThat(proxyPool.coolingDownCount()).isZero();
    }

    private static TestJsonServer startJsonServer(String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "diploma-json-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/catalog.json", exchange -> {
            byte[] bytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new TestJsonServer(server, executor);
    }

    private record TestJsonServer(HttpServer server, ExecutorService executor) implements AutoCloseable {
        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
