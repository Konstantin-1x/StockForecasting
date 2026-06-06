package org.example.security;

import org.example.domain.AppUser;
import org.example.domain.AppUserRole;
import org.example.domain.Seller;
import org.example.repository.AppUserRepository;
import org.example.repository.SellerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

    private final AppUserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository userRepository,
                          SellerRepository sellerRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser registerSellerUser(RegistrationForm form) {
        String username = normalizeRequired(form.getUsername());
        String email = normalizeRequired(form.getEmail());
        String shopName = normalizeRequired(form.getShopName());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Пользователь с такой почтой уже существует.");
        }
        if (sellerRepository.findByShopName(shopName).isPresent()) {
            throw new IllegalArgumentException("Продавец с таким названием магазина уже существует.");
        }

        Seller seller = new Seller();
        seller.setShopName(shopName);
        seller.setContactName(normalize(form.getContactName()));
        seller.setContactEmail(normalize(form.getContactEmail()));
        seller.setContactPhone(normalize(form.getContactPhone()));
        seller.setTaxId(normalize(form.getTaxId()));
        seller.setMarketplaceSellerId(normalize(form.getMarketplaceSellerId()));
        seller.setNotes(normalize(form.getNotes()));
        Seller savedSeller = sellerRepository.save(seller);

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setDisplayName(normalizeRequired(form.getDisplayName()));
        user.setEmail(email);
        user.setPhone(normalize(form.getPhone()));
        user.setRole(AppUserRole.USER);
        user.setEnabled(true);
        user.setSeller(savedSeller);
        return userRepository.save(user);
    }

    private static String normalizeRequired(String value) {
        return normalize(value) == null ? "" : normalize(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
