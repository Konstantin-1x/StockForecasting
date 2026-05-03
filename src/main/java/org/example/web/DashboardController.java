package org.example.web;

import org.example.parser.wb.WildberriesImportResult;
import org.example.parser.wb.WildberriesParserService;
import org.example.repository.CompetitorOfferRepository;
import org.example.repository.ProductCategoryRepository;
import org.example.repository.ProductRepository;
import org.example.repository.PromotionForecastRepository;
import org.example.repository.PromotionRepository;
import org.example.repository.SellerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DashboardController {

    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionForecastRepository forecastRepository;
    private final CompetitorOfferRepository offerRepository;
    private final WildberriesParserService parserService;

    public DashboardController(SellerRepository sellerRepository,
                               ProductRepository productRepository,
                               ProductCategoryRepository categoryRepository,
                               PromotionRepository promotionRepository,
                               PromotionForecastRepository forecastRepository,
                               CompetitorOfferRepository offerRepository,
                               WildberriesParserService parserService) {
        this.sellerRepository = sellerRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.promotionRepository = promotionRepository;
        this.forecastRepository = forecastRepository;
        this.offerRepository = offerRepository;
        this.parserService = parserService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("sellerCount", sellerRepository.count());
        model.addAttribute("productCount", productRepository.count());
        model.addAttribute("categoryCount", categoryRepository.count());
        model.addAttribute("promotionCount", promotionRepository.count());
        model.addAttribute("forecastCount", forecastRepository.count());
        model.addAttribute("offerCount", offerRepository.count());
        model.addAttribute("latestOffers", offerRepository.findAll(
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "collectedAt"))
        ));
        return "dashboard";
    }

    @GetMapping("/offers")
    public String offers(Model model) {
        model.addAttribute("offers", offerRepository.findAll(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "collectedAt"))
        ));
        return "offers";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", productRepository.findAll(Sort.by(Sort.Direction.DESC, "id")));
        return "products";
    }

    @GetMapping("/forecasts")
    public String forecasts(Model model) {
        model.addAttribute("forecasts", forecastRepository.findAll(Sort.by(Sort.Direction.DESC, "calculatedAt")));
        return "forecasts";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("sellerCount", sellerRepository.count());
        model.addAttribute("productCount", productRepository.count());
        model.addAttribute("offerCount", offerRepository.count());
        model.addAttribute("forecastCount", forecastRepository.count());
        model.addAttribute("categoryCount", categoryRepository.count());
        model.addAttribute("latestOffers", offerRepository.findAll(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "collectedAt"))
        ));
        return "admin";
    }

    @PostMapping("/parser/import")
    public String importWildberries(@RequestParam(defaultValue = "3") Integer maxCategories,
                                    @RequestParam(defaultValue = "1") Integer maxPagesPerCategory,
                                    RedirectAttributes redirectAttributes) {
        try {
            WildberriesImportResult result = parserService.importRublesForReviews(maxCategories, maxPagesPerCategory);
            redirectAttributes.addFlashAttribute("status", "Импорт завершен: сохранено "
                    + result.offersSaved() + " предложений, обработано "
                    + result.pagesProcessed() + " страниц.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Импорт не выполнен: " + e.getMessage());
        }
        return "redirect:/offers";
    }
}
