package org.example.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LandingPageForm {

    @NotBlank
    @Size(max = 120)
    private String kicker;

    @NotBlank
    @Size(max = 220)
    private String headline;

    @NotBlank
    @Size(max = 1200)
    private String description;

    @NotBlank
    @Size(max = 80)
    private String modelLabel;

    @NotBlank
    @Size(max = 80)
    private String dataSliceLabel;

    @NotBlank
    @Size(max = 80)
    private String sourceLabel;

    @NotBlank
    @Size(max = 120)
    private String sellerTitle;

    @NotBlank
    @Size(max = 600)
    private String sellerDescription;

    @NotBlank
    @Size(max = 120)
    private String adminTitle;

    @NotBlank
    @Size(max = 600)
    private String adminDescription;

    @NotBlank
    @Size(max = 120)
    private String researchTitle;

    @NotBlank
    @Size(max = 600)
    private String researchDescription;

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
