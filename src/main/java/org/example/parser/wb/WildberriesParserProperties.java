package org.example.parser.wb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "wb.parser")
public class WildberriesParserProperties {

    private String promotionsUrl = "https://static-basket-01.wbbasket.ru/vol0/data/promotions/rubli-za-otzyvy-v3.json";
    private Path cookieFile = Path.of("cookies.txt");
    private int maxCategories = 3;
    private int maxPagesPerCategory = 1;
    private Duration requestTimeout = Duration.ofSeconds(20);
    private Duration requestDelay = Duration.ofMillis(400);

    public String getPromotionsUrl() {
        return promotionsUrl;
    }

    public void setPromotionsUrl(String promotionsUrl) {
        this.promotionsUrl = promotionsUrl;
    }

    public Path getCookieFile() {
        return cookieFile;
    }

    public void setCookieFile(Path cookieFile) {
        this.cookieFile = cookieFile;
    }

    public int getMaxCategories() {
        return maxCategories;
    }

    public void setMaxCategories(int maxCategories) {
        this.maxCategories = maxCategories;
    }

    public int getMaxPagesPerCategory() {
        return maxPagesPerCategory;
    }

    public void setMaxPagesPerCategory(int maxPagesPerCategory) {
        this.maxPagesPerCategory = maxPagesPerCategory;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getRequestDelay() {
        return requestDelay;
    }

    public void setRequestDelay(Duration requestDelay) {
        this.requestDelay = requestDelay;
    }
}
