package org.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "landing_page_content")
public class LandingPageContent {

    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "content_id")
    private Long id = SINGLETON_ID;

    @Column(name = "kicker", nullable = false, length = 120)
    private String kicker;

    @Column(name = "headline", nullable = false, length = 220)
    private String headline;

    @Column(name = "description", nullable = false, length = 1200)
    private String description;

    @Column(name = "model_label", nullable = false, length = 80)
    private String modelLabel;

    @Column(name = "data_slice_label", nullable = false, length = 80)
    private String dataSliceLabel;

    @Column(name = "source_label", nullable = false, length = 80)
    private String sourceLabel;

    @Column(name = "seller_title", nullable = false, length = 120)
    private String sellerTitle;

    @Column(name = "seller_description", nullable = false, length = 600)
    private String sellerDescription;

    @Column(name = "admin_title", nullable = false, length = 120)
    private String adminTitle;

    @Column(name = "admin_description", nullable = false, length = 600)
    private String adminDescription;

    @Column(name = "research_title", nullable = false, length = 120)
    private String researchTitle;

    @Column(name = "research_description", nullable = false, length = 600)
    private String researchDescription;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKicker() {
        return kicker;
    }

    public void setKicker(String kicker) {
        this.kicker = kicker;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModelLabel() {
        return modelLabel;
    }

    public void setModelLabel(String modelLabel) {
        this.modelLabel = modelLabel;
    }

    public String getDataSliceLabel() {
        return dataSliceLabel;
    }

    public void setDataSliceLabel(String dataSliceLabel) {
        this.dataSliceLabel = dataSliceLabel;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public String getSellerTitle() {
        return sellerTitle;
    }

    public void setSellerTitle(String sellerTitle) {
        this.sellerTitle = sellerTitle;
    }

    public String getSellerDescription() {
        return sellerDescription;
    }

    public void setSellerDescription(String sellerDescription) {
        this.sellerDescription = sellerDescription;
    }

    public String getAdminTitle() {
        return adminTitle;
    }

    public void setAdminTitle(String adminTitle) {
        this.adminTitle = adminTitle;
    }

    public String getAdminDescription() {
        return adminDescription;
    }

    public void setAdminDescription(String adminDescription) {
        this.adminDescription = adminDescription;
    }

    public String getResearchTitle() {
        return researchTitle;
    }

    public void setResearchTitle(String researchTitle) {
        this.researchTitle = researchTitle;
    }

    public String getResearchDescription() {
        return researchDescription;
    }

    public void setResearchDescription(String researchDescription) {
        this.researchDescription = researchDescription;
    }
}
