package io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.mcp;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.domain.model.Product;
import io.github.adriansanluis2000.similarproducts.domain.model.ProductId;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * The similar products use case as a tool for AI agents, over the Model Context Protocol. A second inbound adapter next
 * to REST, see ADR 0013. Failures reach the agent as tool errors with the message of the exception.
 */
public class SimilarProductsMcpTools {

    private final GetSimilarProductsUseCase getSimilarProducts;

    public SimilarProductsMcpTools(GetSimilarProductsUseCase getSimilarProducts) {
        this.getSimilarProducts = getSimilarProducts;
    }

    @McpTool(
            name = "get_similar_products",
            title = "Get similar products",
            description = """
                    Returns the products similar to a given product, most similar first, with their name, price \
                    and availability. Use it to suggest alternatives to a product, for example when it is not \
                    available. Similar products whose details cannot be retrieved in time are left out, so the \
                    list may be shorter than usual; asking again later may return them. Fails if the product \
                    does not exist.""",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, idempotentHint = true))
    public SimilarProducts getSimilarProducts(
            @McpToolParam(description = "Id of the product: 1 to 64 letters, digits, hyphens or underscores, such as 1")
                    String productId) {
        var similarProducts = getSimilarProducts.getSimilarProducts(new ProductId(productId)).stream()
                .map(SimilarProduct::of)
                .toList();
        return new SimilarProducts(productId, similarProducts);
    }

    /** The answer of the tool: the requested product and its similar products, most similar first. */
    public record SimilarProducts(String productId, List<SimilarProduct> similarProducts) {}

    public record SimilarProduct(String id, String name, BigDecimal price, boolean available) {

        static SimilarProduct of(Product product) {
            return new SimilarProduct(product.id().value(), product.name(), product.price(), product.available());
        }
    }
}
