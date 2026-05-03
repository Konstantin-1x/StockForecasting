package org.example.parser.wb;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/parser/wildberries")
public class WildberriesParserController {

    private final WildberriesParserService parserService;
    private final WildberriesHttpClient httpClient;

    public WildberriesParserController(WildberriesParserService parserService,
                                       WildberriesHttpClient httpClient) {
        this.parserService = parserService;
        this.httpClient = httpClient;
    }

    @PostMapping("/rubles-for-reviews/import")
    public WildberriesImportResult importRublesForReviews(
            @RequestParam(required = false) @Min(1) @Max(500) Integer maxCategories,
            @RequestParam(required = false) @Min(1) @Max(100) Integer maxPagesPerCategory
    ) {
        return parserService.importRublesForReviews(maxCategories, maxPagesPerCategory);
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        return httpClient.diagnostics();
    }
}
