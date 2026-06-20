package org.example.parser.wb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WildberriesResponseParser {

    private static final String WB_URL_PREFIX = "https://www.wildberries.ru";

    private final ObjectMapper objectMapper;

    public WildberriesResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<WildberriesCategoryRef> parsePromotionCategories(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        String action = root.path("promo").path("id").asText();
        if (action == null || action.isBlank()) {
            throw new IOException("Promotion id was not found in Wildberries promotions JSON");
        }

        Map<String, WildberriesCategoryRef> result = new LinkedHashMap<>();
        JsonNode menu = root.path("menu");
        if (menu.isArray()) {
            for (JsonNode menuNode : menu) {
                JsonNode childNodes = menuNode.path("childNodes");
                if (childNodes.isArray()) {
                    for (JsonNode categoryNode : childNodes) {
                        collectCategory(categoryNode, action, null, result);
                    }
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    public WildberriesPage parseCatalogPage(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode productsNode = findProductsNode(root);
        if (!productsNode.isArray()) {
            return new WildberriesPage(0, List.of());
        }

        int total = findTotal(root, productsNode.size());
        List<WildberriesParsedProduct> products = new ArrayList<>();
        for (JsonNode productNode : productsNode) {
            parseProduct(productNode).ifPresent(products::add);
        }
        return new WildberriesPage(total, products);
    }

    public WildberriesProductDetails parseProductDetail(String json, String expectedArticle) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode productsNode = findProductsNode(root);
        if (!productsNode.isArray() || productsNode.isEmpty()) {
            throw new IOException("Product detail response does not contain products");
        }

        JsonNode productNode = productsNode.get(0);
        if (expectedArticle != null && !expectedArticle.isBlank()) {
            for (JsonNode candidate : productsNode) {
                if (expectedArticle.equals(text(candidate, "id"))) {
                    productNode = candidate;
                    break;
                }
            }
        }

        String article = defaultText(text(productNode, "id"), expectedArticle);
        if (article == null || article.isBlank()) {
            throw new IOException("Product article was not found in detail response");
        }

        return new WildberriesProductDetails(
                article,
                defaultText(text(productNode, "name"), "Unknown product"),
                text(productNode, "supplier"),
                longValue(productNode, "supplierId"),
                firstProductPrice(productNode),
                feedbackReward(productNode),
                totalStock(productNode),
                decimal(productNode, "reviewRating"),
                integer(productNode, "feedbacks"),
                WildberriesProductDetailUrlBuilder.cardUrl(article),
                WildberriesProductDetailUrlBuilder.build(article)
        );
    }

    private void collectCategory(JsonNode node,
                                 String action,
                                 String parentCategoryUrl,
                                 Map<String, WildberriesCategoryRef> result) {
        String categoryUrl = addCategoryIfValid(node, action, parentCategoryUrl, result);
        JsonNode childNodes = node.path("childNodes");
        if (childNodes.isArray()) {
            for (JsonNode child : childNodes) {
                collectCategory(child, action, categoryUrl == null ? parentCategoryUrl : categoryUrl, result);
            }
        }
    }

    private String addCategoryIfValid(JsonNode node,
                                      String action,
                                      String parentCategoryUrl,
                                      Map<String, WildberriesCategoryRef> result) {
        String url = text(node, "url");
        if (url == null || url.isBlank()
                || url.startsWith("https://vmeste.wildberries.ru")
                || url.startsWith("https://travel.wildberries.ru")
                || url.startsWith("https://digital.wildberries.ru")) {
            return null;
        }

        String shardKey = text(node, "shardKey");
        String categoryUrl = ensureWildberriesUrl(url);
        boolean scannable = shardKey != null && !shardKey.isBlank() && !"blackhole".equals(shardKey);
        result.putIfAbsent(categoryUrl, new WildberriesCategoryRef(
                defaultText(text(node, "name"), categoryUrl),
                categoryUrl,
                shardKey,
                sanitizeQuery(text(node, "query")),
                action,
                parentCategoryUrl,
                scannable
        ));
        return categoryUrl;
    }

    private static String ensureWildberriesUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return WB_URL_PREFIX + url;
    }

    private static String sanitizeQuery(String query) {
        if (query == null) {
            return "";
        }
        String sanitized = query
                .replaceAll("&?action=\\d+", "")
                .replaceAll("action=\\d+&?", "")
                .trim();
        while (sanitized.startsWith("&")) {
            sanitized = sanitized.substring(1);
        }
        while (sanitized.endsWith("&")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }

    private static JsonNode findProductsNode(JsonNode root) {
        JsonNode products = root.path("products");
        if (products.isArray()) {
            return products;
        }
        return root.path("data").path("products");
    }

    private static int findTotal(JsonNode root, int fallback) {
        JsonNode total = root.path("total");
        if (total.isNumber()) {
            return total.asInt();
        }
        total = root.path("data").path("total");
        if (total.isNumber()) {
            return total.asInt();
        }
        return fallback;
    }

    private java.util.Optional<WildberriesParsedProduct> parseProduct(JsonNode productNode) {
        String article = text(productNode, "id");
        BigDecimal feedbackReward = feedbackReward(productNode);
        Integer stock = integer(productNode, "totalQuantity");
        BigDecimal price = firstProductPrice(productNode);

        if (article == null || article.isBlank()
                || feedbackReward == null || feedbackReward.signum() <= 0
                || stock == null || stock <= 0
                || price == null || price.signum() <= 0) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new WildberriesParsedProduct(
                article,
                defaultText(text(productNode, "name"), "Без названия"),
                text(productNode, "supplier"),
                longValue(productNode, "supplierId"),
                price,
                feedbackReward,
                stock,
                decimal(productNode, "reviewRating"),
                integer(productNode, "feedbacks"),
                "https://www.wildberries.ru/catalog/" + article + "/detail.aspx"
        ));
    }

    private static BigDecimal firstProductPrice(JsonNode productNode) {
        JsonNode sizes = productNode.path("sizes");
        if (!sizes.isArray()) {
            return null;
        }
        for (JsonNode size : sizes) {
            BigDecimal raw = decimal(size.path("price"), "product");
            if (raw != null && raw.signum() > 0) {
                return raw.divide(BigDecimal.valueOf(100));
            }
        }
        return null;
    }

    private static BigDecimal feedbackReward(JsonNode productNode) {
        BigDecimal feedbackReward = decimal(productNode, "feedbackPoints");
        if (feedbackReward == null) {
            feedbackReward = decimal(productNode, "nmFeedbacks");
        }
        if (feedbackReward == null) {
            feedbackReward = decimal(productNode, "feedbacks");
        }
        return feedbackReward;
    }

    private static Integer totalStock(JsonNode productNode) {
        JsonNode sizes = productNode.path("sizes");
        int total = 0;
        boolean hasStockNodes = false;
        if (sizes.isArray()) {
            for (JsonNode size : sizes) {
                JsonNode stocks = size.path("stocks");
                if (!stocks.isArray()) {
                    continue;
                }
                for (JsonNode stock : stocks) {
                    Integer quantity = integer(stock, "qty");
                    if (quantity != null) {
                        total += quantity;
                        hasStockNodes = true;
                    }
                }
            }
        }

        if (hasStockNodes) {
            return total;
        }

        Integer totalQuantity = integer(productNode, "totalQuantity");
        return totalQuantity == null ? 0 : totalQuantity;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longValue(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
