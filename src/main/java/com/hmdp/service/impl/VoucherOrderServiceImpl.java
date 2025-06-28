package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // 提前读取秒杀优惠券的lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill_old.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct // 容器初始化完成之后执行 在用户秒杀前执行
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 线程任务
    // 创建一个阻塞队列 阻塞队列：当一个线程获取不到数据时，会阻塞，直到有新的数据被添加到队列中，才会继续执行。
    //private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //private class VoucherOrderHandler implements Runnable {
    //
    //    @Override
    //    public void run() {
    //        while (true) {
    //            try {
    //                // 1.获取队列中的订单信息
    //                // take():获取和删除队列的头部 如果需要则等待直到元素可用
    //                VoucherOrder voucherOrder = orderTasks.take();
    //
    //                // 2.创建订单
    //                handleVoucherOrder(voucherOrder);
    //            } catch (Exception e) {
    //                log.error("处理订单异常!", e);
    //            }
    //        }
    //    }
    //}

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROOUP GROUP 组名g1 消费者名c1 COUNT 数量 BLOCK 阻塞时间 STREAMS 流名 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        // 2.1.如果获取失败 说明没有消息 继续下一次循环
                        continue;
                    }
                    // 2.2.解析消息中的订单消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 2.3.如果获取成功 可以下单
                    handleVoucherOrder(voucherOrder);

                    // 3.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常!", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pengding-list队列中的订单信息 XREADGROOUP GROUP 组名g1 消费者名c1 COUNT 数量 STREAMS 流名 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        // 2.1.如果获取失败 说明pengding-list没有消息 继续下一次循环
                        break;
                    }
                    // 2.2.解析消息中的订单消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 2.3.如果获取成功 可以下单
                    handleVoucherOrder(voucherOrder);

                    // 3.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理pengding-list订单异常!", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    // 处理订单
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.获取用户id（不能从UserHolder（ThreadLocal）里取 因为现在是子线程 只能从VoucherOrder里面取）
        Long userId = voucherOrder.getUserId();
        // 2.创建分布式锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3.获取锁
        boolean isLock = lock.tryLock();
        // 4.判断获取锁是否成功（兜底）
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试（理论上不会发生）
            log.error("不允许重复下单!");
            return;
        }

        // 获取当前对象的代理对象 便于执行事务
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    // 代理对象（成员变量）
    private IVoucherOrderService proxy;

    // （主线程）秒杀逻辑之消息队列再优化版本
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        // 2.判断结果是否为0（0为没购买过 可以购买 1为库存不足 2为已经购买过）
        int r = result.intValue();
        if (r != 0) {
            // 2.1.不为0 说明已经购买过 无购买资格
            return Result.fail(r == 1 ? "库存不足!" : "禁止重复下单!");
        }
        // 3.获取代理对象（初始化）
        proxy = (IVoucherOrderService) AopContext.currentProxy();


        // 4.返回订单id
        return Result.ok(orderId);
    }

    // ---------------------------------------------------------------------------------------

    // （主线程）优化后的秒杀处理逻辑（lua+分布式锁）
    //@Override
    //public Result seckillVoucher(Long voucherId) {
    //    // 获取用户
    //    Long userId = UserHolder.getUser().getId();
    //    // 1.执行lua脚本
    //    Long result = stringRedisTemplate.execute(
    //            SECKILL_SCRIPT,
    //            Collections.emptyList(),
    //            voucherId.toString(),
    //            userId.toString()
    //    );
    //    // 2.判断结果是否为0（0为没购买过 可以购买 1为库存不足 2为已经购买过）
    //    int r = result.intValue();
    //    if (r != 0) {
    //        // 2.1.不为0 说明已经购买过 无购买资格
    //        return Result.fail(r == 1 ? "库存不足!" : "禁止重复下单!");
    //    }
    //    // 2.2.为0 说明有购买资格 把下单信息保存到阻塞队列
    //    // TODO 保存阻塞队列
    //    VoucherOrder voucherOrder = new VoucherOrder();
    //    // 2.3.订单id
    //    long orderId = redisIdWorker.nextId("order");
    //    voucherOrder.setId(orderId);
    //    // 2.4.用户id
    //    voucherOrder.setUserId(userId);
    //    // 2.5.代金券id
    //    voucherOrder.setVoucherId(voucherId);
    //
    //    // 2.6.创建阻塞队列
    //    orderTasks.add(voucherOrder);
    //
    //    // 3.获取代理对象（初始化）
    //    proxy = (IVoucherOrderService) AopContext.currentProxy();
    //
    //
    //    // 4.返回订单id
    //    return Result.ok(orderId);
    //}

    // ---------------------------------------------------------------------------------------

    // 原秒杀处理逻辑（分布式锁）
    //@Override
    //public Result seckillVoucher(Long voucherId) {
    //    // 1.查询优惠券
    //    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //    // 2.判断秒杀是否开始
    //    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //        // 尚未开始
    //        return Result.fail("秒杀尚未开始!");
    //    }
    //    // 3.判断秒杀是否已经结束
    //    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
    //        // 已经结束
    //        return Result.fail("秒杀已经结束!");
    //    }
    //    // 4.判断库存是否充足
    //    if (voucher.getStock() < 1) {
    //        // 库存不足
    //        return Result.fail("库存不足!");
    //    }
    //
    //    Long userId = UserHolder.getUser().getId();
    //
    //    // 创建分布式锁对象---------------------------------
    //    //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
    //    RLock lock = redissonClient.getLock("lock:order:" + userId);
    //    // 获取锁
    //    boolean isLock = lock.tryLock();
    //    if (!isLock) {
    //        // 获取锁失败，直接返回失败或者重试
    //        return Result.fail("禁止重复下单!");
    //    }
    //
    //    // 获取当前对象的代理对象 便于执行事务
    //    try {
    //        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //        return proxy.createVoucherOrder(voucherId);
    //    } finally {
    //        // 释放锁
    //        lock.unlock();
    //    }

    // ---------------------------------------------------------------------------------------

    // ---悲观锁---
    //synchronized (userId.toString().intern()) {
    //    // 获取当前对象的代理对象 便于执行事务
    //    IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //    return proxy.createVoucherOrder(voucherId);
    //}

    // ---------------------------------------------------------------------------------------

    // 创建（更新）订单流程
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5.库存充足 则实现一人一单
        // 5.1.查询订单
        Long userId = voucherOrder.getUserId();

        // 每个用户来的时候根据id来锁 而同时String会new一个新的字符串 导致即使是同一个id也会产生新的对象 所以要用intern 取字符串常量池找相同地址的值
        // 确保了 同一个用户id 锁是一样的
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过了!");
            return;
        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // 更新语句
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)  // where条件 where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减库存失败
            log.error("库存不足!");
            return;
        }
        // 7.创建订单
        save(voucherOrder);
    }
}
