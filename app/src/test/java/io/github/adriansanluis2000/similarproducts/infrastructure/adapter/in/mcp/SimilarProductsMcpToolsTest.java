package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductNotFoundException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/** An MCP client, as an AI agent would use it, against the server over streamable HTTP. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "similar-products.mcp.enabled=true")
class SimilarProductsMcpToolsTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private GetSimilarProductsUseCase getSimilarProducts;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                        .build())
                .build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    @Test
    void offersTheToolWithItsSchemas() {
        assertThat(client.listTools().tools()).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("get_similar_products");
            assertThat(tool.description()).contains("most similar first");
            assertThat(tool.inputSchema()).containsEntry("required", List.of("productId"));
            assertThat(tool.outputSchema()).containsKey("properties");
        });
    }

    @Test
    void returnsTheSimilarProductsAsStructuredContent() throws Exception {
        given(getSimilarProducts.getSimilarProducts(new ProductId("1")))
                .willReturn(List.of(
                        new Product(new ProductId("3"), "Blazer", new BigDecimal("29.99"), false),
                        new Product(new ProductId("2"), "Dress", new BigDecimal("19.99"), true)));

        var result = call("1");

        assertThat(result.isError()).isFalse();
        JSONAssert.assertEquals(
                """
                {"productId": "1", "similarProducts": [
                  {"id": "3", "name": "Blazer", "price": 29.99, "available": false},
                  {"id": "2", "name": "Dress", "price": 19.99, "available": true}
                ]}
                """,
                JsonMapper.builder().build().writeValueAsString(result.structuredContent()),
                JSONCompareMode.STRICT);
    }

    @Test
    void reportsAProductThatDoesNotExistAsAToolError() {
        given(getSimilarProducts.getSimilarProducts(new ProductId("99")))
                .willThrow(new ProductNotFoundException(new ProductId("99")));

        var result = call("99");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("Product 99 not found");
    }

    @Test
    void reportsAMalformedProductIdAsAToolError() {
        var result = call("a.b");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("1 to 64 letters, digits, hyphens or underscores");
    }

    private CallToolResult call(String productId) {
        return client.callTool(new CallToolRequest("get_similar_products", Map.of("productId", productId)));
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }
}
