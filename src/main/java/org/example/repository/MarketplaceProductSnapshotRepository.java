package org.example.repository;

import org.example.domain.MarketplaceProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketplaceProductSnapshotRepository extends JpaRepository<MarketplaceProductSnapshot, Long> {

    List<MarketplaceProductSnapshot> findByProductIdOrderByCollectedAtAsc(Long productId);
}
