package org.example.repository;

import org.example.domain.PromotionForecast;
import org.example.domain.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionForecastRepository extends JpaRepository<PromotionForecast, Long> {

    List<PromotionForecast> findByProduct_SellerOrderByCalculatedAtDesc(Seller seller);

    long countByProduct_Seller(Seller seller);
}
