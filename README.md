# FoliaReward
我的世界服务器签到插件
 目录结构总览                                                                                                                                                       
  FoliaReward/                                                                                                                                                    
  ├── pom.xml                          # Maven 构建配置（含 shade 打包）
  └── src/main/
      ├── resources/
      │   ├── plugin.yml               # 声明 folia-supported: true
      │   └── config.yml               # 完整配置文件
      └── java/.../foliareward/
          ├── FoliaRewardPlugin.java   # 主类（启动/关闭逻辑）
          ├── util/
          │   ├── FoliaScheduler.java  # ★ Folia 调度器封装
          │   └── MessageUtil.java     # 颜色/占位符工具
          ├── model/                   # 数据模型
          │   ├── PlayerData.java      # 玩家数据（ConcurrentHashMap）
          │   ├── RewardConfig.java    # 奖励配置
          │   ├── RewardItem.java      # 单个物品配置
          │   ├── TaskConfig.java      # 任务配置
          │   └── TaskType.java        # 任务类型枚举
          ├── database/
          │   ├── Database.java        # 接口（返回 CompletableFuture）
          │   ├── AbstractDatabase.java# HikariCP + 公共 SQL 逻辑
          │   ├── SQLiteDatabase.java  # SQLite 实现
          │   ├── MySQLDatabase.java   # MySQL 实现
          │   └── DatabaseManager.java # 缓存层
          ├── config/
          │   └── ConfigManager.java   # 类型安全的配置读取
          ├── manager/
          │   ├── RewardManager.java   # 奖励发放核心
          │   ├── DailyRewardManager.java
          │   ├── TaskManager.java
          │   └── GroupRewardManager.java
          ├── listener/                # 事件监听器
          ├── command/                 # 命令处理
          └── gui/                     # GUI 界面

  ---
  Folia 兼容关键点汇总

  ┌───────────────────┬──────────────────────────────────────┬─────────────────────────────────────────────────┐
  │     问题场景      │           传统 Paper 写法            │               本项目的 Folia 写法               │
  ├───────────────────┼──────────────────────────────────────┼─────────────────────────────────────────────────┤
  │ 周期性计时器      │ Bukkit.getScheduler().runTaskTimer() │ FoliaScheduler.runGlobalTimer()                 │
  ├───────────────────┼──────────────────────────────────────┼─────────────────────────────────────────────────┤
  │ 给玩家发物品/消息 │ 直接在异步线程操作                   │ FoliaScheduler.runEntity(player, task, retired) │
  ├───────────────────┼──────────────────────────────────────┼─────────────────────────────────────────────────┤
  │ 数据库 I/O        │ runTaskAsynchronously()              │ FoliaScheduler.runAsync()                       │
  ├───────────────────┼──────────────────────────────────────┼─────────────────────────────────────────────────┤
  │ 玩家进服加载数据  │ 同步读库                             │ 异步加载 → 回调到 EntityScheduler               │
  ├───────────────────┼──────────────────────────────────────┼─────────────────────────────────────────────────┤
  │ 所有数据集合      │ HashMap                              │ ConcurrentHashMap + volatile                    │
  └───────────────────┴──────────────────────────────────────┴─────────────────────────────────────────────────┘

  ---
 测试说明

  测试进服奖励

  1. 将 jar 放入 Folia 服务器的 plugins/ 目录，启动
  2. 用新账号首次进入 → 应收到 3 颗钻石 + 1000 金币 + 欢迎消息
  3. 在 plugins/FoliaReward/data.db（SQLite）中查看 player_data 表，first_join_done 应为 1

  测试每日签到

  1. 输入 /daily → 打开 GUI → 点击签到按钮
  2. 再次 /daily → 应提示"今天已经签到过了"
  3. 管理员重置：/reward daily reset <玩家名>，再次签到生效

  测试任务

  1. 挖掘石头 → 挖满 100 块后收到提示
  2. 输入 /task → 查看进度 → 点击"领取所有已完成奖励"按钮
