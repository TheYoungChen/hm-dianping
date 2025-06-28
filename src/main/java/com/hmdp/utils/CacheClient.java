package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 设置普通缓存
    private void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 缓存穿透解决缓存击穿
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class <R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从Redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key); // 获取到的json字符串
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) { // 检查 json 是否不为空(null)且不是空白字符串("")
            // 3.存在，直接返回
            return JSONUtil.toBean(json, type);// 将获取到的json字符串转换为需要的类型
        }
        // 判断命中的缓存是否是空字符串("")如果是 说明之前已经确认过数据不在数据库中 且存储了空值 则直接返回null
        // 如果为null 则说明Redis中没有该键（缓存未命中）
        if (json != null) { // 不是null 而是空字符串
            // 返回错误信息 （说明之前已经确认过数据不在数据库中 且存储了空值 则直接返回null）
            return null;
        }

        // 4.缓存中不存在（查询的值为空 根本没有），根据id查询数据库
        R r = dbFallback.apply(id);

        // 5.依然不存在（缓存、数据库都没有），返回错误信息
        if (r == null) {
            // 如果数据库中也没有找到该数据，将空值 ("") 写入 Redis 并设置较短的 TTL（例如几分钟），以避免后续请求再次查询数据库
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }

        // 6.存在，写入Redis（如果数据库中有数据，将其写入 Redis 并返回）
        this.set(key, r, time, unit);
        // 7.返回
        return r;
    }

    // 逻辑过期解决缓存击穿
    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 逻辑过期
    //public <R> R queryWithLogicalExpire(String keyPrefix, Long id, Class<R> type, Function<Long, R> dbFallback, Long time, TimeUnit unit) {
    //    String key = keyPrefix + id;
    //    // 1.从Redis查询商铺缓存
    //    String json = stringRedisTemplate.opsForValue().get(key); // 获取到的json字符串
    //    // 2.判断Redis中缓存是否存在
    //    if (StrUtil.isBlank(json)) {
    //        // 3.不存在 未命中 直接返回null
    //        return null;
    //    }
    //
    //    // 4.存在 命中缓存 把json反序列化为对象
    //    RedisData redisData = JSONUtil.toBean(json, RedisData.class); // 将redis里存储的数据转为RedisData对象
    //    JSONObject data = (JSONObject) redisData.getData(); // 因为RedisData里的data字段是Object类型 所以不知道返回的是什么类型 所以拿到的实际是JSONObject类型
    //    R r = JSONUtil.toBean(data, type); // 获取店铺信息 将redis里存储的对象的data部分取出来（JsonObject类型）转为Shop类型
    //    LocalDateTime expireTime = redisData.getExpireTime(); // 获取过期时间 直接获取因为RedisData里存储的是就是LocalDateTime类型
    //    // 5.判断缓存是否过期
    //    if (expireTime.isAfter(LocalDateTime.now())) {
    //        // 5.1.未过期 直接返回店铺信息
    //        return r;
    //    }
    //    // 5.2.已过期 需要进行缓存重建
    //    // 6.重建缓存
    //    // 6.1获取互斥锁
    //    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    //    boolean isLock = tryLock(lockKey); // 获取互斥锁
    //    // 6.2.判断是否成功获取互斥锁
    //    if (isLock) {
    //        // TODO 6.3.成功 开启独立线程实现缓存重建
    //        // 已过期 进行缓存重建
    //        CACHE_REBUILD_EXECUTOR.submit(() -> {
    //            try {
    //                // 重建缓存
    //                // 查询数据库
    //                R r1 = dbFallback.apply(id);
    //                // 写入Redis
    //                this.setWithLogicalExpire(key, r1, time, unit); // 设定20s方便测试 实际应该设置为30min
    //            } catch (Exception e) {
    //                throw new RuntimeException(e);
    //            } finally {
    //                // 释放锁
    //                unLock(lockKey);
    //            }
    //        });
    //    }
    //    // 6.4.（失败）返回过期的商铺信息 （成功）返回新的商铺信息
    //    return r;
    //}

    // 逻辑过期优化版
    public <R> R queryWithLogicalExpire(String keyPrefix, Long id, Class<R> type, Function<Long, R> dbFallback, Long time, TimeUnit unit) {
    String key = keyPrefix + id;
    // 1.从Redis查询商铺缓存
    String json = stringRedisTemplate.opsForValue().get(key); // 获取到的json字符串
    // 2.判断Redis中缓存是否存在
    if (StrUtil.isBlank(json)) {
        // 3.缓存未命中 查询数据库
        R shop = dbFallback.apply(id);
        if (shop != null) {
            // 4.数据库中存在数据 写入Redis 并设置逻辑过期时间
            setWithLogicalExpire(key, shop, time, unit);
            return shop;
        } else {
            // 5.数据库中不存在数据 将空值写入Redis 并设置较短的过期时间
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
    }

    // 6.存在 命中缓存 把json反序列化为对象
    RedisData redisData = JSONUtil.toBean(json, RedisData.class); // 将redis里存储的数据转为RedisData对象
    JSONObject data = (JSONObject) redisData.getData(); // 因为RedisData里的data字段是Object类型 所以不知道返回的是什么类型 所以拿到的实际是JSONObject类型
    R r = JSONUtil.toBean(data, type); // 获取店铺信息 将redis里存储的对象的data部分取出来（JsonObject类型）转为Shop类型
    LocalDateTime expireTime = redisData.getExpireTime(); // 获取过期时间 直接获取因为RedisData里存储的是就是LocalDateTime类型
    // 7.判断缓存是否过期
    if (expireTime.isAfter(LocalDateTime.now())) {
        // 8.未过期 直接返回店铺信息
        return r;
    } else {
        // 9.已过期 需要进行缓存重建
        // 9.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey); // 获取互斥锁
        // 9.2.判断是否成功获取互斥锁
        if (isLock) {
            try {
                // 9.3.成功 开启独立线程实现缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 重建缓存
                        // 查询数据库
                        R r1 = dbFallback.apply(id);
                        // 写入Redis
                        if (r1 != null) {
                            setWithLogicalExpire(key, r1, time, unit); // 设定20s方便测试 实际应该设置为30min
                        } else {
                            // 数据库中不存在数据 将空值写入Redis 并设置较短的过期时间
                            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        // 释放锁
                        unLock(lockKey);
                    }
                });
            } finally {
                // 确保锁释放
                unLock(lockKey);
            }
        }
        // 9.4.（失败）返回过期的商铺信息 （成功）返回新的商铺信息
        return r;
    }
}


    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); // Boolean是boolean的包装类 开箱时可能为空 所以要利用工具来判断
    }

    // 释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
