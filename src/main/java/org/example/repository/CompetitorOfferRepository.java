package org.example.repository;

import org.example.domain.CompetitorOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface CompetitorOfferRepository extends JpaRepository<CompetitorOffer, Long> {

    Optional<CompetitorOffer> findByMarketplaceArticle(String marketplaceArticle);

    Page<CompetitorOffer> findByCategoryIdIn(Collection<Long> categoryIds, Pageable pageable);

    @Query("""
            select offer
            from CompetitorOffer offer
            where lower(offer.category.externalUrl) like concat(:categoryUrlPrefix, '%')
            """)
    Page<CompetitorOffer> findByCategoryUrlPrefix(@Param("categoryUrlPrefix") String categoryUrlPrefix,
                                                  Pageable pageable);
}
