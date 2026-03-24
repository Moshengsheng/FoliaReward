# FoliaReward

> Folia / Paper 兼容的全功能玩家奖励插件，支持进服礼包、每日签到、任务系统、权限组奖励与 Web 管理后台。

---

## 功能特性

- **进服奖励** — 首次进服一次性礼包 + 每日首次进服提醒/自动发放
- **每日签到** — `/daily` 签到，支持连续签到额外奖励（第 7 / 14 / 30 天）
- **任务系统** — 挖方块、击杀生物、在线时长三类任务，支持每日重置或一次性成就
- **权限组奖励** — 基于 LuckPerms 权限组的差异化奖励，带冷却时间
- **经济集成** — 通过 Vault 发放金币，兼容 EssentialsX 等主流经济插件
- **GUI 界面** — 奖励中心、签到、任务三套 GUI，交互友好
- **双数据库** — SQLite（零配置）或 MySQL（多服同步）自由切换
- **Web 管理后台** — 可选的内置 HTTP 面板，Token 鉴权
- **Folia 原生支持** — 使用 RegionizedScheduler / AsyncScheduler，无 BukkitScheduler 调用

---

## 环境要求

| 项目 | 要求 |
|------|------|
| 服务端 | Paper 或 Folia 1.20.4+ |
| Java | 17+ |
| 软依赖（可选） | Vault、LuckPerms、PlaceholderAPI |

---

## 安装

1. 将 `FoliaReward-1.0.0.jar` 放入服务器 `plugins/` 目录
2. 重启或热重载服务器（`/reload confirm`）
3. 编辑 `plugins/FoliaReward/config.yml` 按需配置
4. 执行 `/reward reload` 使配置生效

---

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/reward` 或 `/r` | `reward.use` | 打开奖励中心 GUI |
| `/reward daily` | `reward.daily` | 打开签到界面 |
| `/reward task` | `reward.task` | 查看任务列表 |
| `/reward task claim` | `reward.task` | 领取已完成任务奖励 |
| `/reward group` | `reward.group` | 领取权限组专属奖励 |
| `/reward give <玩家> <奖励ID>` | `reward.admin` | 手动为玩家发放奖励 |
| `/reward reload` | `reward.admin` | 重载配置文件 |
| `/daily` | `reward.daily` | 快捷签到命令 |
| `/task` | `reward.task` | 快捷任务命令 |

---

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `reward.use` | 所有人 | 打开奖励中心 |
| `reward.daily` | 所有人 | 每日签到 |
| `reward.task` | 所有人 | 查看/领取任务 |
| `reward.group` | 所有人 | 领取权限组奖励 |
| `reward.admin` | OP | 管理员权限（包含以上全部） |

---

## 配置说明

配置文件位于 `plugins/FoliaReward/config.yml`，主要模块：

### 数据库
```yaml
database:
  type: SQLITE   # 改为 MYSQL 后填写下方 mysql 节
  mysql:
    host: localhost
    port: 3306
    database: foliareward
    username: root
    password: password
```

### 任务类型
```yaml
tasks:
  my_task:
    type: BLOCK_BREAK    # 挖方块
    # type: ENTITY_KILL  # 击杀生物
    # type: ONLINE_TIME  # 在线时长（分钟）
    target: STONE        # Material 名 或 EntityType 名
    amount: 100
    reset-daily: true    # false = 一次性成就
```

### Web 管理后台
```yaml
web:
  enabled: true
  port: 8080
  token: "your_secret_token"   # 请务必修改！
```
> 启用后访问 `http://<服务器IP>:8080`，仅限内网使用，**切勿暴露至公网**。

### Web 管理后台 展示
<img width="2554" height="1356" alt="1" src="https://github.com/user-attachments/assets/02609c93-027c-42e8-b80c-ad3f1ed7c3a0" />
<img width="2536" height="1308" alt="2" src="https://github.com/user-attachments/assets/785b3174-8458-46ac-a71d-b1652bf5489b" />
<img width="2538" height="1219" alt="3" src="https://github.com/user-attachments/assets/9c264364-3db4-4bde-969a-6b0c8ff53d5a" />
<img width="2523" height="1258" alt="4" src="https://github.com/user-attachments/assets/3bfbeaa8-0286-481a-904c-637f8ccec68c" />
<img width="2536" height="1362" alt="5" src="https://github.com/user-attachments/assets/4febec4b-f7e4-4fef-bc19-30499ac022fa" />

## 软依赖说明

| 插件 | 作用 | 缺失时行为 |
|------|------|-----------|
| Vault | 金币奖励 | 金币相关功能禁用，其余正常 |
| LuckPerms | 权限组奖励/签到差异化 | 全部玩家使用 `default` 组配置 |
| PlaceholderAPI | 消息中的占位符扩展 | 忽略，不影响基础功能 |

---

## 构建

```bash
git clone https://github.com/Moshengsheng/FoliaReward.git
cd FoliaReward
mvn package -DskipTests
# 产物：target/FoliaReward-1.0.0.jar
```

---

## 许可证

[MIT License](LICENSE)
