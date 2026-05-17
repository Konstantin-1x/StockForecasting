package org.example.repository;

import org.example.domain.MarketplaceProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MarketplaceProductSnapshotRepository extends JpaRepository<MarketplaceProductSnapshot, Long> {

    List<MarketplaceProductSnapshot> findByProductIdOrderByCollectedAtAsc(Long productId);

    @Query("""
            select snapshot
            from MarketplaceProductSnapshot snapshot
            where snapshot.product.id in :productIds
            order by snapshot.product.id asc, snapshot.collectedAt asc
            """)
    List<MarketplaceProductSnapshot> findByProductIdsOrderByProductAndCollectedAt(
            @Param("productIds") Collection<Long> productIds
    );
}
