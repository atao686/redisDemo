package com.atao.controller;

import com.atao.model.Product;
import com.atao.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Description
 * @Author atao
 * @Date 2023/2/4
 */
@RestController("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public Product create(@RequestBody Product productParam) {
        return productService.create(productParam);
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public Product update(@RequestBody Product productParam) {
        return productService.update(productParam);
    }

    @RequestMapping("/get/{productId}")
    public Product getProduct(@PathVariable Long productId) {
        return productService.get(productId);
    }

}
