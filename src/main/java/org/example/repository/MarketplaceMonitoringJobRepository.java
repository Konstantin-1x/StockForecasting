package org.example.repository;

import org.example.domain.MarketplaceMonitoringJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarketplaceMonitoringJobRepository extends JpaRepository<MarketplaceMonitoringJob, Long> {

    @Query("""
            select job
            from MarketplaceMonitoringJob job
            join fetch job.product product
            where job.finished = false and job.nextRunAt <= :now
            order by job.nextRunAt asc
            """)
    List<MarketplaceMonitoringJob> findDueJobs(@Param("now") Instant now, Pageable pageable);

    @Query("""
            select job
            from MarketplaceMonitoringJob job
            join fetch job.product product
            where job.id = :id
            """)
    Optional<MarketplaceMonitoringJob> findWithProductById(@Param("id") Long id);

    long countByFinishedFalse();
}
