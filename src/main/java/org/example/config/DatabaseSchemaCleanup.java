package org.example.config;

import org.example.parser.wb.WildberriesCategoryNameTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseSchemaCleanup {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaCleanup.class);

    @Bean
    ApplicationRunner removeUnusedRawJsonColumn(JdbcTemplate jdbcTemplate) {
        return ignored -> {
            try {
                jdbcTemplate.execute("alter table marketplace_product_snapshots drop column if exists raw_json");
                log.info("Database cleanup checked marketplace_product_snapshots.raw_json");
            } catch (RuntimeException e) {
                log.warn("Database cleanup skipped marketplace_product_snapshots.raw_json: {}", e.getMessage());
            }
        };
    }

    @Bean
    ApplicationRunner localizeWildberriesCategoryNames(JdbcTemplate jdbcTemplate) {
        return ignored -> {
            try {
                int updated = 0;
                for (var entry : WildberriesCategoryNameTranslator.replacements().entrySet()) {
                    updated += jdbcTemplate.update(
                            "update product_categories set category_name = ? where category_name = ?",
                            entry.getValue(),
                            entry.getKey()
                    );
                }
                log.info("Database cleanup localized {} Wildberries category name(s)", updated);
            } catch (RuntimeException e) {
                log.warn("Database cleanup skipped Wildberries category localization: {}", e.getMessage());
            }
        };
    }
}
