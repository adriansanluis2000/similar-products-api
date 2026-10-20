package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import io.github.adriansanluis2000.similarproducts.application.port.in.GetSimilarProductsUseCase;
import io.github.adriansanluis2000.similarproducts.application.port.out.ProductCatalog;
import io.github.adriansanluis2000.similarproducts.application.service.SimilarProductsService;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.cache.CachingProductCatalog;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http.ExistingProductsClient;
import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http.HttpProductCatalog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.service.registry.ImportHttpServices;

/** Base URL and timeouts of the client come from {@code spring.http.serviceclient.products.*}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SimilarProductsProperties.class)
@ImportHttpServices(group = "products", types = ExistingProductsClient.class)
class ApplicationConfig {

    /** The existing APIs over HTTP, protected by a circuit breaker and a concurrency limit, behind a cache. */
    @Bean
    ProductCatalog productCatalog(
            ExistingProductsClient client, AsyncTaskExecutor executor, SimilarProductsProperties properties) {
        return new CachingProductCatalog(
                new HttpProductCatalog(client, executor, properties.upstream()), properties.cache());
    }

    @Bean
    GetSimilarProductsUseCase getSimilarProductsUseCase(ProductCatalog catalog, SimilarProductsProperties properties) {
        return new SimilarProductsService(catalog, properties.budget());
    }
}
