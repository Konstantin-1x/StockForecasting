package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "marketplace_monitoring_jobs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_marketplace_monitoring_jobs_product", columnNames = "tracked_product_id")
        },
        indexes = {
                @Index(name = "idx_marketplace_monitoring_jobs_due", columnList = "finished,next_run_at")
        }
)
public class MarketplaceMonitoringJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "monitoring_job_id")
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tracked_product_id", nullable = false)
    private TrackedMarketplaceProduct product;

    @Column(name = "stage", nullable = false)
    private int stage;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "finished", nullable = false)
    private boolean finished;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public TrackedMarketplaceProduct getProduct() {
        return product;
    }

    public void setProduct(TrackedMarketplaceProduct product) {
        this.product = product;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
