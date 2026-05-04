package org.example.repository;

import org.example.domain.MarketplaceProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceProductSnapshotRepository extends JpaRepository<MarketplaceProductSnapshot, Long> {
}
