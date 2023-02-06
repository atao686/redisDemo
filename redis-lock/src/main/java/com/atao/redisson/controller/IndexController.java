package com.atao.redisson.controller;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Description
 * @Author atao
 */
@RestController
public class IndexController {

    @Autowired
    private Redisson redisson;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping("/deduct_stock")
    public String deductStock() {

        String lockKey = "lock:product_101";
        //获取锁对象
        RLock redissonLock = redisson.getLock(lockKey);
        //加分布式锁
        redissonLock.lock();  //  .setIfAbsent(lockKey, clientId, 30, TimeUnit.SECONDS); {
        try {
            int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get("stock"));
            if (stock > 0) {
                int realStock = stock - 1;
                stringRedisTemplate.opsForValue().set("stock", realStock + "");
                System.out.println("扣减成功，剩余库存:" + realStock);
            } else {
                System.out.println("扣减失败，库存不足");
            }
        } finally {
        //解锁
        redissonLock.unlock();
        }

        return "end";
    }

}
