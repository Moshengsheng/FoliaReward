package com.example.foliareward.web;

import com.example.foliareward.FoliaRewardPlugin;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 内嵌 Web 管理服务器。
 * 使用 JDK 内置 com.sun.net.httpserver，无需额外依赖。
 */
public class WebServer {

    private final FoliaRewardPlugin plugin;
    private HttpServer httpServer;
    private final long startTime = System.currentTimeMillis();

    public WebServer(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        int port = plugin.getConfig().getInt("web.port", 8080);
        httpServer = HttpServer.create(new InetSocketAddress(port), 32);

        ApiRouter router = new ApiRouter(plugin, this);
        httpServer.createContext("/", router);

        httpServer.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "FoliaReward-Web");
            t.setDaemon(true);
            return t;
        }));
        httpServer.start();
        plugin.getLogger().info("Web 管理后台已启动，访问: http://localhost:" + port + "/");
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            plugin.getLogger().info("Web 管理后台已关闭。");
        }
    }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }
}
