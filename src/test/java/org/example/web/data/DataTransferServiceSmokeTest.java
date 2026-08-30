package org.example.web.data;

import org.example.StockForecastingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = StockForecastingApplication.class,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:5432/postgres?currentSchema=stockforecasting_import_smoke",
                "spring.datasource.username=postgres",
                "spring.datasource.password=admin",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.default_schema=stockforecasting_import_smoke",
                "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
                "spring.sql.init.mode=never",
                "wb.parser.continuous-scan-enabled=false",
                "wb.parser.baseline-on-startup=false",
                "wb.parser.monitoring-enabled=false",
                "wb.parser.proxy-enabled=false"
        }
)
class DataTransferServiceSmokeTest {

    @Autowired
    private DataTransferService dataTransferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void importsWebExportArchive() throws Exception {
        DataExportFile archive = dataTransferService.exportZipToTempFile();
        try {
            assertThat(archive.size()).isPositive();

            try (var input = Files.newInputStream(archive.path())) {
                DataImportResult result = dataTransferService.importZip(input);
                assertThat(result.totalRows()).isPositive();
            }
        } finally {
            Files.deleteIfExists(archive.path());
        }

        Long users = jdbcTemplate.queryForObject(
                "select count(*) from app_users",
                Long.class
        );
        assertThat(users).isNotNull().isGreaterThanOrEqualTo(2L);
    }
}
