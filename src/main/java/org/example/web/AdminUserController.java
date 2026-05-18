package org.example.web;

import org.example.domain.AppUser;
import org.example.domain.PromotionForecast;
import org.example.domain.Seller;
import org.example.repository.AppUserRepository;
import org.example.repository.ProductRepository;
import org.example.repository.PromotionForecastRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class AdminUserController {

    private final AppUserRepository userRepository;
    private final ProductRepository productRepository;
    private final PromotionForecastRepository forecastRepository;

    public AdminUserController(AppUserRepository userRepository,
                               ProductRepository productRepository,
                               PromotionForecastRepository forecastRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.forecastRepository = forecastRepository;
    }

    @GetMapping("/admin/users")
    public String users(Model model) {
        List<UserOverview> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toOverview)
                .toList();
        model.addAttribute("users", users);
        return "admin-users";
    }

    @GetMapping("/admin/users/{id}")
    public String userDetails(@PathVariable Long id, Model model) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Seller seller = user.getSeller();

        model.addAttribute("profileUser", user);
        model.addAttribute("seller", seller);
        model.addAttribute("products", seller == null ? List.of() : productRepository.findBySellerOrderByIdDesc(seller));
        model.addAttribute("forecasts", seller == null ? List.<PromotionForecast>of()
                : forecastRepository.findByProduct_SellerOrderByCalculatedAtDesc(seller));
        return "admin-user-details";
    }

    private UserOverview toOverview(AppUser user) {
        Seller seller = user.getSeller();
        long productCount = seller == null ? 0 : productRepository.countBySeller(seller);
        long forecastCount = seller == null ? 0 : forecastRepository.countByProduct_Seller(seller);
        return new UserOverview(user, productCount, forecastCount);
    }

    public record UserOverview(AppUser user, long productCount, long forecastCount) {
    }
}
