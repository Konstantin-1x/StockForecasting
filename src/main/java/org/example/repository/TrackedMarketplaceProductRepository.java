package org.example.repository;

import org.example.domain.ProductCategory;
import org.example.domain.TrackedMarketplaceProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TrackedMarketplaceProductRepository extends JpaRepository<TrackedMarketplaceProduct, Long> {

    boolean existsByMarketplaceArticle(String marketplaceArticle);

    Optional<TrackedMarketplaceProduct> findByMarketplaceArticle(String marketplaceArticle);

    long countByActiveTrue();

    List<TrackedMarketplaceProduct> findAllByActiveTrue();

    List<TrackedMarketplaceProduct> findByCategoryOrderByDiscoveredAtDesc(ProductCategory category, Pageable pageable);

    @Query("""
            select product
            from TrackedMarketplaceProduct product
            where product.category.parentCategory = :parentCategory
            order by product.discoveredAt desc
            """)
    List<TrackedMarketplaceProduct> findByParentCategory(@Param("parentCategory") ProductCategory parentCategory,
                                                         Pageable pageable);

    List<TrackedMarketplaceProduct> findByDiscoveredPriceBetweenOrderByDiscoveredAtDesc(BigDecimal minPrice,
                                                                                        BigDecimal maxPrice,
                                                                                        Pageable pageable);

    List<TrackedMarketplaceProduct> findAllByOrderByDiscoveredAtDesc(Pageable pageable);
}
