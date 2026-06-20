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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

@Controller
public class UserPortalController {

    private static final int TABLE_PAGE_SIZE = 20;
    private static final int DASHBOARD_PREVIEW_SIZE = 5;
    private static final int HIGH_STOCK_THRESHOLD = 50;

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
        Page<Product> latestProductPage = seller == null
                ? Page.empty(PageRequest.of(0, DASHBOARD_PREVIEW_SIZE))
                : productRepository.findBySellerOrderByIdDesc(seller, PageRequest.of(0, DASHBOARD_PREVIEW_SIZE));
        Page<PromotionForecast> latestForecastPage = seller == null
                ? Page.empty(PageRequest.of(0, DASHBOARD_PREVIEW_SIZE))
                : forecastRepository.findByProduct_SellerOrderByCalculatedAtDesc(seller, PageRequest.of(0, DASHBOARD_PREVIEW_SIZE));
        long productCount = seller == null ? 0 : productRepository.countBySeller(seller);
        long forecastCount = seller == null ? 0 : forecastRepository.countByProduct_Seller(seller);
        long unforecastedProductCount = seller == null ? 0 : productRepository.countWithoutForecastBySeller(seller);
        long highStockProductCount = seller == null
                ? 0
                : productRepository.countBySellerAndCurrentStockGreaterThan(seller, HIGH_STOCK_THRESHOLD);
        Optional<PromotionForecast> lastForecast = seller == null
                ? Optional.empty()
                : forecastRepository.findFirstByProduct_SellerOrderByCalculatedAtDesc(seller);

        model.addAttribute("portalUser", user);
        model.addAttribute("seller", seller);
        model.addAttribute("sellerStatusLabel", sellerStatusLabel(seller));
        model.addAttribute("latestProducts", latestProductPage.getContent());
        model.addAttribute("latestForecasts", latestForecastPage.getContent());
        model.addAttribute("productCount", productCount);
        model.addAttribute("forecastCount", forecastCount);
        model.addAttribute("unforecastedProductCount", unforecastedProductCount);
        model.addAttribute("lastForecastLabel", lastForecast.map(PromotionForecast::getCalculatedAtLabel)
                .orElse("Прогнозов пока нет"));
        model.addAttribute("dashboardRecommendations", dashboardRecommendations(
                seller,
                productCount,
                forecastCount,
                unforecastedProductCount,
                highStockProductCount,
                lastForecast
        ));
        return "user-home";
    }

    @GetMapping("/app/products/new")
    public String newProduct(Authentication authentication,
                             @RequestParam(required = false) String categoryKey,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        Seller seller = currentUser(authentication).getSeller();
        if (seller == null) {
            redirectAttributes.addFlashAttribute("error", "Сначала заполните профиль продавца.");
            return "redirect:/app/profile/edit";
        }
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
    public String products(Authentication authentication,
                           @RequestParam(defaultValue = "0") Integer page,
                           Model model) {
        AppUser user = currentUser(authentication);
        Seller seller = user == null ? null : user.getSeller();
        Page<Product> productPage = seller == null
                ? Page.empty(pageRequest(page))
                : productRepository.findBySellerOrderByIdDesc(seller, pageRequest(page));

        model.addAttribute("seller", seller);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("productPage", productPage);
        model.addAttribute("productPaginationPages", pageNumbers(productPage));
        model.addAttribute("productCount", productPage.getTotalElements());
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

    @PostMapping(value = "/app/products", headers = "X-Requested-With=XMLHttpRequest")
    public ResponseEntity<Map<String, Object>> addProductAsync(Authentication authentication,
                                                              @Valid @ModelAttribute("productForm") UserProductForm form,
                                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(asyncError(validationMessage(bindingResult)));
        }

        try {
            Product product = userPortalService.addProduct(currentUser(authentication), form);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Товар '" + product.getName() + "' добавлен в кабинет продавца.",
                    "redirectUrl", "/app/products"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(asyncError(e.getMessage()));
        }
    }

    @GetMapping("/app/forecasts")
    public String forecasts(Authentication authentication,
                            @RequestParam(required = false) Long productId,
                            @RequestParam(defaultValue = "0") Integer productsPage,
                            @RequestParam(defaultValue = "0") Integer forecastsPage,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        AppUser user = currentUser(authentication);
        Seller seller = user == null ? null : user.getSeller();
        if (seller == null) {
            redirectAttributes.addFlashAttribute("error", "Сначала заполните профиль продавца.");
            return "redirect:/app/profile/edit";
        }

        addForecastPageModel(seller, model, productId, productsPage, forecastsPage);
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
            addForecastPageModel(seller, model, form.getProductId(), 0, 0);
            return "user-forecasts";
        }

        NeuralForecastRunResult result;
        try {
            result = neuralPromotionForecastService.recalculateForecastForSellerProduct(
                    form.getProductId(),
                    form.getPromotionBudget(),
                    seller
            );
        } catch (RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Расчет прогноза завершился ошибкой. Проверьте данные товара и повторите попытку."
                    : e.getMessage();
            bindingResult.reject("forecast.failed", message);
            addForecastPageModel(seller, model, form.getProductId(), 0, 0);
            model.addAttribute("forecastError", message);
            return "user-forecasts";
        }
        if (result.forecastsSaved() > 0) {
            redirectAttributes.addFlashAttribute("forecastResult", result.statusMessage());
            if (result.scenario() != null) {
                redirectAttributes.addFlashAttribute("forecastScenario", result.scenario());
            }
        } else {
            redirectAttributes.addFlashAttribute("forecastError", result.statusMessage());
        }
        return "redirect:/app/forecasts";
    }

    @PostMapping(value = "/app/forecasts/recalculate", headers = "X-Requested-With=XMLHttpRequest")
    public ResponseEntity<Map<String, Object>> recalculateSellerForecastsAsync(
            Authentication authentication,
            @Valid @ModelAttribute("forecastRequestForm") SellerForecastRequestForm form,
            BindingResult bindingResult) {
        AppUser user = currentUser(authentication);
        Seller seller = user == null ? null : user.getSeller();
        if (seller == null) {
            return ResponseEntity.badRequest().body(asyncError("К аккаунту не привязан продавец."));
        }

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(asyncError(validationMessage(bindingResult)));
        }

        try {
            NeuralForecastRunResult result = neuralPromotionForecastService.recalculateForecastForSellerProduct(
                    form.getProductId(),
                    form.getPromotionBudget(),
                    seller
            );
            boolean success = result.forecastsSaved() > 0;
            Map<String, Object> body = Map.of(
                    "success", success,
                    "message", result.statusMessage(),
                    "scenario", result.scenario() == null ? Map.of() : result.scenario(),
                    "refresh", success
            );
            return success ? ResponseEntity.ok(body) : ResponseEntity.badRequest().body(body);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Расчет прогноза завершился ошибкой. Проверьте данные товара и повторите попытку."
                    : e.getMessage();
            return ResponseEntity.badRequest().body(asyncError(message));
        }
    }

    private void addProductFormModel(Model model, String selectedCategoryKey) {
        Optional<ProductCategory> selectedCategory = categoryCatalogService.selectedProductCategory(selectedCategoryKey);
        model.addAttribute("catalog", categoryCatalogService.buildCatalog(selectedCategoryKey));
        model.addAttribute("selectedCategoryKey", selectedCategoryKey);
        model.addAttribute("selectedProductCategory", selectedCategory.orElse(null));
    }

    private void addForecastPageModel(Seller seller,
                                      Model model,
                                      Long selectedProductId,
                                      Integer productsPageNumber,
                                      Integer forecastsPageNumber) {
        Page<PromotionForecast> forecastPage = seller == null
                ? Page.empty(pageRequest(forecastsPageNumber))
                : forecastRepository.findByProduct_SellerOrderByCalculatedAtDesc(seller, pageRequest(forecastsPageNumber));
        List<Product> products = seller == null ? List.of() : productRepository.findBySellerOrderByIdDesc(seller);
        model.addAttribute("seller", seller);
        model.addAttribute("products", products);
        model.addAttribute("productCount", seller == null ? 0 : productRepository.countBySeller(seller));
        model.addAttribute("forecasts", forecastPage.getContent());
        model.addAttribute("forecastPage", forecastPage);
        model.addAttribute("forecastPaginationPages", pageNumbers(forecastPage));
        model.addAttribute("forecastCount", forecastPage.getTotalElements());
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

    private static PageRequest pageRequest(Integer page) {
        return PageRequest.of(Math.max(0, page == null ? 0 : page), TABLE_PAGE_SIZE);
    }

    private static List<Integer> pageNumbers(Page<?> page) {
        int totalPages = page.getTotalPages();
        if (totalPages <= 1) {
            return List.of();
        }

        int current = page.getNumber();
        int start = Math.max(0, current - 2);
        int end = Math.min(totalPages - 1, current + 2);
        return IntStream.rangeClosed(start, end)
                .boxed()
                .toList();
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

    private static Map<String, Object> asyncError(String message) {
        return Map.of(
                "success", false,
                "message", message == null || message.isBlank() ? "Запрос не выполнен." : message
        );
    }

    private static String validationMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .findFirst()
                .map(ObjectError::getDefaultMessage)
                .filter(message -> message != null && !message.isBlank())
                .orElse("Проверьте заполнение формы.");
    }

    private static String sellerStatusLabel(Seller seller) {
        if (seller == null || seller.getStatus() == null) {
            return "не заполнен";
        }
        return switch (seller.getStatus()) {
            case ACTIVE -> "активен";
            case BLOCKED -> "заблокирован";
        };
    }

    private static List<DashboardRecommendation> dashboardRecommendations(Seller seller,
                                                                           long productCount,
                                                                           long forecastCount,
                                                                           long unforecastedProductCount,
                                                                           long highStockProductCount,
                                                                           Optional<PromotionForecast> lastForecast) {
        List<DashboardRecommendation> recommendations = new ArrayList<>();
        if (seller == null) {
            recommendations.add(new DashboardRecommendation(
                    "Заполните профиль продавца, чтобы добавлять товары и рассчитывать прогнозы.",
                    "/app/profile/edit",
                    "Открыть профиль"
            ));
            return recommendations;
        }
        if (productCount == 0) {
            recommendations.add(new DashboardRecommendation(
                    "У вас пока нет товаров. Добавьте первый товар, чтобы начать прогнозирование.",
                    "/app/products/new",
                    "Добавить товар"
            ));
            return recommendations;
        }
        if (unforecastedProductCount > 0) {
            recommendations.add(new DashboardRecommendation(
                    "У вас есть " + unforecastedProductCount + " товар(ов) без прогноза. Рассчитайте параметры продвижения.",
                    "/app/forecasts",
                    "Рассчитать прогноз"
            ));
        }
        if (forecastCount == 0) {
            recommendations.add(new DashboardRecommendation(
                    "Прогнозы пока не выполнялись. Выберите товар и задайте бюджет продвижения для первого расчета.",
                    "/app/forecasts",
                    "Рассчитать прогноз"
            ));
        } else {
            lastForecast.ifPresent(forecast -> {
                if (forecast.getCalculatedAt() != null
                        && forecast.getCalculatedAt().isBefore(Instant.now().minus(7, ChronoUnit.DAYS))) {
                    recommendations.add(new DashboardRecommendation(
                            "Последний расчет был выполнен более 7 дней назад. Рекомендуется обновить прогноз.",
                            "/app/forecasts",
                            "Обновить прогноз"
                    ));
                } else {
                    recommendations.add(new DashboardRecommendation(
                            "Последний расчет выполнен " + forecast.getCalculatedAtLabel() + ".",
                            "/app/forecasts",
                            "Открыть прогнозы"
                    ));
                }
            });
        }
        if (highStockProductCount > 0) {
            recommendations.add(new DashboardRecommendation(
                    "У " + highStockProductCount + " товар(ов) высокий остаток. Для них стоит проверить сценарий продвижения.",
                    "/app/forecasts",
                    "Проверить"
            ));
        }
        if (recommendations.isEmpty()) {
            recommendations.add(new DashboardRecommendation(
                    "Данные выглядят актуальными. Следите за остатками и обновляйте прогнозы при изменении бюджета.",
                    "/app/forecasts",
                    "Открыть прогнозы"
            ));
        }
        return recommendations;
    }

    public record DashboardRecommendation(String text, String href, String actionLabel) {
    }
}
