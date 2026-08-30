package org.example.web;

import org.example.domain.ProductCategory;
import org.example.repository.ProductCategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
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
    public List<Long> selectedCategoryIds(String selectedCategoryKey) {
        String normalizedSelectedKey = normalizeKey(selectedCategoryKey);
        if (normalizedSelectedKey == null) {
            return List.of();
        }
        CategoryTree tree = buildTree();
        CategoryNode selectedNode = tree.nodesByKey().get(normalizedSelectedKey);
        if (selectedNode == null) {
            return List.of();
        }
        List<Long> categoryIds = new ArrayList<>();
        selectedNode.collectCategoryIds(categoryIds);
        return categoryIds;
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
        Map<Long, CategoryNode> nodesById = new HashMap<>();
        Map<String, CategoryNode> nodesByKey = new LinkedHashMap<>();

        for (ProductCategory category : categories) {
            String key = normalizeKey(category.getExternalUrl());
            if (key == null) {
                continue;
            }

            CategoryNode node = new CategoryNode(key, category.getName());
            node.categoryId(category.getId());
            nodesById.put(category.getId(), node);
            nodesByKey.put(key, node);
        }

        for (ProductCategory category : categories) {
            CategoryNode node = nodesById.get(category.getId());
            ProductCategory parentCategory = category.getParentCategory();
            if (node == null || parentCategory == null) {
                continue;
            }

            CategoryNode parent = nodesById.get(parentCategory.getId());
            if (parent != null && parent != node) {
                parent.addChild(node);
                node.parent(parent);
            }
        }

        List<CategoryNode> roots = nodesById.values().stream()
                .filter(node -> node.parent() == null)
                .sorted(CategoryNode.BY_NAME)
                .toList();
        nodesById.values().forEach(CategoryNode::sortChildren);
        return new CategoryTree(roots, nodesByKey);
    }

    private CategoryCatalogItem toRootItem(CategoryNode root, CategoryNode selectedNode) {
        return toItem(root, 0, selectedNode);
    }

    private CategoryCatalogItem toItem(CategoryNode node, int depth, CategoryNode selectedNode) {
        List<CategoryCatalogItem> children = node.children().stream()
                .map(child -> toItem(child, depth + 1, selectedNode))
                .toList();
        boolean selected = selectedNode != null && node.key().equals(selectedNode.key());
        boolean activeTrail = selectedNode != null && selectedNode.isDescendantOf(node);
        return new CategoryCatalogItem(
                node.key(),
                node.categoryId(),
                node.name(),
                depth,
                selected,
                activeTrail,
                !children.isEmpty(),
                children
        );
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

        void addChild(CategoryNode child) {
            if (childKeys.putIfAbsent(child.key(), child) == null) {
                children.add(child);
            }
        }

        void sortChildren() {
            children.sort(BY_NAME);
        }

        void collectCategoryIds(List<Long> categoryIds) {
            if (categoryId != null) {
                categoryIds.add(categoryId);
            }
            children.forEach(child -> child.collectCategoryIds(categoryIds));
        }

        boolean isDescendantOf(CategoryNode possibleAncestor) {
            CategoryNode current = parent;
            while (current != null) {
                if (current == possibleAncestor) {
                    return true;
                }
                current = current.parent;
            }
            return false;
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
