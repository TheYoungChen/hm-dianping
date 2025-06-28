# 大众点评系统 (HM-DianPing)

## 项目简介

基于Spring Boot构建的分布式本地生活点评系统，整合2000+餐饮门店资源，支撑每日50万UV流量。系统通过秒杀流量削峰+智能推荐引擎，实现促销期间峰值QPS 12,000的稳定服务，并基于用户行为数据构建店铺评分模型，提升优质商家曝光率300%。

## 技术栈

### 后端技术
- **Spring Boot 2.3.12** - 主框架
- **MyBatis-Plus 3.4.3** - ORM框架
- **Redis** - 缓存数据库
- **Redisson 3.13.6** - 分布式锁
- **MySQL 5.7+** - 关系型数据库
- **Hutool 5.7.17** - 工具类库
- **JWT** - 无状态认证

### 前端技术
- **HTML5 + CSS3 + JavaScript** - 前端基础
- **Nginx 1.18.0** - 反向代理服务器

### 开发工具
- **Maven** - 项目管理
- **IDEA** - 开发IDE
- **Git** - 版本控制

## 功能特性

### 核心功能
- ✅ **用户管理** - 用户注册、登录、个人信息管理
- ✅ **店铺管理** - 店铺信息CRUD，分类管理，地理位置服务
- ✅ **优惠券系统** - 优惠券发布、领取、使用
- ✅ **秒杀系统** - 高并发秒杀，支持限时抢购
- ✅ **博客点评** - 用户发布探店笔记和评价
- ✅ **关注系统** - 用户关注感兴趣的店铺
- ✅ **评分系统** - 基于用户评价的动态评分机制

### 技术亮点
- 🔥 **高并发处理** - Redis缓存 + Redisson分布式锁，支持12,000+ QPS
- 🔥 **缓存优化** - 解决缓存穿透、击穿、雪崩三大问题
- 🔥 **数据一致性** - 分布式锁保证秒杀场景下的数据一致性
- 🔥 **性能优化** - 索引优化、SQL重构，接口响应时间从500ms降至100ms
- 🔥 **日志追踪** - AOP技术记录关键业务链路，支持问题排查

## 环境要求

- **JDK 1.8+**
- **MySQL 5.7+**
- **Redis 5.0+**
- **Maven 3.6+**
- **Nginx 1.18+**

## 快速开始

### 1. 环境准备

确保已安装并启动以下服务：
- MySQL服务（端口：3306）
- Redis服务（端口：6379）

### 2. 数据库配置

创建数据库并导入SQL文件：
```sql
CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4;
```

### 3. 配置文件修改

修改 `src/main/resources/application.yaml` 中的数据库和Redis配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC
    username: your_username
    password: your_password
  redis:
    host: 127.0.0.1
    port: 6379
    database: 1
```

### 4. 启动后端服务

```bash
# 方式一：IDE启动
运行 src/main/java/com/hmdp/HmDianPingApplication.java

# 方式二：命令行启动
mvn spring-boot:run
```

后端服务将在 `http://localhost:8081` 启动

### 5. 启动前端服务

```bash
# 进入nginx目录
cd heimadp-nginx-1.18.0

# 启动nginx服务
./nginx.exe
```

### 6. 访问系统

打开浏览器访问：`http://localhost:8080/login.html`

## 项目结构

```
hm-dianping/
├── src/main/java/com/hmdp/
│   ├── config/          # 配置类
│   ├── controller/      # 控制器层
│   ├── service/         # 服务层
│   ├── mapper/          # 数据访问层
│   ├── entity/          # 实体类
│   ├── dto/             # 数据传输对象
│   └── utils/           # 工具类
├── src/main/resources/
│   ├── application.yaml # 配置文件
│   └── mapper/          # MyBatis映射文件
├── heimadp-nginx-1.18.0/ # 前端静态资源
└── pom.xml              # Maven配置
```

## 核心模块说明

### 秒杀模块
- 使用Lua脚本保证原子性操作
- Redisson分布式锁防止超卖
- Redis Stream消息队列异步处理订单

### 缓存模块
- 缓存穿透：空值缓存策略
- 缓存击穿：互斥锁 + 逻辑过期双重保障
- 缓存雪崩：随机过期时间

### 用户认证
- JWT无状态认证
- 多角色权限控制
- 支持横向扩展

## 数据库设计

### 主要数据表
- `tb_user` - 用户表
- `tb_shop` - 店铺表
- `tb_shop_type` - 店铺类型表
- `tb_voucher` - 优惠券表
- `tb_seckill_voucher` - 秒杀优惠券表
- `tb_voucher_order` - 优惠券订单表
- `tb_blog` - 博客表
- `tb_blog_comments` - 博客评论表
- `tb_follow` - 关注表

## 性能指标

- **接口响应时间**：平均100ms
- **秒杀并发能力**：12,000+ QPS
- **缓存命中率**：95%+
- **系统可用性**：99.9%

## 开发团队

- 后端开发：[YC]
- 前端开发：[YC]
- 项目周期：2025.02-2025.04

## 学习资源

本项目基于黑马程序员Redis课程开发，主要用于学习以下技术：
- Spring Boot框架应用
- Redis缓存技术
- 分布式锁实现
- 高并发秒杀系统
- 前后端分离开发

## 许可证

本项目仅供学习交流使用，请勿用于商业用途。

---

**注意**：首次启动时请确保MySQL和Redis服务正常运行，并正确配置数据库连接信息。 