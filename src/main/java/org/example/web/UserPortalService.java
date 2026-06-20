package org.example.web;

import org.example.domain.AppUser;
import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.domain.Seller;
import org.example.repository.AppUserRepository;
import org.example.repository.ProductCategoryRepository;
import org.example.repository.ProductRepository;
import org.example.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class UserPortalService {

    private static final String SYNTHETIC_FORECAST_SELLER = "WB Neural Forecast";

    private final AppUserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public UserPortalService(AppUserRepository userRepository,
                             SellerRepository sellerRepository,
                             ProductRepository productRepository,
                             ProductCategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.sellerRepository = sellerRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Product addProduct(AppUser user, UserProductForm form) {
        Seller seller = requireSeller(user);
        ProductCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Выберите категорию товара."));

        String article = normalizeRequired(form.getMarketplaceArticle());
        Product product = productRepository.findByMarketplaceArticle(article).orElseGet(Product::new);
        if (product.getId() != null
                && product.getSeller() != null
                && !product.getSeller().getId().equals(seller.getId())
                && !SYNTHETIC_FORECAST_SELLER.equals(product.getSeller().getShopName())) {
            throw new IllegalArgumentException("Товар с таким артикулом уже привязан к другому продавцу.");
        }
        if (product.getId() != null
                && product.getSeller() != null
                && product.getSeller().getId().equals(seller.getId())) {
            throw new IllegalArgumentException("Этот товар уже есть в вашем кабинете.");
        }

        product.setSeller(seller);
        product.setCategory(category);
        product.setMarketplaceArticle(article);
        product.setName(normalizeRequired(form.getName()));
        product.setDescription(normalize(form.getDescription()));
        product.setBasePrice(form.getBasePrice());
        product.setCurrentStock(form.getCurrentStock() == null ? 0 : form.getCurrentStock());
        return productRepository.save(product);
    }

    @Transactional
    public void updateProfile(AppUser user, UserProfileForm form) {
        AppUser managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден."));
        Seller seller = managedUser.getSeller();
        if (seller == null) {
            seller = new Seller();
            seller.setRegistrationDate(LocalDate.now());
            managedUser.setSeller(seller);
        }

        String email = normalizeRequired(form.getEmail());
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(managedUser.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Эта почта уже используется другим пользователем.");
                });

        String shopName = normalizeRequired(form.getShopName());
        Long sellerId = seller.getId();
        sellerRepository.findByShopName(shopName)
                .filter(existing -> !existing.getId().equals(sellerId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Продавец с таким названием магазина уже существует.");
                });

        managedUser.setDisplayName(normalizeRequired(form.getDisplayName()));
        managedUser.setEmail(email);

        seller.setShopName(shopName);
        seller.setContactName(normalizeRequired(form.getContactName()));
        seller.setContactEmail(normalizeRequired(form.getContactEmail()));
        seller.setContactPhone(normalize(form.getContactPhone()));
        seller.setTaxId(normalize(form.getTaxId()));
        seller.setMarketplaceSellerId(normalize(form.getMarketplaceSellerId()));

        Seller savedSeller = sellerRepository.save(seller);
        managedUser.setSeller(savedSeller);
        userRepository.save(managedUser);
    }

    public UserProfileForm toProfileForm(AppUser user) {
        UserProfileForm form = new UserProfileForm();
        form.setDisplayName(user.getDisplayName());
        form.setEmail(user.getEmail());

        Seller seller = user.getSeller();
        if (seller != null) {
            form.setShopName(seller.getShopName());
            form.setContactName(seller.getContactName());
            form.setContactEmail(seller.getContactEmail());
            form.setContactPhone(seller.getContactPhone());
            form.setTaxId(seller.getTaxId());
            form.setMarketplaceSellerId(seller.getMarketplaceSellerId());
        }
        return form;
    }

    private static Seller requireSeller(AppUser user) {
        if (user == null || user.getSeller() == null) {
            throw new IllegalArgumentException("К аккаунту не привязан продавец.");
        }
        return user.getSeller();
    }

    private static String normalizeRequired(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
