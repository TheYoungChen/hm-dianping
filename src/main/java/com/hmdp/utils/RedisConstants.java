package com.hmdp.utils;

// Redis相关配置的常量定义
public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:"; // 登录验证码的Redis key 后面拼接了手机号
    public static final Long LOGIN_CODE_TTL = 2L; // 验证码的过期时间
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 1000L;

    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:"; // 缓存店铺类型的Redis key

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
