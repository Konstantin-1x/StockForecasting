package org.example.web;

import jakarta.validation.Valid;
import org.example.domain.AppUser;
import org.example.domain.PromotionForecast;
import org.example.domain.Seller;
import org.example.repository.AppUserRepository;
import org.example.repository.ProductRepository;
import org.example.repository.PromotionForecastRepository;
import org.example.security.AppUserService;
import org.example.security.RegistrationForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.IntStream;

@Controller
public class AdminUserController {

    private static final int USER_DETAILS_PAGE_SIZE = 20;

    private final AppUserRepository userRepository;
    private final ProductRepository productRepository;
    private final PromotionForecastRepository forecastRepository;
    private final AppUserService appUserService;

    public AdminUserController(AppUserRepository userRepository,
                               ProductRepository productRepository,
                               PromotionForecastRepository forecastRepository,
                               AppUserService appUserService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.forecastRepository = forecastRepository;
        this.appUserService = appUserService;
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        addUsersModel(model);
        if (!model.containsAttribute("adminRegistrationForm")) {
            model.addAttribute("adminRegistrationForm", new RegistrationForm());
        }
        return "admin-users";
    }

    @PostMapping("/admin/users/admins")
    public String createAdmin(@Valid @ModelAttribute("adminRegistrationForm") RegistrationForm form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addUsersModel(model);
            return "admin-users";
        }

        try {
            appUserService.registerAdminUser(form);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("admin.failed", e.getMessage());
            addUsersModel(model);
            return "admin-users";
        }

        redirectAttributes.addFlashAttribute("status", "Администратор создан.");
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/users/{id}")
    public String userDetails(@PathVariable Long id,
                              @RequestParam(defaultValue = "0") Integer productsPage,
                              @RequestParam(defaultValue = "0") Integer forecastsPage,
                              Model model) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Seller seller = user.getSeller();
        PageRequest productPageRequest = PageRequest.of(Math.max(0, productsPage == null ? 0 : productsPage), USER_DETAILS_PAGE_SIZE);
        PageRequest forecastPageRequest = PageRequest.of(Math.max(0, forecastsPage == null ? 0 : forecastsPage), USER_DETAILS_PAGE_SIZE);
        Page<org.example.domain.Product> productPage = seller == null
                ? Page.empty(productPageRequest)
                : productRepository.findBySellerOrderByIdDesc(seller, productPageRequest);
        Page<PromotionForecast> forecastPage = seller == null
                ? Page.empty(forecastPageRequest)
                : forecastRepository.findByProduct_SellerOrderByCalculatedAtDesc(seller, forecastPageRequest);

        model.addAttribute("profileUser", user);
        model.addAttribute("seller", seller);
        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("productPaginationPages", pageNumbers(productPage));
        model.addAttribute("forecastPage", forecastPage);
        model.addAttribute("forecasts", forecastPage.getContent());
        model.addAttribute("forecastPaginationPages", pageNumbers(forecastPage));
        return "admin-user-details";
    }

    private UserOverview toOverview(AppUser user) {
        Seller seller = user.getSeller();
        long productCount = seller == null ? 0 : productRepository.countBySeller(seller);
        long forecastCount = seller == null ? 0 : forecastRepository.countByProduct_Seller(seller);
        return new UserOverview(user, productCount, forecastCount);
    }

    private void addUsersModel(Model model) {
        List<UserOverview> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toOverview)
                .toList();
        model.addAttribute("users", users);
    }

    public record UserOverview(AppUser user, long productCount, long forecastCount) {
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
}
