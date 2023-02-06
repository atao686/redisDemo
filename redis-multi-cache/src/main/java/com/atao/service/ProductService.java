package com.atao.service;

import com.alibaba.fastjson.JSON;
import com.atao.common.RedisKeyPrefixConst;
import com.atao.common.RedisUtil;
import com.atao.dao.ProductDao;
import com.atao.model.Product;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @Description
 * @Author atao
 * @Date 2023/2/4
 */
@Service
public class ProductService {

    @Autowired
    private ProductDao productDao;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private Redisson redisson;

    public static final Integer PRODUCT_CACHE_TIMEOUT = 60 * 60 * 24;
    public static final String EMPTY_CACHE = "{}";
    public static final String LOCK_PRODUCT_HOT_CACHE_CREATE_PREFIX = "lock:product:hot_cache_create:";
    public static final String LOCK_PRODUCT_UPDATE_PREFIX = "lock:product:update:";
    public static Map<String, Product> productMap = new HashMap<>();

    @Transactional
    public Product create(Product product) {
        Product productResult = productDao.create(product);
        redisUtil.set(RedisKeyPrefixConst.PRODUCT_CACHE + productResult.getId(), JSON.toJSONString(productResult),
                PRODUCT_CACHE_TIMEOUT, TimeUnit.SECONDS);
        return productResult;
    }

    @Transactional
    public Product update(Product product) {
        Product productResult = null;
        // 加分布式读锁解决缓存双写不一致问题
//        RLock productUpdateLock = redisson.getLock(LOCK_PRODUCT_UPDATE_PREFIX + product.getId());
//        productUpdateLock.lock();
        //RLock productUpdateLock = redisson.getLock(LOCK_PRODUCT_UPDATE_PREFIX + product.getId());
        RReadWriteLock productUpdateLock = redisson.getReadWriteLock(LOCK_PRODUCT_UPDATE_PREFIX + product.getId());
        RLock writeLock = productUpdateLock.writeLock();
        writeLock.lock();
        try {
            productResult = productDao.update(product);
            redisUtil.set(RedisKeyPrefixConst.PRODUCT_CACHE + productResult.getId(), JSON.toJSONString(productResult),
                    genProductCacheTimeout(), TimeUnit.SECONDS);
        } finally {
//            productUpdateLock.unlock();
            writeLock.unlock();
        }
        return productResult;
    }

    public Product get(Long productId) {
        Product product = null;
        String productCacheKey = RedisKeyPrefixConst.PRODUCT_CACHE + productId;

        // 从缓存里查数据
        product = getProductFromCache(productCacheKey);
        if (product != null) {
            return product;
        }

        // 加分布式锁解决热点缓存并发重建问题
        RLock hotCreateCacheLock = redisson.getLock(LOCK_PRODUCT_HOT_CACHE_CREATE_PREFIX + productId);
        hotCreateCacheLock.lock();
        try {
            // 从缓存里查数据
            product = getProductFromCache(productCacheKey);
            if (product != null) {
                return product;
            }

            // 加分布式读锁解决缓存双写不一致问题
//            RLock productUpdateLock = redisson.getLock(LOCK_PRODUCT_UPDATE_PREFIX + productId);
//            productUpdateLock.lock();
            RReadWriteLock productUpdateLock = redisson.getReadWriteLock(LOCK_PRODUCT_UPDATE_PREFIX + productId);
            RLock rLock = productUpdateLock.readLock();
            rLock.lock();
            try {
                product = productDao.get(productId);
                if (product != null) {
                    redisUtil.set(productCacheKey, JSON.toJSONString(product), PRODUCT_CACHE_TIMEOUT, TimeUnit.SECONDS);
                } else {
                    // 设置空缓存解决缓存穿透问题
                    redisUtil.set(productCacheKey, EMPTY_CACHE, genEmptyCacheTimeout(), TimeUnit.SECONDS);
                }
            } finally {
//                productUpdateLock.unlock();
                rLock.unlock();
            }
        } finally {
            hotCreateCacheLock.unlock();
        }

        return product;
    }


    private Integer genProductCacheTimeout() {
        //加随机超时机制解决缓存批量失效(击穿)问题
        return PRODUCT_CACHE_TIMEOUT + new Random().nextInt(5) * 60 * 60;
    }

    private Integer genEmptyCacheTimeout() {
        return 60 + new Random().nextInt(30);
    }

    private Product getProductFromCache(String productCacheKey) {
        Product product = null;
        //多级缓存查询，jvm级别缓存可以交给单独的热点缓存系统统一维护，有变动推送到各个web应用系统自行更新
        product = productMap.get(productCacheKey);
        if (product != null) {
            return product;
        }
        String productStr = redisUtil.get(productCacheKey);
        if (!StringUtils.isEmpty(productStr)) {
            if (EMPTY_CACHE.equals(productStr)) {
                redisUtil.expire(productCacheKey, genEmptyCacheTimeout(), TimeUnit.SECONDS);
                return new Product();
            }
            product = JSON.parseObject(productStr, Product.class);
            //缓存读延期
            redisUtil.expire(productCacheKey, genProductCacheTimeout(), TimeUnit.SECONDS);
        }
        return product;
    }

}
