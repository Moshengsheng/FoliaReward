# Changelog

## [1.0.0] - 2026-03-24

### 首次发布

#### 核心功能
- **进服奖励系统**：首次进服一次性礼包（物品 + 金币 + 控制台命令），每日首次进服提醒或自动发放
- **每日签到系统**：`/daily` 命令，记录连续签到天数；第 7 / 14 / 30 天触发额外奖励；基于 LuckPerms 权限组的差异化签到奖励（default / vip / svip）
- **任务系统**：支持 `BLOCK_BREAK`（挖方块）、`ENTITY_KILL`（击杀生物）、`ONLINE_TIME`（在线时长）三类任务；每个任务可独立配置为每日重置或一次性成就；任务完成后 GUI 提示，手动领取奖励
- **权限组专属奖励**：`/reward group` 手动领取，支持独立冷却时间（默认 24 小时）

#### 技术亮点
- **Folia 原生兼容**：所有调度器调用均使用 `RegionizedScheduler` / `GlobalRegionScheduler` / `AsyncScheduler`，`plugin.yml` 声明 `folia-supported: true`
- **双数据库支持**：SQLite（零配置，开箱即用）与 MySQL（多服同步，HikariCP 连接池）自由切换
- **异步数据持久化**：每 5 分钟异步批量保存在线玩家数据，关服时同步等待写入完成，数据安全有保障
- **GUI 界面**：奖励中心、签到、任务三套 Inventory GUI

#### 集成与扩展
- Vault 经济集成（软依赖，缺失时自动降级）
- LuckPerms 集成，读取玩家当前主权限组
- PlaceholderAPI 软依赖预留
- 可选内置 Web 管理后台（HTTP + Token 鉴权）

#### 配置 & 运维
- 全功能 `config.yml`，内含详细中文注释
- `/reward reload` 热重载配置，无需重启服务器
- 管理员命令 `/reward give <玩家> <奖励ID>` 手动补发奖励
