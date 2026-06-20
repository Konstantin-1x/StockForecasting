package org.example.web;

import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.repository.ProductCategoryRepository;
import org.example.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:diploma-web;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "wb.parser.continuous-scan-enabled=false",
        "wb.parser.baseline-on-startup=false",
        "wb.parser.monitoring-enabled=false",
        "wb.parser.proxy-enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("Authorization and web interface tests")
class DiplomaWebInterfaceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Protected pages redirect anonymous user to login page")
    void protectedPagesRedirectAnonymousUserToLoginPage() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        System.out.println("[REPORT] GET /admin as anonymous -> HTTP 302, redirect=/login");

        mockMvc.perform(get("/app"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        System.out.println("[REPORT] GET /app as anonymous -> HTTP 302, redirect=/login");
    }

    @Test
    @DisplayName("Administrator opens dashboard after login")
    void administratorCanOpenDashboardAfterLogin() throws Exception {
        MockHttpSession adminSession = login("admin", "admin");

        mockMvc.perform(get("/admin").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin"))
                .andExpect(model().attributeExists(
                        "sellerCount",
                        "productCount",
                        "categoryCount",
                        "forecastCount",
                        "offerCount",
                        "promotionCount",
                        "userCount"
                ));

        mockMvc.perform(get("/admin/diagnostics").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("diagnostics"))
                .andExpect(model().attributeExists(
                        "diagnostics",
                        "diagnosticAlerts",
                        "dataCollectionStatus"
                ));
        System.out.println("[REPORT] Login admin/admin -> authenticated");
        System.out.println("[REPORT] GET /admin as administrator -> HTTP 200, view=admin, dashboard metrics loaded");
        System.out.println("[REPORT] GET /admin/diagnostics as administrator -> HTTP 200, service controls loaded");
    }

    @Test
    @DisplayName("Seller adds product, opens forecast page and starts calculation")
    void sellerCanAddProductOpenForecastPageAndRunForecastRequest() throws Exception {
        ProductCategory category = ensureReportCategory();
        MockHttpSession sellerSession = login("seller_demo", "seller123");

        mockMvc.perform(get("/app/products").session(sellerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("user-products"))
                .andExpect(model().attributeExists("seller", "products"));
        System.out.println("[REPORT] Login seller_demo/seller123 -> authenticated");
        System.out.println("[REPORT] GET /app/products as seller -> HTTP 200, view=user-products");

        String marketplaceArticle = "REPORT-" + UUID.randomUUID();
        mockMvc.perform(post("/app/products")
                        .session(sellerSession)
                        .with(csrf())
                        .param("categoryId", category.getId().toString())
                        .param("marketplaceArticle", marketplaceArticle)
                        .param("name", "Report test product")
                        .param("description", "Product created by diploma web interface test")
                        .param("basePrice", "990")
                        .param("currentStock", "35"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app"));

        Product product = productRepository.findByMarketplaceArticle(marketplaceArticle).orElseThrow();
        System.out.println("[REPORT] POST /app/products -> HTTP 302, created product: article=" + marketplaceArticle
                + ", productId=" + product.getId() + ", stock=" + product.getCurrentStock());
        assertThat(product.getName()).isEqualTo("Report test product");
        assertThat(product.getSeller()).isNotNull();

        mockMvc.perform(get("/app/forecasts")
                        .session(sellerSession)
                        .param("productId", product.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("user-forecasts"))
                .andExpect(model().attributeExists("seller", "products", "forecasts", "forecastRequestForm"));
        System.out.println("[REPORT] GET /app/forecasts?productId=" + product.getId()
                + " -> HTTP 200, forecast form contains selected product");

        mockMvc.perform(post("/app/forecasts/recalculate")
                        .session(sellerSession)
                        .with(csrf())
                        .param("productId", product.getId().toString())
                        .param("promotionBudget", "5000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/forecasts"));
        System.out.println("[REPORT] POST /app/forecasts/recalculate -> HTTP 302, budget=5000, forecast request processed");
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(SecurityMockMvcRequestBuilders.formLogin()
                        .user(username)
                        .password(password))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(username))
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private ProductCategory ensureReportCategory() {
        return categoryRepository.findByExternalUrl("https://www.wildberries.ru/promotions/rubli-za-otzyvy/report-tests")
                .orElseGet(() -> {
                    ProductCategory category = new ProductCategory();
                    category.setName("Report tests");
                    category.setExternalUrl("https://www.wildberries.ru/promotions/rubli-za-otzyvy/report-tests");
                    category.setWbShardKey("catalog");
                    category.setWbQuery("cat=1");
                    return categoryRepository.save(category);
                });
    }
}
