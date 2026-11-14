package com.example.taskapp.product;

import com.example.taskapp.common.ApiException;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ProductDtos.ProductResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(ProductDtos.ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductResponse findById(long id) {
        return ProductDtos.ProductResponse.from(requireProduct(id));
    }

    public ProductDtos.ProductResponse create(ProductDtos.ProductRequest request) {
        return ProductDtos.ProductResponse.from(repository.save(
                new Product(request.name().trim(), request.description(), request.price(), request.stock())));
    }

    public ProductDtos.ProductResponse update(long id, ProductDtos.ProductRequest request) {
        Product product = requireProduct(id);
        product.update(request.name().trim(), request.description(), request.price(), request.stock());
        return ProductDtos.ProductResponse.from(repository.save(product));
    }

    public void delete(long id) {
        repository.delete(requireProduct(id));
    }

    private Product requireProduct(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
