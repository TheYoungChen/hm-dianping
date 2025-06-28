package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypeList() {
        // 方法一： stringRedisTemplate.opsForValue().get(key);
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 1.从Redis查询商铺列表缓存
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopTypeListJson)) {
            // 3.存在，直接返回
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        // 4.不存在，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        // 5.依然不存在，返回错误信息
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return Result.fail("店铺类型列表不存在！");
        }

        // 6.存在，写入Redis缓存 用于快速存取
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList));

        // 7.返回
        return Result.ok(shopTypeList);

        // 方法二：stringRedisTemplate.opsForList().range(key, 0, -1); --- 会报错！！！
        //String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        //// 1.从Redis查询商铺类型列表缓存
        //List<String> shopTypeListJson = stringRedisTemplate.opsForList().range(key, 0, -1);
        //
        //// 2.判断是否存在
        //if (shopTypeListJson != null && !shopTypeListJson.isEmpty()) {
        //    // 3.存在，直接返回
        //    List<ShopType> shopTypeList = new ArrayList<>();
        //    for (String json : shopTypeListJson) {
        //        ShopType shopType = JSONUtil.toBean(json, ShopType.class);
        //        shopTypeList.add(shopType);
        //    }
        //    return Result.ok(shopTypeList);
        //}
        //
        //// 4.不存在，查询数据库
        //List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //
        //// 5.依然不存在，返回错误信息
        //if (shopTypeList == null || shopTypeList.isEmpty()) {
        //    return Result.fail("店铺类型列表不存在！");
        //}
        //
        //// 6.存在，写入Redis
        //List<String> shopTypeListJsonToStore = new ArrayList<>();
        //for (ShopType shopType : shopTypeList) {
        //    shopTypeListJsonToStore.add(JSONUtil.toJsonStr(shopType));
        //}
        //stringRedisTemplate.opsForList().rightPushAll(key, shopTypeListJsonToStore);
        //
        //// 7.返回
        //return Result.ok(shopTypeList);
    }
}
