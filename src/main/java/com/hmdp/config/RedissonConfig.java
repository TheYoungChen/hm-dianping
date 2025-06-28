package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        //1.创建配置
        Config config = new Config();
        //config.useSingleServer().setAddress("redis://192.168.246.129:6379").setPassword("yangchen2004");
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");

        //2.创建RedissonClient对象
        return Redisson.create(config);
    }

}
