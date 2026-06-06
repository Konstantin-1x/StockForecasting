package org.example.web;

import jakarta.validation.Valid;
import org.example.domain.AppUser;
import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.domain.PromotionForecast;
import org.example.domain.Seller;
import org.example.forecast.NeuralForecastJobService;
import org.example.forecast.NeuralForecastRunResult;
import org.example.forecast.NeuralPromotionForecastService;
import org.example.repository.AppUserRepository;
import org.example.repository.ProductRepository;
import org.example.repository.PromotionForecastRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class UserPortalController {

    private final AppUserRepository userRepository;
    private final ProductRepository productRepository;
    private final PromotionForecastRepository forecastRepository;
    private final CategoryCatalogService categoryCatalogService;
    private final UserPortalService userPortalService;
    private final NeuralForecastJobService neuralForecastJobService;
    private final NeuralPromotionForecastService neuralPromotionForecastService;

    public UserPortalController(AppUserRepository userRepository,
                                ProductRepository productRepository,
                                PromotionForecastRepository forecastRepository,
                                CategoryCatalogService categoryCatalogService,
                                UserPortalService userPortalService,
                                NeuralForecastJobService neuralForecastJobService,
                                NeuralPromotionForecastService neuralPromotionForecastService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.forecastRepository = forecastRepository;
        this.categoryCatalogService = categoryCatalogService;
        this.userPortalService = userPortalService;
        this.neuralForecastJobService = neuralForecastJobService;
        this.neuralPromotionForecastService = neuralPromotionForecastService;
    }

    @GetMapping("/app")
    public String userHome(Authentication authentication, Model model) {
        AppUser user = currentUser(authentication);
        Seller seller = user == null ? null : user.getSeller();

        model.addAttribute("portalUser", user);
        model.addAttribute("seller", seller);
        model.addAttribute("products", seller == null ? List.of() : productRepository.findBySellerOrderByIdDesc(seller));
        model.addAttribute("forecasts", seller == null ? List.<PromotionForecast>of()
                : forecastRepository.findByProduct_SellerOrderByCalculatedAtDesc(seller));
        model.addAttribute("neuralForecastJobStatus", neuralForecastJobService.currentStatus());
        return "user-home";
    }

    @GetMapping("/app/products/new")
    public String newProduct(@RequestParam(required = false) String categoryKey, Model model) {
        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new UserProductForm());
        }
        UserProductForm form = (UserProductForm) model.asMap().get("productForm");
        String selectedCategoryKey = resolveSelectedCategoryKey(categoryKey, form);
        categoryCatalogService.selectedProductCategory(selectedCategoryKey)
                .map(ProductCategory::getId)
                .ifPresent(form::setCategoryId);
        addProductFormModel(model, selectedCategoryKey);
        return "user-product-form";
    }

    @GetMapping("/app/products")
    public String products(Authentication authentication, Model model) {
        AppUser user = currentUser(authentication);
        Seller seller = user == null ? null : user.getSeller();

        model.addAttribute("seller", seller);
        model.addAttribute("products", seller == null ? List.of() : productRepository.findBySellerOrderByIdDesc(seller));
        return "user-products";
    }

    @PostMapping("/app/products")
    public String addProduct(Authentication authentication,
                             @Valid @ModelAttribute("productForm") UserProductForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addProductFormModel(model, resolveSelectedCategoryKey(null, form));
            return "user-product-form";
        }

        try {
            userPortalService.addProduct(currentUser(authentication), form);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("product.failed", e.getMessage());
            addProductFormModel(model, resolveSelectedCategoryKey(null, form));
            return "user-product-form";
        }

        redirectAttributes.addFlashAttribute("status", "Товар добавлен в кабинет продавца.");
        return "redirect:/app";
    }

    @GetMapping("/app/forecasts")
    public String forecasts(Authentication authentication,
                            @RequestParam(required = false) Long productId,
                            Model model) {
        AppUser user = currentUser(authentication);
        Seller seller = user == null ? null : user.getSeller();

        addForecastPageModel(seller, model, productId);
        model.addAttribute("neuralForecastJobStatus", neuralForecastJobService.currentStatus());
        return "user-forecasts";
    }

    @GetMapping("/app/profile/edit")
    public String editProfile(Authentication authentication, Model model) {
        AppUser user = currentUser(authentication);
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", userPortalService.toProfileForm(user));
        }
        return "user-profile-edit";
    }

    @PostMapping("/app/profile")
    public String updateProfile(Authentication authentication,
                                @Valid @ModelAttribute("profileForm") UserProfileForm form,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "user-profile-edit";
        }

        try {
            userPortalService.updateProfile(currentUser(authentication), form);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("profile.failed", e.getMessage());
            return "user-profile-edit";
        }

        redirectAttributes.addFlashAttribute("status", "Данные кабинета обновлены.");
        return "redirect:/app";
    }

    @PostMapping("/app/forecasts/recalculate")
    public String recalculateSellerForecasts(Authentication authentication,
                                             @Valid @ModelAttribute("forecastRequestForm") SellerForecastRequestForm form,
                                             BindingResult bindingResult,
                                             Model model,
                                             RedirectAttributes redirectAttributes) {
        AppUser user = currentUser(authentication);
        Seller seller = user == null ? null : user.getSeller();
        if (seller == null) {
            redirectAttributes.addFlashAttribute("error", "К аккаунту не привязан продавец.");
            return "redirect:/app/forecasts";
        }

        if (bindingResult.hasErrors()) {
            addForecastPageModel(seller, model, form.getProductId());
            return "user-forecasts";
        }

        NeuralForecastRunResult result;
        try {
            result = neuralPromotionForecastService.recalculateForecastForSellerProduct(
                    form.getProductId(),
                    form.getPromotionBudget(),
                    seller
            );
        } catch (IllegalArgumentException e) {
            bindingResult.reject("forecast.failed", e.getMessage());
            addForecastPageModel(seller, model, form.getProductId());
            return "user-forecasts";
        }
        if (result.forecastsSaved() > 0) {
            redirectAttributes.addFlashAttribute("status", result.statusMessage());
        } else {
            redirectAttributes.addFlashAttribute("error", result.statusMessage());
        }
        return "redirect:/app/forecasts";
    }

    private void addProductFormModel(Model model, String selectedCategoryKey) {
        Optional<ProductCategory> selectedCategory = categoryCatalogService.selectedProductCategory(selectedCategoryKey);
        model.addAttribute("catalog", categoryCatalogService.buildCatalog(selectedCategoryKey));
        model.addAttribute("selectedCategoryKey", selectedCategoryKey);
        model.addAttribute("selectedProductCategory", selectedCategory.orElse(null));
    }

    private void addForecastPageModel(Seller seller, Model model, Long selectedProductId) {
        List<Product> products = seller == null ? List.of() : productRepository.findBySellerOrderByIdDesc(seller);
        model.addAttribute("seller", seller);
        model.addAttribute("products", products);
        model.addAttribute("forecasts", seller == null ? List.<PromotionForecast>of()
                : forecastRepository.findByProduct_SellerOrderByCalculatedAtDesc(seller));
        if (!model.containsAttribute("forecastRequestForm")) {
            SellerForecastRequestForm form = new SellerForecastRequestForm();
            products.stream()
                    .filter(product -> selectedProductId != null && selectedProductId.equals(product.getId()))
                    .findFirst()
                    .or(() -> products.stream().findFirst())
                    .map(Product::getId)
                    .ifPresent(form::setProductId);
            model.addAttribute("forecastRequestForm", form);
        }
    }

    private String resolveSelectedCategoryKey(String categoryKey, UserProductForm form) {
        String selectedCategoryKey = categoryCatalogService.selectedCategoryUrlPrefix(categoryKey);
        if (selectedCategoryKey != null) {
            return selectedCategoryKey;
        }
        return categoryCatalogService.categoryKeyForId(form.getCategoryId()).orElse(null);
    }

    private AppUser currentUser(Authentication authentication) {
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден."));
    }
}
