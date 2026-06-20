package org.example.web.data;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ForecastDataQualityService {

    private final JdbcTemplate jdbcTemplate;

    public ForecastDataQualityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ForecastDataQualityReport buildReport() {
        List<ForecastDataQualityCheck> checks = new ArrayList<>();

        long trackedProducts = count("select count(*) from tracked_marketplace_products");
        long snapshots = count("select count(*) from marketplace_product_snapshots");
        long orphanSnapshots = count("""
                select count(*)
                from marketplace_product_snapshots snapshot
                left join tracked_marketplace_products product
                    on product.tracked_product_id = snapshot.tracked_product_id
                where product.tracked_product_id is null
                """);
        long productsWithTwoSnapshots = count("""
                select count(*)
                from (
                    select tracked_product_id
                    from marketplace_product_snapshots
                    group by tracked_product_id
                    having count(*) >= 2
                ) product_series
                """);
        long productsWithFiveSnapshots = count("""
                select count(*)
                from (
                    select tracked_product_id
                    from marketplace_product_snapshots
                    group by tracked_product_id
                    having count(*) >= 5
                ) product_series
                """);
        long productsWithoutInitialSnapshot = count("""
                select count(*)
                from tracked_marketplace_products product
                where not exists (
                    select 1
                    from marketplace_product_snapshots snapshot
                    where snapshot.tracked_product_id = product.tracked_product_id
                      and snapshot.measurement_source in ('DISCOVERY_CATALOG', 'DISCOVERY_BACKFILL')
                )
                """);
        long unexpectedSources = count("""
                select count(*)
                from marketplace_product_snapshots
                where measurement_source not in ('DISCOVERY_CATALOG', 'DISCOVERY_BACKFILL', 'PRODUCT_DETAIL')
                """);

        BigDecimal avgSnapshotsPerProduct = decimal("""
                select coalesce(avg(snapshot_count), 0)
                from (
                    select count(*) as snapshot_count
                    from marketplace_product_snapshots
                    group by tracked_product_id
                ) counts
                """);
        String timeRange = value("""
                select coalesce(to_char(min(collected_at), 'YYYY-MM-DD HH24:MI'), 'нет данных')
                       || ' — '
                       || coalesce(to_char(max(collected_at), 'YYYY-MM-DD HH24:MI'), 'нет данных')
                from marketplace_product_snapshots
                """);

        checks.add(new ForecastDataQualityCheck(
                "Объем временного ряда",
                snapshots + " замеров / " + trackedProducts + " товаров",
                snapshots > 0 && trackedProducts > 0 ? "OK" : "BAD",
                "Для прогнозирования основной источник - marketplace_product_snapshots."
        ));
        checks.add(new ForecastDataQualityCheck(
                "Временной диапазон",
                timeRange,
                snapshots > 0 ? "OK" : "BAD",
                "После недельного сбора диапазон должен покрывать все дни эксперимента."
        ));
        checks.add(new ForecastDataQualityCheck(
                "Связи snapshot -> товар",
                orphanSnapshots + " битых ссылок",
                orphanSnapshots == 0 ? "OK" : "BAD",
                "Каждый замер обязан ссылаться на tracked_marketplace_products.",
                orphanSnapshots > 0
        ));
        checks.add(new ForecastDataQualityCheck(
                "Начальный замер",
                productsWithoutInitialSnapshot + " товаров без стартового замера",
                productsWithoutInitialSnapshot == 0 ? "OK" : "WARN",
                "У каждого нового товара должен быть DISCOVERY_CATALOG или DISCOVERY_BACKFILL.",
                productsWithoutInitialSnapshot > 0
        ));
        checks.add(new ForecastDataQualityCheck(
                "Глубина истории",
                productsWithTwoSnapshots + " товаров >= 2 замера; " + productsWithFiveSnapshots + " товаров >= 5 замеров",
                productsWithFiveSnapshots > 0 ? "OK" : "WARN",
                "Для недельного сбора ожидается рост числа товаров с несколькими почасовыми точками."
        ));
        checks.add(new ForecastDataQualityCheck(
                "Среднее число замеров",
                avgSnapshotsPerProduct == null ? "0" : avgSnapshotsPerProduct.setScale(2, java.math.RoundingMode.HALF_UP).toString(),
                avgSnapshotsPerProduct != null && avgSnapshotsPerProduct.compareTo(BigDecimal.ONE) > 0 ? "OK" : "WARN",
                "После недели значение должно заметно вырасти относительно 1."
        ));
        checks.add(new ForecastDataQualityCheck(
                "Источники замеров",
                unexpectedSources + " неизвестных источников",
                unexpectedSources == 0 ? "OK" : "BAD",
                "Допустимы DISCOVERY_CATALOG, DISCOVERY_BACKFILL и PRODUCT_DETAIL.",
                unexpectedSources > 0
        ));

        return new ForecastDataQualityReport(Instant.now(), checks);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }

    private String value(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }
}
