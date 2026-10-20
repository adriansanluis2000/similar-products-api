package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.mcp.SimilarProductsMcpTools;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The MCP server, off unless {@code similar-products.mcp.enabled} is true. The same property turns Spring AI's MCP
 * server on and off (see {@code application.yaml}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("similar-products.mcp.enabled")
class McpConfig {

    @Bean
    SimilarProductsMcpTools similarProductsMcpTools(GetSimilarProductsUseCase getSimilarProducts) {
        return new SimilarProductsMcpTools(getSimilarProducts);
    }
}
