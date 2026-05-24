package org.example.web.data;

import org.example.StockForecastingApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = StockForecastingApplication.class,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:5432/stockforecasting_import_smoke",
                "spring.datasource.username=postgres",
                "spring.datasource.password=admin",
                "spring.jpa.hibernate.ddl-auto=create",
                "wb.parser.continuous-scan-enabled=false",
                "wb.parser.monitoring-enabled=false"
        }
)
@EnabledIfEnvironmentVariable(named = "SMOKE_IMPORT_ZIP", matches = ".+")
class DataTransferServiceSmokeTest {

    @Autowired
    private DataTransferService dataTransferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void importsWebExportArchive() throws Exception {
        Path archive = Path.of(System.getenv("SMOKE_IMPORT_ZIP"));
        try (var input = Files.newInputStream(archive)) {
            DataImportResult result = dataTransferService.importZip(input);
            assertThat(result.totalRows()).isPositive();
        }

        Long snapshots = jdbcTemplate.queryForObject(
                "select count(*) from marketplace_product_snapshots",
                Long.class
        );
        assertThat(snapshots).isNotNull().isPositive();
    }
}
