package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name; // 业务名称/锁的名称
    // 提前读取lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    // UUID为了区分是哪一台服务器 主要的标识还是线程id
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 上分布式锁
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁 key是"lock:order:" value是"UUID - 线程id"
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name,  threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success); // 直接返回success会由于Boolean类的拆箱可能会有空指针异常
    }

    // 释放分布式锁
    @Override
    public void unLock() {
        //
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }

    //@Override
    //public void unLock() {
    //    // 获取线程标识
    //    String threadId = ID_PREFIX + Thread.currentThread().getId();
    //    // 获取锁中的标识
    //    String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //    // 判断标识是否一致
    //    if (threadId.equals(id)) {
    //        // 释放锁
    //        stringRedisTemplate.delete(KEY_PREFIX + name);
    //    }
    //}
}
