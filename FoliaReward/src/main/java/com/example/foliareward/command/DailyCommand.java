package com.example.foliareward.command;
import java.util.Map;
import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /daily 命令快捷方式（等同于 /reward daily）
 */
public class DailyCommand implements CommandExecutor {

    private final FoliaRewardPlugin plugin;

    public DailyCommand(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color("&c只有玩家才能使用此命令！"));
            return true;
        }
        if (!player.hasPermission("reward.daily")) {
            player.sendMessage(MessageUtil.color(
                    plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("no-permission")));
            return true;
        }
        plugin.getDailyGUI().open(player);
        return true;
    }
}
