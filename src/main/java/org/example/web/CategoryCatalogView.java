package org.example.web;

import java.util.List;

public record CategoryCatalogView(
        List<CategoryCatalogItem> rootCategories,
        String selectedCategoryKey,
        String selectedRootCategoryKey,
        String selectedCategoryName
) {
}
