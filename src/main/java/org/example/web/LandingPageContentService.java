package org.example.web;

import org.example.domain.LandingPageContent;
import org.example.repository.LandingPageContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LandingPageContentService {

    private final LandingPageContentRepository contentRepository;

    public LandingPageContentService(LandingPageContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Transactional
    public LandingPageContent currentContent() {
        return contentRepository.findById(LandingPageContent.SINGLETON_ID)
                .orElseGet(() -> contentRepository.save(defaultContent()));
    }

    @Transactional
    public LandingPageForm toForm() {
        return toForm(currentContent());
    }

    @Transactional
    public void update(LandingPageForm form) {
        LandingPageContent content = currentContent();
        content.setKicker(form.getKicker().trim());
        content.setHeadline(form.getHeadline().trim());
        content.setDescription(form.getDescription().trim());
        content.setModelLabel(form.getModelLabel().trim());
        content.setDataSliceLabel(form.getDataSliceLabel().trim());
        content.setSourceLabel(form.getSourceLabel().trim());
        content.setSellerTitle(form.getSellerTitle().trim());
        content.setSellerDescription(form.getSellerDescription().trim());
        content.setAdminTitle(form.getAdminTitle().trim());
        content.setAdminDescription(form.getAdminDescription().trim());
        content.setResearchTitle(form.getResearchTitle().trim());
        content.setResearchDescription(form.getResearchDescription().trim());
        contentRepository.save(content);
    }

    private static LandingPageForm toForm(LandingPageContent content) {
        LandingPageForm form = new LandingPageForm();
        form.setKicker(content.getKicker());
        form.setHeadline(content.getHeadline());
        form.setDescription(content.getDescription());
        form.setModelLabel(content.getModelLabel());
        form.setDataSliceLabel(content.getDataSliceLabel());
        form.setSourceLabel(content.getSourceLabel());
        form.setSellerTitle(content.getSellerTitle());
        form.setSellerDescription(content.getSellerDescription());
        form.setAdminTitle(content.getAdminTitle());
        form.setAdminDescription(content.getAdminDescription());
        form.setResearchTitle(content.getResearchTitle());
        form.setResearchDescription(content.getResearchDescription());
        return form;
    }

    private static LandingPageContent defaultContent() {
        LandingPageContent content = new LandingPageContent();
        content.setId(LandingPageContent.SINGLETON_ID);
        content.setKicker("ВКР · нейросетевое прогнозирование");
        content.setHeadline("Промо Прогноз для продавцов Wildberries");
        content.setDescription("Система собирает почасовые временные ряды товаров, хранит историю акционных показателей и строит прогноз будущей цены, времени до старта акции и остатка товара.");
        content.setModelLabel("Промо сценарий");
        content.setDataSliceLabel("1 час");
        content.setSourceLabel("Wildberries");
        content.setSellerTitle("Для продавца");
        content.setSellerDescription("Регистрация создает профиль продавца, к которому затем привязываются товары и прогнозы.");
        content.setAdminTitle("Для администратора");
        content.setAdminDescription("Админ видит пользователей, карточки продавцов, товары, прогнозы и качество данных.");
        content.setResearchTitle("Для исследования");
        content.setResearchDescription("История строится как временной ряд, чтобы модель обучалась на динамике цены, остатка и акции.");
        return content;
    }
}
