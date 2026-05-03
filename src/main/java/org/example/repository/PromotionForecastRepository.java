package org.example.repository;

import org.example.domain.PromotionForecast;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionForecastRepository extends JpaRepository<PromotionForecast, Long> {
}
