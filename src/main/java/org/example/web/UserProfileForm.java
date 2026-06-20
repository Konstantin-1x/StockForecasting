package org.example.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserProfileForm {

    @NotBlank
    @Size(max = 150)
    private String displayName;

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @NotBlank
    @Size(max = 150)
    private String shopName;

    @NotBlank
    @Size(max = 150)
    private String contactName;

    @NotBlank
    @Email
    @Size(max = 180)
    private String contactEmail;

    @Size(max = 60)
    private String contactPhone;

    @Size(max = 40)
    private String taxId;

    @Size(max = 100)
    private String marketplaceSellerId;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getMarketplaceSellerId() {
        return marketplaceSellerId;
    }

    public void setMarketplaceSellerId(String marketplaceSellerId) {
        this.marketplaceSellerId = marketplaceSellerId;
    }

}
