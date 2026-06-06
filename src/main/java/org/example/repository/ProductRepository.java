package org.example.repository;

import org.example.domain.Product;
import org.example.domain.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByMarketplaceArticle(String marketplaceArticle);

    List<Product> findBySellerOrderByIdDesc(Seller seller);

    long countBySeller(Seller seller);
}
