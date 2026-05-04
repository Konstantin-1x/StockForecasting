package org.example.repository;

import org.example.domain.TrackedMarketplaceProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackedMarketplaceProductRepository extends JpaRepository<TrackedMarketplaceProduct, Long> {

    boolean existsByMarketplaceArticle(String marketplaceArticle);

    Optional<TrackedMarketplaceProduct> findByMarketplaceArticle(String marketplaceArticle);

    long countByActiveTrue();
}
