package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询商铺信息
     */
    @Override
    public Result queryById(Long id) {
        // 调用缓存穿透方法
        //Shop shop = queryWithPassThrough(id);
        //Shop shop = cacheClient.queryWithPassThrough(
        //        RedisConstants.CACHE_SHOP_KEY,
        //        id,
        //        Shop.class,
        //        this::getById,
        //        RedisConstants.CACHE_SHOP_TTL,
        //        TimeUnit.MINUTES
        //);

        // 用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                20L,
                TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在!");
        }

        // 7.返回
        return Result.ok(shop);
    }

    // 逻辑过期
    //public Shop queryWithLogicalExpire(Long id) {
    //    String key = RedisConstants.CACHE_SHOP_KEY + id;
    //    // 1.从Redis查询商铺缓存
    //    String shopJson = stringRedisTemplate.opsForValue().get(key); // 获取到的json字符串
    //    // 2.判断Redis中缓存是否存在
    //    if (StrUtil.isBlank(shopJson)) {
    //        // 3.不存在 未命中 直接返回null
    //        return null;
    //    }
    //
    //    // 4.存在 命中缓存 把json反序列化为对象
    //    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class); // 将redis里存储的数据转为RedisData对象
    //    JSONObject data = (JSONObject) redisData.getData(); // 因为RedisData里的data字段是Object类型 所以不知道返回的是什么类型 所以拿到的实际是JSONObject类型
    //    Shop shop = JSONUtil.toBean(data, Shop.class); // 获取店铺信息 将redis里存储的对象的data部分取出来（JsonObject类型）转为Shop类型
    //    LocalDateTime expireTime = redisData.getExpireTime(); // 获取过期时间 直接获取因为RedisData里存储的是就是LocalDateTime类型
    //    // 5.判断缓存是否过期
    //    if (expireTime.isAfter(LocalDateTime.now())) {
    //        // 5.1.未过期 直接返回店铺信息
    //        return shop;
    //    }
    //    // 5.2.已过期 需要进行缓存重建
    //    // 6.重建缓存
    //    // 6.1获取互斥锁
    //    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    //    boolean isLock = tryLock(lockKey); // 获取互斥锁
    //    // 6.2.判断是否成功获取互斥锁
    //    if (isLock) {
    //        // TODO 6.3.成功 开启独立线程实现缓存重建
    //        // 获取锁成功 依然需要再次检测Redis缓存中的店铺数据是否过期（避免刚好有其他线程完成重建 刚好释放锁 刚好被当前线程获取锁）
    //        Shop cachedShop = checkCacheAgain(key);
    //        // 未过期 直接返回缓存中的店铺数据
    //        if (cachedShop != null) {
    //            return cachedShop;
    //        }
    //        // 已过期 进行缓存重建
    //        CACHE_REBUILD_EXECUTOR.submit(() -> {
    //            try {
    //                // 重建缓存
    //                this.saveShop2Redis(id, 20L); // 设定20s方便测试 实际应该设置为30min
    //            } catch (Exception e) {
    //                throw new RuntimeException(e);
    //            } finally {
    //                // 释放锁
    //                unLock(lockKey);
    //            }
    //        });
    //    }
    //
    //    // 6.4.（失败）返回过期的商铺信息 （成功）返回新的商铺信息
    //    return shop;
    //}

    // 再次检查缓存是否过期
    private Shop checkCacheAgain(String key) {
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        return null;
    }

    // 互斥锁
    //public Shop queryWithMutex(Long id) {
    //    String key = RedisConstants.CACHE_SHOP_KEY + id;
    //    // 1.从Redis查询商铺缓存
    //    String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id); // 获取到的json字符串
    //    // 2.判断是否存在
    //    if (StrUtil.isNotBlank(shopJson)) {
    //        // 3.存在，直接返回
    //        return JSONUtil.toBean(shopJson, Shop.class);
    //    }
    //    // 判断命中的是否是空值
    //    if (shopJson != null) {
    //        // 返回错误信息
    //        return null;
    //    }
    //    // 4.实现缓存重建
    //    // 4.1获取互斥锁
    //    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    //    Shop shop = null;
    //    try {
    //        boolean isLock = tryLock(lockKey);
    //        // 4.2判断是否获取锁成功
    //        if (!isLock) {
    //            // 4.3失败，休眠并重试
    //            Thread.sleep(50);
    //            return queryWithMutex(id);
    //        }
    //
    //        // 获取锁成功后再次查询Redis
    //        shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
    //        if (StrUtil.isNotBlank(shopJson)) {
    //            // 存在，直接返回
    //            return JSONUtil.toBean(shopJson, Shop.class);
    //        }
    //
    //        // 模拟重建缓存的延迟
    //        //Thread.sleep(200);
    //
    //        // 4.4成功，根据id查询数据库
    //        shop = getById(id);
    //
    //        // 5.依然不存在，返回错误信息
    //        if (shop == null) {
    //            // 将空值写入Redis
    //            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
    //            // 返回错误信息
    //            return null;
    //        }
    //
    //        // 6.存在，写入Redis
    //        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES); // 将Shop类型的JavaBean对象转换为json字符串存入Redis
    //    } catch (InterruptedException e) {
    //        throw new RuntimeException(e);
    //    } finally {
    //        // 7.释放互斥锁
    //        unLock(lockKey);
    //    }
    //
    //    // 8.返回
    //    return shop;
    //}

    // 缓存穿透
    //public Shop queryWithPassThrough(Long id) {
    //    String key = RedisConstants.CACHE_SHOP_KEY + id;
    //    // 1.从Redis查询商铺缓存
    //    String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id); // 获取到的json字符串
    //    // 2.判断是否存在
    //    if (StrUtil.isNotBlank(shopJson)) {
    //        // 3.存在，直接返回
    //        Shop shop = JSONUtil.toBean(shopJson, Shop.class);// 将获取到的json字符串转换为Shop类型的JavaBean对象
    //        return shop;
    //    }
    //    // 判断命中的是否是空值
    //    if (shopJson != null) {
    //        // 返回错误信息
    //        return null;
    //    }
    //
    //    // 4.不存在，根据id查询数据库
    //    Shop shop = getById(id);
    //
    //    // 5.依然不存在，返回错误信息
    //    if (shop == null) {
    //        // 将空值写入Redis
    //        stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
    //        // 返回错误信息
    //        return null;
    //    }
    //
    //    // 6.存在，写入Redis
    //    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES); // 将Shop类型的JavaBean对象转换为json字符串存入Redis
    //
    //    // 7.返回
    //    return shop;
    //}

    //// 加锁
    //private boolean tryLock(String key) {
    //    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    //    return BooleanUtil.isTrue(flag); // Boolean是boolean的包装类 开箱时可能为空 所以要利用工具来判断
    //}
    //
    //// 释放锁
    //private void unLock(String key) {
    //    stringRedisTemplate.delete(key);
    //}
    //
    //// 将店铺信息保存到Redis中
    //public void saveShop2Redis(Long id, Long ttl) throws InterruptedException {
    //    // 模拟重建缓存的延迟 延迟越长越容易出现线程安全问题
    //    Thread.sleep(200);
    //    // 1.查询店铺数据
    //    Shop shop = getById(id);
    //    // 2.封装逻辑过期时间
    //    RedisData redisData = new RedisData();
    //    redisData.setData(shop);
    //    redisData.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
    //    // 3.写入Redis
    //    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    //}

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        // 3.返回结果
        return Result.ok();
    }
}
