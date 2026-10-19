package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.out.http.ExistingProductsClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** Base URL and timeouts of the client come from {@code spring.http.serviceclient.products.*}. */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "products", types = ExistingProductsClient.class)
class ApplicationConfig {}
