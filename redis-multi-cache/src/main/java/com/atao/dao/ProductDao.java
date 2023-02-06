package com.atao.dao;

import com.atao.model.Product;
import org.springframework.stereotype.Repository;

/**
 * @Description
 * @Author atao
 * @Date 2023/2/4
 */
@Repository
public class ProductDao {

    public Product create(Product product) {
        System.out.println("创建商品成功");
        return product;
    }

    public Product update(Product product) {
        System.out.println("修改商品成功");
        return product;
    }

    public Product get(Long productId) {
        System.out.println("查询商品成功");
        Product product = new Product();
        product.setId(productId);
        product.setName("test");
        return product;
    }

}

