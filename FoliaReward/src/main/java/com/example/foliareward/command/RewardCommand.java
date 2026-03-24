package com.example.foliareward.command;

import com.example.foliareward.FoliaRewardPlugin;
import com.example.foliareward.model.RewardConfig;
import com.example.foliareward.model.TaskConfig;
import com.example.foliareward.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /reward 和 /task 命令处理器
 */
public class RewardCommand implements CommandExecutor, TabCompleter {

    private final FoliaRewardPlugin plugin;

    public RewardCommand(FoliaRewardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /task 是 /reward task 的别名
        if (command.getName().equalsIgnoreCase("task")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.color("&c只有玩家才能使用此命令！"));
                return true;
            }
            if (!player.hasPermission("reward.task")) {
                player.sendMessage(MessageUtil.color(
                        plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("no-permission")));
                return true;
            }
            plugin.getTaskGUI().open(player);
            return true;
        }

        // ---- /reward ----
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessageUtil.color("&c只有玩家才能打开 GUI！"));
                return true;
            }
            if (!player.hasPermission("reward.use")) {
                player.sendMessage(MessageUtil.color(
                        plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("no-permission")));
                return true;
            }
            plugin.getRewardGUI().open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("只有玩家才能打开 GUI！"); return true;
                }
                if (!player.hasPermission("reward.use")) { noPermission(player); return true; }
                plugin.getRewardGUI().open(player);
            }
            case "daily" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("只有玩家才能使用！"); return true;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
                    // /reward daily reset <player>
                    if (!sender.hasPermission("reward.admin")) { noPermission(sender); return true; }
                    Player target = plugin.getServer().getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage(MessageUtil.format(
                                plugin.getConfigManager().getPrefix() +
                                plugin.getConfigManager().getMessage("reward-player-offline"),
                                "{player}", args[2]));
                        return true;
                    }
                    plugin.getDailyRewardManager().resetDailyForPlayer(target);
                    sender.sendMessage(MessageUtil.color(
                            plugin.getConfigManager().getPrefix() +
                            "&a已重置 " + target.getName() + " 的签到记录。"));
                } else {
                    if (!player.hasPermission("reward.daily")) { noPermission(player); return true; }
                    plugin.getDailyRewardManager().claimDaily(player);
                }
            }
            case "task" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("只有玩家才能使用！"); return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("claim")) {
                    if (!player.hasPermission("reward.task")) { noPermission(player); return true; }
                    plugin.getTaskManager().claimTaskRewards(player);
                } else {
                    if (!player.hasPermission("reward.task")) { noPermission(player); return true; }
                    plugin.getTaskGUI().open(player);
                }
            }
            case "group" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("只有玩家才能使用！"); return true;
                }
                if (!player.hasPermission("reward.group")) { noPermission(player); return true; }
                plugin.getGroupRewardManager().claimGroupReward(player);
            }
            case "give" -> {
                // /reward give <player> <rewardId>
                if (!sender.hasPermission("reward.admin")) { noPermission(sender); return true; }
                if (args.length < 3) {
                    sender.sendMessage(MessageUtil.color("&c用法: /reward give <玩家> <奖励ID>"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(MessageUtil.format(
                            plugin.getConfigManager().getPrefix() +
                            plugin.getConfigManager().getMessage("reward-player-offline"),
                            "{player}", args[1]));
                    return true;
                }
                TaskConfig task = plugin.getConfigManager().getTask(args[2]);
                if (task == null) {
                    sender.sendMessage(MessageUtil.color("&c找不到奖励ID: " + args[2]));
                    return true;
                }
                plugin.getRewardManager().giveReward(target, task.getRewards(), target.getName());
                sender.sendMessage(MessageUtil.format(
                        plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("reward-given"),
                        "{player}", target.getName(), "{reward}", args[2]));
            }
            case "reload" -> {
                if (!sender.hasPermission("reward.admin")) { noPermission(sender); return true; }
                plugin.reload();
                sender.sendMessage(MessageUtil.color(
                        plugin.getConfigManager().getPrefix() +
                        plugin.getConfigManager().getMessage("reload-success")));
            }
            default -> {
                sender.sendMessage(MessageUtil.color(
                        "&6FoliaReward &7命令帮助:\n" +
                        "&e/reward &7- 打开奖励主界面\n" +
                        "&e/reward daily &7- 每日签到\n" +
                        "&e/reward task &7- 查看任务\n" +
                        "&e/reward task claim &7- 领取任务奖励\n" +
                        "&e/reward group &7- 领取权限组奖励\n" +
                        "&e/reward give <玩家> <奖励ID> &7- [OP] 手动发放\n" +
                        "&e/reward reload &7- [OP] 重载配置"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("task")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("gui", "daily", "task", "group", "give", "reload");
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("daily")) {
                return List.of("reset");
            }
            if (args[0].equalsIgnoreCase("task")) {
                return List.of("claim");
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return plugin.getConfigManager().getTasks().keySet().stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("daily")
                && args[1].equalsIgnoreCase("reset")) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(MessageUtil.color(
                plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("no-permission")));
    }
}
