package org.example.parser.wb;

public record WildberriesImportResult(
        int categoriesLoaded,
        int categoriesProcessed,
        int pagesProcessed,
        int productsParsed,
        int offersSaved
) {
}
