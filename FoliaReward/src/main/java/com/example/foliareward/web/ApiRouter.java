package com.example.foliareward.web;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.model.PlayerData;
import com.example.foliareward.model.TaskConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP 请求路由器，处理所有 API 请求和前端页面服务。
 */
public class ApiRouter implements HttpHandler {

    private final FoliaRewardPlugin plugin;
    private final WebServer webServer;

    public ApiRouter(FoliaRewardPlugin plugin, WebServer webServer) {
        this.plugin = plugin;
        this.webServer = webServer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS 支持（方便本地开发调试）
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        try {
            if (path.equals("/") || path.equals("/index.html")) {
                serveHtml(exchange);
            } else if (path.startsWith("/api/")) {
                handleApi(exchange, path.substring(5), method);
            } else {
                sendError(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Web API 处理异常: " + e.getMessage());
            sendError(exchange, 500, "Internal Server Error");
        }
    }

    // ----------------------------------------------------------------
    // 前端页面
    // ----------------------------------------------------------------

    private void serveHtml(HttpExchange exchange) throws IOException {
        InputStream res = plugin.getResource("webui/index.html");
        if (res == null) {
            sendError(exchange, 404, "Frontend not found");
            return;
        }
        byte[] body = res.readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    // ----------------------------------------------------------------
    // API 路由
    // ----------------------------------------------------------------

    private void handleApi(HttpExchange exchange, String apiPath, String method) throws IOException {
        // 读取 token
        String configToken = plugin.getConfig().getString("web.token", "");
        if (!configToken.isEmpty() && !checkToken(exchange, configToken)) {
            sendJson(exchange, 401, JsonBuilder.simple("error", "Unauthorized"));
            return;
        }

        switch (apiPath) {
            case "status"       -> handleStatus(exchange);
            case "players"      -> handlePlayers(exchange);
            case "tasks"        -> handleTasks(exchange);
            case "reload"       -> handleReload(exchange, method);
            case "daily/reset"  -> handleDailyReset(exchange, method);
            case "reward/give"  -> handleRewardGive(exchange, method);
            default             -> {
                // /api/player/{uuid}
                if (apiPath.startsWith("player/")) {
                    handlePlayerDetail(exchange, apiPath.substring(7));
                } else {
                    sendError(exchange, 404, "Unknown API endpoint");
                }
            }
        }
    }

    // ---- GET /api/status ----

    private void handleStatus(HttpExchange exchange) throws IOException {
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        int taskCount = plugin.getConfigManager().getTasks().size();
        String version = plugin.getDescription().getVersion();
        long uptime = webServer.getUptimeSeconds();
        String dbType = plugin.getConfig().getString("database.type", "SQLITE");

        sendJson(exchange, 200, JsonBuilder.simple(
                "players", onlinePlayers,
                "tasks", taskCount,
                "version", version,
                "uptime", uptime,
                "database", dbType
        ));
    }

    // ---- GET /api/players ----

    private void handlePlayers(HttpExchange exchange) throws IOException {
        List<Object> list = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PlayerData data = plugin.getDatabaseManager().getCached(p.getUniqueId());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", p.getUniqueId().toString());
            entry.put("name", p.getName());
            entry.put("streak", data != null ? data.getDailyStreak() : 0);
            entry.put("completedTasks", data != null ? data.getCompletedTasks().size() : 0);
            entry.put("pendingClaim", data != null ? data.getPendingClaim().size() : 0);
            entry.put("lastDailyDate", data != null ? data.getLastDailyClaimDate() : "");
            entry.put("onlineSeconds", plugin.getTaskManager().getOnlineSeconds(p.getUniqueId()));
            list.add(entry);
        }
        sendJson(exchange, 200, JsonBuilder.array(list));
    }

    // ---- GET /api/player/{uuid} ----

    private void handlePlayerDetail(HttpExchange exchange, String uuidStr) throws IOException {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, JsonBuilder.simple("error", "Invalid UUID"));
            return;
        }

        Player player = plugin.getServer().getPlayer(uuid);
        PlayerData data = plugin.getDatabaseManager().getCached(uuid);
        if (player == null || data == null) {
            sendJson(exchange, 404, JsonBuilder.simple("error", "Player not online"));
            return;
        }

        // 构建任务进度列表
        List<Object> taskProgressList = new ArrayList<>();
        for (Map.Entry<String, TaskConfig> entry : plugin.getTaskManager().getTaskConfigs().entrySet()) {
            TaskConfig task = entry.getValue();
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", task.getId());
            t.put("name", stripColor(task.getDisplayName()));
            t.put("progress", data.getProgress(task.getId()));
            t.put("amount", task.getAmount());
            t.put("completed", data.isCompleted(task.getId()));
            t.put("pending", data.getPendingClaim().contains(task.getId()));
            taskProgressList.add(t);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", uuid.toString());
        result.put("name", player.getName());
        result.put("streak", data.getDailyStreak());
        result.put("lastDailyDate", data.getLastDailyClaimDate());
        result.put("firstJoinDone", data.isFirstJoinDone());
        result.put("onlineSeconds", plugin.getTaskManager().getOnlineSeconds(uuid));
        result.put("tasks", taskProgressList);

        sendJson(exchange, 200, JsonBuilder.object(result));
    }

    // ---- GET /api/tasks ----

    private void handleTasks(HttpExchange exchange) throws IOException {
        List<Object> list = new ArrayList<>();
        for (TaskConfig task : plugin.getConfigManager().getTasks().values()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", task.getId());
            t.put("name", stripColor(task.getDisplayName()));
            t.put("description", stripColor(task.getDescription()));
            t.put("type", task.getType().name());
            t.put("target", task.getTarget());
            t.put("amount", task.getAmount());
            t.put("resetDaily", task.isResetDaily());
            t.put("rewardMoney", task.getRewards().getMoney());
            t.put("rewardItems", task.getRewards().getItems().size());
            t.put("rewardCommands", task.getRewards().getCommands().size());
            list.add(t);
        }
        sendJson(exchange, 200, JsonBuilder.array(list));
    }

    // ---- POST /api/reload ----

    private void handleReload(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) { sendError(exchange, 405, "Method Not Allowed"); return; }
        plugin.reload();
        sendJson(exchange, 200, JsonBuilder.simple("success", true, "message", "配置已重载"));
    }

    // ---- POST /api/daily/reset ----

    private void handleDailyReset(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) { sendError(exchange, 405, "Method Not Allowed"); return; }
        String body = readBody(exchange);
        String uuidStr = parseJsonString(body, "uuid");
        if (uuidStr == null) {
            sendJson(exchange, 400, JsonBuilder.simple("error", "Missing uuid")); return;
        }
        Player target;
        try {
            target = plugin.getServer().getPlayer(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, JsonBuilder.simple("error", "Invalid UUID")); return;
        }
        if (target == null) {
            sendJson(exchange, 404, JsonBuilder.simple("error", "Player not online")); return;
        }
        plugin.getDailyRewardManager().resetDailyForPlayer(target);
        sendJson(exchange, 200, JsonBuilder.simple("success", true,
                "message", "已重置 " + target.getName() + " 的签到记录"));
    }

    // ---- POST /api/reward/give ----

    private void handleRewardGive(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) { sendError(exchange, 405, "Method Not Allowed"); return; }
        String body = readBody(exchange);
        String uuidStr = parseJsonString(body, "uuid");
        String taskId  = parseJsonString(body, "taskId");
        if (uuidStr == null || taskId == null) {
            sendJson(exchange, 400, JsonBuilder.simple("error", "Missing uuid or taskId")); return;
        }
        Player target;
        try {
            target = plugin.getServer().getPlayer(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, JsonBuilder.simple("error", "Invalid UUID")); return;
        }
        if (target == null) {
            sendJson(exchange, 404, JsonBuilder.simple("error", "Player not online")); return;
        }
        TaskConfig task = plugin.getConfigManager().getTask(taskId);
        if (task == null) {
            sendJson(exchange, 404, JsonBuilder.simple("error", "Task not found: " + taskId)); return;
        }
        plugin.getRewardManager().giveReward(target, task.getRewards(), target.getName());
        sendJson(exchange, 200, JsonBuilder.simple("success", true,
                "message", "已向 " + target.getName() + " 发放任务奖励: " + taskId));
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    private boolean checkToken(HttpExchange exchange, String configToken) {
        // 从 Authorization 头部读取：Bearer <token>
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return configToken.equals(authHeader.substring(7).trim());
        }
        // 从 query string 读取：?token=xxx
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return configToken.equals(param.substring(6));
                }
            }
        }
        return false;
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        sendJson(exchange, code, JsonBuilder.simple("error", message));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 极简 JSON 字符串字段提取（仅支持简单 key-value）*/
    private String parseJsonString(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    /** 去除 Minecraft 颜色代码（&a, §b 等） */
    private String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }
}
