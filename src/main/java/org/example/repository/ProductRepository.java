package org.example.repository;

import org.example.domain.Product;
import org.example.domain.Seller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByMarketplaceArticle(String marketplaceArticle);

    List<Product> findBySellerOrderByIdDesc(Seller seller);

    Page<Product> findBySellerOrderByIdDesc(Seller seller, Pageable pageable);

    @Query("""
            select count(product)
            from Product product
            where product.seller = :seller
              and not exists (
                  select forecast.id
                  from PromotionForecast forecast
                  where forecast.product = product
              )
            """)
    long countWithoutForecastBySeller(@Param("seller") Seller seller);

    long countBySellerAndCurrentStockGreaterThan(Seller seller, Integer currentStock);

    long countBySeller(Seller seller);
}
