package org.example.parser.wb;

import java.util.List;

public record WildberriesPage(
        int total,
        List<WildberriesParsedProduct> products
) {
}
