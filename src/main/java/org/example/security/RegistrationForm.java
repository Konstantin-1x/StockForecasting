package org.example.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank
    @Size(max = 80)
    private String username;

    @NotBlank
    @Size(min = 6, max = 80)
    private String password;

    @NotBlank
    @Size(max = 150)
    private String displayName;

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @Size(max = 60)
    private String phone;

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

    @Size(max = 2000)
    private String notes;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
