package org.example.parser.wb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WildberriesResponseParserTest {

    private final WildberriesResponseParser parser = new WildberriesResponseParser(new ObjectMapper());

    @Test
    void parsesPromotionCategoriesRecursively() throws IOException {
        String json = """
                {
                  "promo": {"id": 123},
                  "menu": [
                    {"childNodes": [
                      {
                        "name": "Обувь",
                        "url": "/promotions/rubli-za-otzyvy/obuv",
                        "shardKey": "catalog",
                        "query": "cat=1&action=999",
                        "childNodes": [
                          {
                            "name": "Кроссовки",
                            "url": "/promotions/rubli-za-otzyvy/obuv/krossovki",
                            "shardKey": "catalog",
                            "query": "cat=2"
                          }
                        ]
                      }
                    ]}
                  ]
                }
                """;

        List<WildberriesCategoryRef> categories = parser.parsePromotionCategories(json);

        assertThat(categories).hasSize(2);
        assertThat(categories.getFirst().categoryUrl())
                .isEqualTo("https://www.wildberries.ru/promotions/rubli-za-otzyvy/obuv");
        assertThat(categories.getFirst().query()).isEqualTo("cat=1");
        assertThat(categories.getFirst().action()).isEqualTo("123");
    }

    @Test
    void parsesCatalogProductsWithFeedbackRewardAndPrice() throws IOException {
        String json = """
                {
                  "data": {
                    "total": 1,
                    "products": [
                      {
                        "id": 987,
                        "name": "Тестовый товар",
                        "feedbackPoints": "450",
                        "totalQuantity": "12",
                        "supplier": "Test Store",
                        "supplierId": 55,
                        "sizes": [{"price": {"product": 150000}}]
                      }
                    ]
                  }
                }
                """;

        WildberriesPage page = parser.parseCatalogPage(json);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.products()).hasSize(1);
        WildberriesParsedProduct product = page.products().getFirst();
        assertThat(product.article()).isEqualTo("987");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(product.feedbackReward()).isEqualByComparingTo(new BigDecimal("450"));
        assertThat(product.benefitPercent()).isEqualByComparingTo(new BigDecimal("30.0000"));
    }
}
