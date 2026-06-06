package org.example.parser.wb;

final class WildberriesCatalogUrlBuilder {

    private WildberriesCatalogUrlBuilder() {
    }

    static String build(WildberriesCategoryRef category, int page) {
        String query = category.query() == null ? "" : category.query();
        StringBuilder params = new StringBuilder();
        params.append("ab_testing=false");
        params.append("&action=").append(category.action());
        params.append("&appType=1");
        if (!query.isBlank()) {
            params.append("&").append(query);
        }
        params.append("&curr=rub");
        params.append("&dest=-1257786");
        params.append("&hide_dtype=11");
        params.append("&lang=ru");
        params.append("&page=").append(page);
        params.append("&sort=popular");
        params.append("&spp=30");

        return "https://www.wildberries.ru/__internal/u-catalog/catalog/"
                + category.shardKey()
                + "/v4/catalog?"
                + params;
    }
}
