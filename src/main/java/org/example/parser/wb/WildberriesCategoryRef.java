package org.example.parser.wb;

public record WildberriesCategoryRef(
        String name,
        String categoryUrl,
        String shardKey,
        String query,
        String action
) {
}
