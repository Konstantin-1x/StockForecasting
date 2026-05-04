package org.example.parser.wb;

public record WildberriesCategoryRef(
        String name,
        String categoryUrl,
        String shardKey,
        String query,
        String action,
        String parentCategoryUrl,
        boolean scannable
) {

    public WildberriesCategoryRef(String name,
                                  String categoryUrl,
                                  String shardKey,
                                  String query,
                                  String action) {
        this(name, categoryUrl, shardKey, query, action, null, true);
    }
}
