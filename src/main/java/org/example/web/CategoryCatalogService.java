package org.example.web;

import org.example.domain.ProductCategory;
import org.example.repository.ProductCategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CategoryCatalogService {

    private static final String PROMO_ROOT = "https://www.wildberries.ru/promotions/rubli-za-otzyvy";
    private static final Map<String, String> SLUG_NAMES = Map.ofEntries(
            Map.entry("zhenshchinam", "Женщинам"),
            Map.entry("muzhchinam", "Мужчинам"),
            Map.entry("detyam", "Детям"),
            Map.entry("dom", "Дом"),
            Map.entry("krasota", "Красота"),
            Map.entry("aksessuary", "Аксессуары"),
            Map.entry("akssesuary", "Аксессуары"),
            Map.entry("asksseuary", "Аксессуары"),
            Map.entry("elektronika", "Электроника"),
            Map.entry("igrushki", "Игрушки"),
            Map.entry("mebel", "Мебель"),
            Map.entry("produkty", "Продукты"),
            Map.entry("produkty-pitaniya", "Продукты питания"),
            Map.entry("tsvety", "Цветы"),
            Map.entry("bytovaya-tehnika", "Бытовая техника"),
            Map.entry("tovary-dlya-zhivotnyh", "Зоотовары"),
            Map.entry("zootovary", "Зоотовары"),
            Map.entry("sport", "Спорт"),
            Map.entry("avtotovary", "Автотовары"),
            Map.entry("obuv", "Обувь"),
            Map.entry("knigi", "Книги"),
            Map.entry("knigi-i-diski", "Книги и диски"),
            Map.entry("knigi-i-kantstovary", "Книги и канцтовары"),
            Map.entry("yuvelirnye-ukrasheniya", "Ювелирные изделия"),
            Map.entry("dlya-remonta", "Для ремонта"),
            Map.entry("tovary-dlya-remonta", "Товары для ремонта"),
            Map.entry("dom-i-dacha", "Дом и дача"),
            Map.entry("sad-i-dacha", "Сад и дача"),
            Map.entry("zdorove", "Здоровье"),
            Map.entry("adaptivnye-tovary", "Адаптивные товары"),
            Map.entry("kantstovary", "Канцтовары"),
            Map.entry("pitanie", "Питание"),
            Map.entry("tovary-dlya-sobak", "Товары для собак"),
            Map.entry("tovary-dlya-vzroslyh", "Товары для взрослых"),
            Map.entry("budushchie-mamy", "Будущие мамы"),
            Map.entry("odezhda", "Одежда"),
            Map.entry("obuv-i-aksessuary", "Обувь и аксессуары"),
            Map.entry("smartfony-i-telefony", "Смартфоны и телефоны"),
            Map.entry("kompyutery", "Компьютеры"),
            Map.entry("televizory-i-audio", "Телевизоры и аудио"),
            Map.entry("kuhnya", "Кухня"),
            Map.entry("dlya-doma", "Для дома"),
            Map.entry("dosug-i-tvorchestvo", "Досуг и творчество"),
            Map.entry("dachniy-sezon", "Дачный сезон"),
            Map.entry("lekarstvennye-preparaty", "Лекарственные препараты"),
            Map.entry("sdelano-v-rossii", "Сделано в России"),
            Map.entry("svadba", "Свадьба"),
            Map.entry("transportnye-sredstva", "Транспортные средства"),
            Map.entry("yuvelirnye-izdeliya", "Ювелирные изделия")
    );

    private final ProductCategoryRepository categoryRepository;

    public CategoryCatalogService(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public CategoryCatalogView buildCatalog(String selectedCategoryKey) {
        CategoryTree tree = buildTree();
        String normalizedSelectedKey = normalizeKey(selectedCategoryKey);
        CategoryNode selectedNode = normalizedSelectedKey == null ? null : tree.nodesByKey().get(normalizedSelectedKey);
        CategoryNode selectedRoot = selectedNode == null ? null : selectedNode.root();

        List<CategoryCatalogItem> rootItems = tree.roots().stream()
                .map(root -> toRootItem(root, selectedNode))
                .toList();

        return new CategoryCatalogView(
                rootItems,
                selectedNode == null ? null : selectedNode.key(),
                selectedRoot == null ? null : selectedRoot.key(),
                selectedNode == null ? null : selectedNode.name()
        );
    }

    @Transactional(readOnly = true)
    public String selectedCategoryUrlPrefix(String selectedCategoryKey) {
        String normalizedSelectedKey = normalizeKey(selectedCategoryKey);
        if (normalizedSelectedKey == null) {
            return null;
        }
        CategoryTree tree = buildTree();
        return tree.nodesByKey().containsKey(normalizedSelectedKey) ? normalizedSelectedKey : null;
    }

    @Transactional(readOnly = true)
    public Optional<ProductCategory> selectedProductCategory(String selectedCategoryKey) {
        String normalizedSelectedKey = normalizeKey(selectedCategoryKey);
        if (normalizedSelectedKey == null) {
            return Optional.empty();
        }
        return categoryRepository.findAll().stream()
                .filter(category -> normalizedSelectedKey.equals(normalizeKey(category.getExternalUrl())))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<String> categoryKeyForId(Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return categoryRepository.findById(categoryId)
                .map(ProductCategory::getExternalUrl)
                .map(CategoryCatalogService::normalizeKey);
    }

    private CategoryTree buildTree() {
        List<ProductCategory> categories = categoryRepository.findAll(Sort.by("name"));
        Map<String, CategoryNode> nodesByKey = new LinkedHashMap<>();

        for (ProductCategory category : categories) {
            List<String> segments = categorySegments(category.getExternalUrl());
            if (segments.isEmpty()) {
                continue;
            }

            CategoryNode parent = null;
            StringBuilder keyBuilder = new StringBuilder(PROMO_ROOT);
            for (int index = 0; index < segments.size(); index++) {
                String segment = segments.get(index);
                keyBuilder.append('/').append(segment);
                String key = keyBuilder.toString();
                boolean leaf = index == segments.size() - 1;
                String name = leaf ? category.getName() : displayName(segment);

                CategoryNode node = nodesByKey.computeIfAbsent(key, ignored -> new CategoryNode(key, name));
                if (leaf) {
                    node.name(name);
                    node.categoryId(category.getId());
                }
                if (parent != null) {
                    parent.addChild(node);
                    node.parent(parent);
                }
                parent = node;
            }
        }

        List<CategoryNode> roots = nodesByKey.values().stream()
                .filter(node -> node.parent() == null)
                .sorted(CategoryNode.BY_NAME)
                .toList();
        nodesByKey.values().forEach(CategoryNode::sortChildren);
        return new CategoryTree(roots, nodesByKey);
    }

    private CategoryCatalogItem toRootItem(CategoryNode root, CategoryNode selectedNode) {
        List<CategoryCatalogItem> children = new ArrayList<>();
        for (CategoryNode child : root.children()) {
            flatten(child, 0, selectedNode, children);
        }
        return toItem(root, 0, selectedNode, true, children);
    }

    private void flatten(CategoryNode node,
                         int depth,
                         CategoryNode selectedNode,
                         List<CategoryCatalogItem> result) {
        result.add(toItem(node, depth, selectedNode, false, List.of()));
        for (CategoryNode child : node.children()) {
            flatten(child, depth + 1, selectedNode, result);
        }
    }

    private CategoryCatalogItem toItem(CategoryNode node,
                                       int depth,
                                       CategoryNode selectedNode,
                                       boolean rootItem,
                                       List<CategoryCatalogItem> children) {
        boolean selected = selectedNode != null && node.key().equals(selectedNode.key());
        boolean activeTrail = selectedNode != null && selectedNode.key().startsWith(node.key() + "/");
        return new CategoryCatalogItem(
                node.key(),
                node.categoryId(),
                node.name(),
                depth,
                selected,
                activeTrail,
                rootItem ? !children.isEmpty() : node.hasChildren(),
                children
        );
    }

    private static List<String> categorySegments(String externalUrl) {
        String normalized = normalizeKey(externalUrl);
        if (normalized == null || !normalized.startsWith(PROMO_ROOT + "/")) {
            return List.of();
        }
        String relativePath = normalized.substring((PROMO_ROOT + "/").length());
        if (relativePath.isBlank()) {
            return List.of();
        }
        return List.of(relativePath.split("/"));
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        try {
            URI uri = URI.create(normalized);
            if (uri.getScheme() != null && uri.getHost() != null) {
                normalized = uri.getScheme().toLowerCase(Locale.ROOT)
                        + "://"
                        + uri.getHost().toLowerCase(Locale.ROOT)
                        + (uri.getPath() == null ? "" : uri.getPath());
            }
        } catch (IllegalArgumentException ignored) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String displayName(String slug) {
        String mapped = SLUG_NAMES.get(slug);
        if (mapped != null) {
            return mapped;
        }
        String decoded = URLDecoder.decode(slug, StandardCharsets.UTF_8)
                .replace('-', ' ')
                .replace('_', ' ')
                .trim();
        if (decoded.isBlank()) {
            return slug;
        }
        return decoded.substring(0, 1).toUpperCase(Locale.ROOT) + decoded.substring(1);
    }

    private record CategoryTree(List<CategoryNode> roots, Map<String, CategoryNode> nodesByKey) {
    }

    private static final class CategoryNode {
        private static final Comparator<CategoryNode> BY_NAME =
                Comparator.comparing(CategoryNode::name, String.CASE_INSENSITIVE_ORDER);

        private final String key;
        private final List<CategoryNode> children = new ArrayList<>();
        private final Map<String, CategoryNode> childKeys = new HashMap<>();
        private String name;
        private Long categoryId;
        private CategoryNode parent;

        private CategoryNode(String key, String name) {
            this.key = key;
            this.name = name;
        }

        String key() {
            return key;
        }

        String name() {
            return name;
        }

        void name(String name) {
            this.name = name;
        }

        Long categoryId() {
            return categoryId;
        }

        void categoryId(Long categoryId) {
            this.categoryId = categoryId;
        }

        CategoryNode parent() {
            return parent;
        }

        void parent(CategoryNode parent) {
            this.parent = parent;
        }

        List<CategoryNode> children() {
            return children;
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }

        void addChild(CategoryNode child) {
            if (childKeys.putIfAbsent(child.key(), child) == null) {
                children.add(child);
            }
        }

        void sortChildren() {
            children.sort(BY_NAME);
        }

        CategoryNode root() {
            CategoryNode current = this;
            while (current.parent != null) {
                current = current.parent;
            }
            return current;
        }
    }
}
