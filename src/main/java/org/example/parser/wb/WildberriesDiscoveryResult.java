package org.example.parser.wb;

public record WildberriesDiscoveryResult(
        boolean baselineScan,
        boolean baselineReady,
        int categoriesLoaded,
        int categoriesScanned,
        int pagesScanned,
        int productsSeen,
        int baselineProductsAdded,
        int newProductsCreated,
        int existingProductsSkipped,
        int errors
) {
}
