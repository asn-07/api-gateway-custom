package com.googlephotos.server.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

@SpringBootApplication
public class PhotosApiGatewayApplication {

    // Define individual URI properties for each service
    @Value("${auth.service.uri:http://localhost:8081}")
    private String authServiceUri;

    @Value("${asset.service.uri:http://localhost:8082}")
    private String assetServiceUri;

    @Value("${search.service.uri:http://localhost:8087}")
    private String searchServiceUri;

    @Value("${album.timeline.service.uri:http://localhost:8083}")
    private String albumTimelineService;

    @Value("${contact.service.uri}")
    private String contactService;

    @Value("${asset.service.v2.uri:http://localhost:8091}")
    private String assetServiceV2Uri;

    public static void main(String[] args) {
        SpringApplication.run(PhotosApiGatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth Service Routes (Public - no auth required)
                .route("auth-service", r -> r.path("/api/auth/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri(authServiceUri))

                // Asset Service Routes (Protected)
                .route("asset-service", r -> r.path("/api/assets/**", "/api/admin/redis/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri(assetServiceUri))

                // Search Service Routes (Protected)
                .route("search-service", r -> r.path("/api/search/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri(searchServiceUri))

                // Album timeline service (Protected)
                .route("album-timeline-service", r -> r.path("/api/timeline/**", "/api/memories/**", "/api/albums/**")
                        .filters(f -> f)
                        .uri(albumTimelineService))

                // contact service (Protected)
                .route("contact-service", r -> r.path("/api/v1/contacts/**")
                        .filters(f -> f)
                        .uri(contactService))

                // Asset Service Routes (Protected)
                .route("asset-service-v2", r -> r.path("/api/v1/uploads/**", "/api/v1/files/**")
                        .filters(f -> f.stripPrefix(0))
                        .uri(assetServiceV2Uri))

                .build();
    }
}