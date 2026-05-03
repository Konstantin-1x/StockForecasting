package org.example.repository;

import org.example.domain.CompetitorOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompetitorOfferRepository extends JpaRepository<CompetitorOffer, Long> {

    Optional<CompetitorOffer> findByMarketplaceArticle(String marketplaceArticle);
}
