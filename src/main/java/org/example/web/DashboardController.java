package org.example.web;

import jakarta.validation.Valid;
import org.example.domain.CompetitorOffer;
import org.example.forecast.NeuralForecastJobService;
import org.example.parser.wb.DataCollectionControlService;
import org.example.parser.wb.DataCollectionStatus;
import org.example.parser.wb.WildberriesImportResult;
import org.example.parser.wb.WildberriesParserService;
import org.example.repository.CompetitorOfferRepository;
import org.example.repository.AppUserRepository;
import org.example.repository.ProductCategoryRepository;
import org.example.repository.ProductRepository;
import org.example.repository.PromotionForecastRepository;
import org.example.repository.PromotionRepository;
import org.example.repository.SellerRepository;
import org.example.web.data.ForecastDataQualityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.IntStream;

@Controller
public class DashboardController {

    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionForecastRepository forecastRepository;
    private final CompetitorOfferRepository offerRepository;
    private final AppUserRepository userRepository;
    private final WildberriesParserService parserService;
    private final CategoryCatalogService categoryCatalogService;
    private final ForecastDataQualityService forecastDataQualityService;
    private final NeuralForecastJobService neuralForecastJobService;
    private final LandingPageContentService landingPageContentService;
    private final DataCollectionControlService dataCollectionControlService;

    public DashboardController(SellerRepository sellerRepository,
                               ProductRepository productRepository,
                               ProductCategoryRepository categoryRepository,
                               PromotionRepository promotionRepository,
                               PromotionForecastRepository forecastRepository,
                               CompetitorOfferRepository offerRepository,
                               AppUserRepository userRepository,
                               WildberriesParserService parserService,
                               CategoryCatalogService categoryCatalogService,
                               ForecastDataQualityService forecastDataQualityService,
                               NeuralForecastJobService neuralForecastJobService,
                               LandingPageContentService landingPageContentService,
                               DataCollectionControlService dataCollectionControlService) {
        this.sellerRepository = sellerRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.promotionRepository = promotionRepository;
        this.forecastRepository = forecastRepository;
        this.offerRepository = offerRepository;
        this.userRepository = userRepository;
        this.parserService = parserService;
        this.categoryCatalogService = categoryCatalogService;
        this.forecastDataQualityService = forecastDataQualityService;
        this.neuralForecastJobService = neuralForecastJobService;
        this.landingPageContentService = landingPageContentService;
        this.dataCollectionControlService = dataCollectionControlService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("landingPage", landingPageContentService.currentContent());
        return "home";
    }

    @GetMapping("/admin/home/edit")
    public String editHome(Model model) {
        if (!model.containsAttribute("landingPageForm")) {
            model.addAttribute("landingPageForm", landingPageContentService.toForm());
        }
        return "home-edit";
    }

    @PostMapping("/admin/home/edit")
    public String updateHome(@Valid @ModelAttribute("landingPageForm") LandingPageForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "home-edit";
        }

        landingPageContentService.update(form);
        redirectAttributes.addFlashAttribute("status", "Стартовая страница обновлена.");
        return "redirect:/";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/offers")
    public String legacyOffers(@RequestParam(required = false) String categoryKey,
                               @RequestParam(required = false) Integer page,
                               RedirectAttributes redirectAttributes) {
        if (categoryKey != null && !categoryKey.isBlank()) {
            redirectAttributes.addAttribute("categoryKey", categoryKey);
        }
        if (page != null && page > 0) {
            redirectAttributes.addAttribute("page", page);
        }
        return "redirect:/admin/offers";
    }

    @GetMapping("/products")
    public String legacyProducts() {
        return "redirect:/admin/products";
    }

    @GetMapping("/forecasts")
    public String legacyForecasts() {
        return "redirect:/admin/forecasts";
    }

    @GetMapping("/admin/offers")
    public String offers(@RequestParam(required = false) String categoryKey,
                         @RequestParam(defaultValue = "0") Integer page,
                         Model model) {
        int currentPage = Math.max(0, page == null ? 0 : page);
        PageRequest pageRequest = PageRequest.of(
                currentPage,
                100,
                Sort.by(Sort.Direction.DESC, "collectedAt")
        );

        Page<CompetitorOffer> offerPage;
        String categoryUrlPrefix = categoryCatalogService.selectedCategoryUrlPrefix(categoryKey);
        if (categoryUrlPrefix == null) {
            offerPage = offerRepository.findAll(pageRequest);
        } else {
            offerPage = offerRepository.findByCategoryUrlPrefix(categoryUrlPrefix, pageRequest);
        }

        model.addAttribute("catalog", categoryCatalogService.buildCatalog(categoryUrlPrefix));
        model.addAttribute("selectedCategoryKey", categoryUrlPrefix);
        model.addAttribute("offerPage", offerPage);
        model.addAttribute("offers", offerPage.getContent());
        model.addAttribute("paginationPages", pageNumbers(offerPage));
        return "offers";
    }

    @GetMapping("/admin/products")
    public String products(Model model) {
        model.addAttribute("products", productRepository.findAll(Sort.by(Sort.Direction.DESC, "id")));
        return "products";
    }

    @GetMapping("/admin/forecasts")
    public String forecasts(Model model) {
        model.addAttribute("forecasts", forecastRepository.findAll(Sort.by(Sort.Direction.DESC, "calculatedAt")));
        model.addAttribute("neuralForecastJobStatus", neuralForecastJobService.currentStatus());
        return "forecasts";
    }

    @GetMapping("/admin/data-quality")
    public String dataQuality(Model model) {
        model.addAttribute("qualityReport", forecastDataQualityService.buildReport());
        return "admin-data-quality";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("sellerCount", sellerRepository.count());
        model.addAttribute("productCount", productRepository.count());
        model.addAttribute("forecastCount", forecastRepository.count());
        model.addAttribute("categoryCount", categoryRepository.count());
        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("neuralForecastJobStatus", neuralForecastJobService.currentStatus());
        model.addAttribute("dataCollectionStatus", dataCollectionControlService.currentStatus());
        return "admin";
    }

    @PostMapping("/admin/data-collection/start")
    public String startDataCollection(RedirectAttributes redirectAttributes) {
        DataCollectionStatus status = dataCollectionControlService.startCollection();
        redirectAttributes.addFlashAttribute("status", status.message());
        return "redirect:/admin";
    }

    @PostMapping("/admin/data-collection/stop")
    public String stopDataCollection(RedirectAttributes redirectAttributes) {
        DataCollectionStatus status = dataCollectionControlService.stopCollection();
        redirectAttributes.addFlashAttribute("status", status.message());
        return "redirect:/admin";
    }

    @PostMapping({"/admin/parser/import", "/parser/import"})
    public String importWildberries(@RequestParam(defaultValue = "3") Integer maxCategories,
                                    @RequestParam(defaultValue = "1") Integer maxPagesPerCategory,
                                    RedirectAttributes redirectAttributes) {
        try {
            WildberriesImportResult result = parserService.importRublesForReviews(maxCategories, maxPagesPerCategory);
            redirectAttributes.addFlashAttribute(
                    "status",
                    "Импорт завершен: сохранено " + result.offersSaved()
                            + " предложений, обработано " + result.pagesProcessed() + " страниц."
            );
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Импорт не выполнен: " + e.getMessage());
        }
        return "redirect:/admin/offers";
    }

    @PostMapping("/admin/forecasts/recalculate")
    public String recalculateNeuralForecasts(RedirectAttributes redirectAttributes) {
        boolean started = neuralForecastJobService.startRecalculation();
        if (started) {
            redirectAttributes.addFlashAttribute("status", "Нейросетевой пересчет запущен в фоне. Статус можно смотреть на странице прогнозов.");
        } else {
            redirectAttributes.addFlashAttribute("status", "Нейросетевой пересчет уже выполняется.");
        }
        return "redirect:/admin/forecasts";
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
