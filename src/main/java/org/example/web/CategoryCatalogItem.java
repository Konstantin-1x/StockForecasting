package org.example.web;

import java.util.List;

public record CategoryCatalogItem(
        String key,
        Long categoryId,
        String name,
        int depth,
        boolean selected,
        boolean activeTrail,
        boolean hasChildren,
        List<CategoryCatalogItem> children
) {
}
