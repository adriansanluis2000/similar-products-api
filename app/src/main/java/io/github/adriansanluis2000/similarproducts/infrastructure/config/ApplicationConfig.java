package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.service.SimilarProductsService;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http.ExistingProductsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** Base URL and timeouts of the client come from {@code spring.http.serviceclient.products.*}. */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "products", types = ExistingProductsClient.class)
class ApplicationConfig {

    @Bean
    GetSimilarProductsUseCase getSimilarProductsUseCase(ProductCatalog catalog) {
        return new SimilarProductsService(catalog);
    }
}
