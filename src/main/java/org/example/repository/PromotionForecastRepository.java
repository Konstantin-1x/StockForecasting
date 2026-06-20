package org.example.repository;

import org.example.domain.PromotionForecast;
import org.example.domain.Seller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionForecastRepository extends JpaRepository<PromotionForecast, Long> {

    List<PromotionForecast> findByProduct_SellerOrderByCalculatedAtDesc(Seller seller);

    Page<PromotionForecast> findByProduct_SellerOrderByCalculatedAtDesc(Seller seller, Pageable pageable);

    Optional<PromotionForecast> findFirstByProduct_SellerOrderByCalculatedAtDesc(Seller seller);

    long countByProduct_Seller(Seller seller);
}
