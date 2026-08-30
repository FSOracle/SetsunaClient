package com.setsuna.command.commands;

import com.mojang.blaze3d.platform.InputConstants;
import com.setsuna.Setsuna;
import com.setsuna.command.Command;
import com.setsuna.command.CommandException;
import com.setsuna.command.CommandManager;
import com.setsuna.command.Parameter;
import com.setsuna.command.builder.CommandBuilder;
import com.setsuna.command.builder.ParameterBuilder;
import com.setsuna.config.ConfigManager;
import com.setsuna.manager.FriendManager;
import com.setsuna.module.Category;
import com.setsuna.module.Module;
import com.setsuna.module.ModuleManager;
import com.setsuna.module.modules.render.TeamViewer;
import com.setsuna.script.LuaScriptError;
import com.setsuna.script.LuaScriptManager;
import com.setsuna.setting.Setting;
import com.setsuna.setting.settings.BooleanSetting;
import com.setsuna.setting.settings.ColorSetting;
import com.setsuna.setting.settings.DoubleSetting;
import com.setsuna.setting.settings.EnumSetting;
import com.setsuna.setting.settings.IntSetting;
import com.setsuna.setting.settings.KeybindSetting;
import com.setsuna.setting.settings.StringSetting;
import com.setsuna.util.StringUtil;
import com.setsuna.util.client.InputBind;
import com.setsuna.util.client.KeybindUtils;
import com.setsuna.util.player.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Commands whose backing features exist in Setsuna. */
public final class BuiltInCommands {

    private static final Minecraft MC = Setsuna.mc();

    private BuiltInCommands() {
    }

    public static void register(CommandManager manager) {
        manager.addCommand(client(manager));
        manager.addCommand(friend());
        manager.addCommand(toggle());
        manager.addCommand(bind());
        manager.addCommand(center());
        manager.addCommand(help(manager));
        manager.addCommand(binds());
        manager.addCommand(clear());
        manager.addCommand(hide());
        manager.addCommand(panic());
        manager.addCommand(value());
        manager.addCommand(config("config"));
        manager.addCommand(config("localconfig"));
        manager.addCommand(ping());
        manager.addCommand(say());
        manager.addCommand(username());
        manager.addCommand(coordinates());
        manager.addCommand(vclip());
        manager.addCommand(serverInfo());
        manager.addCommand(lua());
        manager.addCommand(teamViewer());
    }

    private static Command teamViewer() {
        Parameter<String> player = ParameterBuilder.<String>begin("player")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedFrom(() -> TeamViewer.INSTANCE.members().stream()
                        .map(TeamViewer.MemberSnapshot::name)
                        .filter(name -> !name.isBlank())
                        .toList())
                .required()
                .build();
        Command position = CommandBuilder.begin("position")
                .alias("pmc")
                .description("Show an Apollo team member's position")
                .parameter(player)
                .handler(context -> {
                    String name = (String) context.arg(0);
                    TeamViewer.MemberSnapshot member = TeamViewer.INSTANCE.findMember(name)
                            .orElseThrow(() -> new CommandException(name + " is not on the Apollo team."));
                    reply(member.coordinates());
                })
                .build();
        Command list = CommandBuilder.begin("list")
                .description("List active Apollo team members")
                .handler(context -> {
                    List<String> names = TeamViewer.INSTANCE.members().stream()
                            .map(TeamViewer.MemberSnapshot::name)
                            .filter(name -> !name.isBlank())
                            .toList();
                    reply(names.isEmpty() ? "No active Apollo team members."
                            : "Apollo team: " + String.join(", ", names));
                })
                .build();
        Command status = CommandBuilder.begin("status")
                .description("Show Apollo receiver and renderer status")
                .handler(context -> {
                    TeamViewer.Status state = TeamViewer.INSTANCE.status();
                    String age = state.lastPacketAgeMs() < 0L
                            ? "never" : state.lastPacketAgeMs() + "ms ago";
                    reply("Apollo packets=" + state.payloads()
                            + ", updates=" + state.updates()
                            + ", ignored=" + state.ignored()
                            + ", malformed=" + state.malformed());
                    reply("Members=" + state.members()
                            + ", drawn=" + state.drawn()
                            + ", selfWorld=" + (state.selfWorld() == null ? "unknown" : state.selfWorld())
                            + ", lastPacket=" + age);
                })
                .build();
        return CommandBuilder.begin("teamviewer")
                .alias("tv")
                .description("Inspect the Lunar Apollo team")
                .hub()
                .subcommand(position)
                .subcommand(list)
                .subcommand(status)
                .build();
    }

    private static Command lua() {
        Command list = CommandBuilder.begin("list")
                .description("列出已加载的 Lua 脚本")
                .handler(context -> {
                    List<String> scripts = LuaScriptManager.INSTANCE.loadedScripts();
                    reply(scripts.isEmpty() ? "没有已加载的 Lua 脚本。"
                            : "Lua 脚本: " + String.join(", ", scripts));
                })
                .build();
        Command reload = CommandBuilder.begin("reload")
                .description("重新加载 Lua 脚本")
                .handler(context -> {
                    try {
                        int modules = LuaScriptManager.INSTANCE.reload();
                        int errors = LuaScriptManager.INSTANCE.errors().size();
                        reply("Lua 已重新加载: " + modules + " 个模块, " + errors + " 个错误。");
                    } catch (IOException | RuntimeException error) {
                        throw new CommandException("Lua 重载失败: " + safeMessage(error));
                    }
                })
                .build();
        Command errors = CommandBuilder.begin("errors")
                .description("显示最近的 Lua 错误")
                .handler(context -> {
                    List<LuaScriptError> failures = LuaScriptManager.INSTANCE.errors();
                    if (failures.isEmpty()) {
                        reply("没有 Lua 错误。");
                        return;
                    }
                    reply("最近的 Lua 错误 (" + failures.size() + "):");
                    failures.stream().skip(Math.max(0, failures.size() - 10L)).forEach(error -> {
                        String file = error.file() == null ? "runtime" : error.file().getFileName().toString();
                        reply("  " + file + " [" + error.phase() + "]: " + error.message());
                    });
                })
                .build();
        Command folder = CommandBuilder.begin("folder")
                .description("打开 Lua 脚本目录")
                .handler(context -> {
                    try {
                        LuaScriptManager.INSTANCE.openScriptsDirectory();
                        reply("已打开 Lua 脚本目录。");
                    } catch (IOException | RuntimeException error) {
                        throw new CommandException("无法打开 Lua 脚本目录: " + safeMessage(error));
                    }
                })
                .build();
        return CommandBuilder.begin("lua")
                .description("管理 Lua 脚本")
                .hub()
                .subcommand(list)
                .subcommand(reload)
                .subcommand(errors)
                .subcommand(folder)
                .build();
    }

    private static Command client(CommandManager manager) {
        Command info = CommandBuilder.begin("info")
                .description("Show client information")
                .handler(context -> {
                    reply("Client: " + Setsuna.NAME);
                    reply("Version: " + Setsuna.VERSION);
                    reply("Author: " + Setsuna.DEVELOPER);
                })
                .build();
        Command prefix = CommandBuilder.begin("prefix")
                .parameter(stringParameter("prefix", true))
                .handler(context -> {
                    String value = (String) context.arg(0);
                    manager.setPrefix(value);
                    saveConfig();
                    reply("Command prefix changed to " + value);
                })
                .build();
        return CommandBuilder.begin("client")
                .description("Manage the client")
                .hub()
                .subcommand(info)
                .subcommand(prefix)
                .build();
    }

    private static Command help(CommandManager manager) {
        Parameter<Integer> page = ParameterBuilder.<Integer>begin("page")
                .verifiedBy(input -> {
                    int maxPage = Math.max(1, (manager.commands().size() + 7) / 8);
                    try {
                        int parsed = Integer.parseInt(input);
                        return parsed >= 1 && parsed <= maxPage
                                ? new Parameter.Ok<>(parsed)
                                : new Parameter.Error<>("'" + input + "' is not in range 1.." + maxPage);
                    } catch (NumberFormatException ignored) {
                        return new Parameter.Error<>("'" + input + "' is not an integer");
                    }
                })
                .optional()
                .build();
        return CommandBuilder.begin("help")
                .description("Show available commands")
                .parameter(page)
                .handler(context -> {
                    int selectedPage = context.optionalArg(0) instanceof Integer value ? value : 1;
                    List<Command> commands = manager.commands();
                    int maxPage = Math.max(1, (commands.size() + 7) / 8);
                    int from = (selectedPage - 1) * 8;
                    int to = Math.min(from + 8, commands.size());
                    reply("Commands (" + selectedPage + "/" + maxPage + "):");
                    for (Command command : commands.subList(from, to)) {
                        String aliases = command.aliases().isEmpty()
                                ? ""
                                : " (" + String.join(", ", command.aliases()) + ")";
                        reply(manager.prefix() + command.name() + aliases
                                + (command.description().isEmpty() ? "" : " - " + command.description()));
                    }
                })
                .build();
    }

    private static Command toggle() {
        return CommandBuilder.begin("toggle")
                .alias("t")
                .description("Toggle a module")
                .parameter(moduleParameter("module", module -> true))
                .handler(context -> {
                    Module module = (Module) context.arg(0);
                    if (module.isToggleable()) {
                        module.toggle();
                        saveConfig();
                        reply(module.displayName() + " " + (module.isEnabled() ? "enabled" : "disabled") + ".");
                    } else {
                        module.trigger();
                        reply(module.displayName() + " triggered.");
                    }
                })
                .build();
    }

    private static Command bind() {
        return CommandBuilder.begin("bind")
                .description("Bind a key to a module")
                .parameter(moduleParameter("module", module -> true))
                .parameter(keyParameter())
                .parameter(choiceParameter("action", List.of("Toggle", "Hold", "Smart"), false, false))
                .parameter(choiceParameter(
                        "modifiers", List.of("Shift", "Control", "Alt", "Super"), false, true))
                .handler(context -> bindModule(
                        (Module) context.arg(0),
                        (String) context.arg(1),
                        (String) context.optionalArg(2),
                        context.optionalArg(3)))
                .build();
    }

    private static Command binds() {
        Command add = CommandBuilder.begin("add")
                .parameter(moduleParameter("module", module -> true))
                .parameter(keyParameter())
                .parameter(choiceParameter("action", List.of("Toggle", "Hold", "Smart"), false, false))
                .parameter(choiceParameter(
                        "modifiers", List.of("Shift", "Control", "Alt", "Super"), false, true))
                .handler(context -> bindModule(
                        (Module) context.arg(0),
                        (String) context.arg(1),
                        (String) context.optionalArg(2),
                        context.optionalArg(3)))
                .build();
        Command remove = CommandBuilder.begin("remove")
                .parameter(modulesParameter("modules", module -> module.keyBind() != KeybindUtils.NONE))
                .handler(context -> {
                    @SuppressWarnings("unchecked")
                    Set<Module> modules = (Set<Module>) context.arg(0);
                    for (Module module : modules) {
                        module.setBind(InputBind.UNBOUND);
                        reply("Removed bind from " + module.displayName() + ".");
                    }
                    saveConfig();
                })
                .build();
        Command list = CommandBuilder.begin("list")
                .handler(context -> {
                    List<Module> bound = ModuleManager.INSTANCE.modules().stream()
                            .filter(module -> module.keyBind() != KeybindUtils.NONE)
                            .sorted(Comparator.comparing(Module::displayName, String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    if (bound.isEmpty()) {
                        reply("No modules are bound.");
                    } else {
                        reply("Bindings:");
                        for (Module module : bound) {
                            reply(module.displayName() + ": " + module.bind().renderText());
                        }
                    }
                })
                .build();
        Command clear = CommandBuilder.begin("clear")
                .handler(context -> {
                    ModuleManager.INSTANCE.modules().forEach(module -> module.setBind(InputBind.UNBOUND));
                    saveConfig();
                    reply("All binds cleared.");
                })
                .build();
        return CommandBuilder.begin("binds")
                .description("Manage module bindings")
                .hub()
                .subcommand(add)
                .subcommand(remove)
                .subcommand(list)
                .subcommand(clear)
                .build();
    }

    private static Command friend() {
        Command add = CommandBuilder.begin("add")
                .parameter(playerNameParameter("playerName"))
                .parameter(stringParameter("alias", false))
                .handler(context -> {
                    String name = (String) context.arg(0);
                    String alias = (String) context.optionalArg(1);
                    if (!FriendManager.INSTANCE.add(name)) {
                        throw new CommandException(name + " is already a friend.");
                    }
                    if (alias != null) FriendManager.INSTANCE.setAlias(name, alias);
                    saveConfig();
                    reply(alias == null ? "Added friend " + name + "." : "Added friend " + name + " as " + alias + ".");
                })
                .build();
        Command remove = CommandBuilder.begin("remove")
                .parameter(friendNameParameter())
                .handler(context -> {
                    String name = (String) context.arg(0);
                    if (!FriendManager.INSTANCE.remove(name)) {
                        throw new CommandException(name + " is not a friend.");
                    }
                    saveConfig();
                    reply("Removed friend " + name + ".");
                })
                .build();
        Command alias = CommandBuilder.begin("alias")
                .parameter(friendNameParameter())
                .parameter(stringParameter("alias", true))
                .handler(context -> {
                    String name = (String) context.arg(0);
                    String value = (String) context.arg(1);
                    if (!FriendManager.INSTANCE.setAlias(name, value)) {
                        throw new CommandException(name + " is not a friend.");
                    }
                    saveConfig();
                    reply("Changed " + name + "'s alias to " + value + ".");
                })
                .build();
        Command list = CommandBuilder.begin("list")
                .handler(context -> {
                    List<String> friends = FriendManager.INSTANCE.all();
                    if (friends.isEmpty()) {
                        reply("No friends.");
                    } else {
                        reply("Friends:");
                        for (String name : friends) {
                            String value = FriendManager.INSTANCE.alias(name);
                            reply("- " + name + (value == null ? "" : " (" + value + ")"));
                        }
                    }
                })
                .build();
        Command clear = CommandBuilder.begin("clear")
                .handler(context -> {
                    if (FriendManager.INSTANCE.all().isEmpty()) {
                        throw new CommandException("No friends.");
                    }
                    FriendManager.INSTANCE.clear();
                    saveConfig();
                    reply("Friend list cleared.");
                })
                .build();
        return CommandBuilder.begin("friend")
                .description("Manage friends")
                .hub()
                .subcommand(add)
                .subcommand(remove)
                .subcommand(alias)
                .subcommand(list)
                .subcommand(clear)
                .build();
    }

    private static Command clear() {
        return CommandBuilder.begin("clear")
                .description("Clear the chat")
                .handler(context -> {
                    if (MC.gui != null) MC.gui.getChat().clearMessages(true);
                })
                .build();
    }

    private static Command hide() {
        Command hide = CommandBuilder.begin("hide")
                .parameter(modulesParameter("modules", module -> !module.isHidden()))
                .handler(context -> setHidden(context, true))
                .build();
        Command unhide = CommandBuilder.begin("unhide")
                .parameter(modulesParameter("modules", Module::isHidden))
                .handler(context -> setHidden(context, false))
                .build();
        Command list = CommandBuilder.begin("list")
                .handler(context -> {
                    List<Module> hidden = ModuleManager.INSTANCE.modules().stream().filter(Module::isHidden).toList();
                    reply(hidden.isEmpty() ? "No hidden modules." : "Hidden: "
                            + String.join(", ", hidden.stream().map(Module::displayName).toList()));
                })
                .build();
        Command clear = CommandBuilder.begin("clear")
                .handler(context -> {
                    ModuleManager.INSTANCE.modules().forEach(module -> module.setHidden(false));
                    saveConfig();
                    reply("All modules unhidden.");
                })
                .build();
        return CommandBuilder.begin("hide")
                .description("Hide modules from the module list")
                .hub()
                .subcommand(hide)
                .subcommand(unhide)
                .subcommand(list)
                .subcommand(clear)
                .build();
    }

    private static Command panic() {
        return CommandBuilder.begin("panic")
                .description("Disable modules")
                .parameter(stringParameter("category", false))
                .handler(context -> {
                    String type = context.optionalArg(0) instanceof String value
                            ? value.toLowerCase(Locale.ROOT)
                            : "nonrender";
                    Category category = null;
                    if (!type.equals("all") && !type.equals("nonrender")) {
                        for (Category candidate : Category.values()) {
                            if (candidate.name().equalsIgnoreCase(type)
                                    || candidate.displayName().equalsIgnoreCase(type)) {
                                category = candidate;
                                break;
                            }
                        }
                        if (category == null) throw new CommandException("Category " + type + " not found.");
                    }
                    int count = 0;
                    for (Module module : ModuleManager.INSTANCE.modules()) {
                        if (!module.isEnabled()) continue;
                        if (type.equals("nonrender") && module.category() == Category.RENDER) continue;
                        if (category != null && module.category() != category) continue;
                        module.setEnabled(false);
                        count++;
                    }
                    saveConfig();
                    reply("Disabled " + count + " modules.");
                })
                .build();
    }

    private static Command value() {
        Command set = CommandBuilder.begin("set")
                .parameter(settingPathParameter())
                .parameter(valueParameter())
                .handler(context -> {
                    String path = (String) context.arg(0);
                    Setting<?> setting = findSetting(path);
                    if (setting == null) throw new CommandException("Value " + path + " was not found.");
                    setSettingFromString(setting, (String) context.arg(1));
                    saveConfig();
                    reply("Set " + path + " to " + formatSetting(setting) + ".");
                })
                .build();
        Command reset = CommandBuilder.begin("reset")
                .parameter(settingPathParameter())
                .handler(context -> {
                    String path = (String) context.arg(0);
                    Setting<?> setting = findSetting(path);
                    if (setting == null) throw new CommandException("Value " + path + " was not found.");
                    setting.reset();
                    saveConfig();
                    reply("Reset " + path + ".");
                })
                .build();
        Command resetAll = CommandBuilder.begin("reset-all")
                .parameter(moduleParameter("valueGroupPath", module -> true))
                .handler(context -> {
                    Module module = (Module) context.arg(0);
                    module.settings().forEach(Setting::reset);
                    saveConfig();
                    reply("Reset all values in " + module.displayName() + ".");
                })
                .build();
        return CommandBuilder.begin("value")
                .description("Change values by path")
                .hub()
                .subcommand(set)
                .subcommand(reset)
                .subcommand(resetAll)
                .build();
    }

    private static Command config(String rootName) {
        Parameter<String> profile = profileParameter();
        Command save = CommandBuilder.begin("save")
                .alias("create")
                .parameter(stringParameter("name", false))
                .handler(context -> {
                    String name = context.optionalArg(0) instanceof String value
                            ? value
                            : ConfigManager.INSTANCE.currentProfile();
                    try {
                        reply("Saved config " + ConfigManager.INSTANCE.saveProfile(name) + ".");
                    } catch (IOException error) {
                        throw new CommandException("Failed to save config " + name + ".", error, List.of());
                    }
                })
                .build();
        Command load = CommandBuilder.begin("load")
                .parameter(profile)
                .handler(context -> {
                    String name = (String) context.arg(0);
                    try {
                        if (!ConfigManager.INSTANCE.loadProfile(name)) {
                            throw new CommandException("Config " + name + " was not found.");
                        }
                        reply("Loaded config " + ConfigManager.INSTANCE.currentProfile() + ".");
                    } catch (IOException error) {
                        throw new CommandException("Failed to load config " + name + ".", error, List.of());
                    }
                })
                .build();
        Command list = CommandBuilder.begin("list")
                .handler(context -> {
                    List<String> profiles = ConfigManager.INSTANCE.listProfiles();
                    reply("Local configs: " + (profiles.isEmpty() ? "none" : String.join(", ", profiles)));
                    reply("Active: " + ConfigManager.INSTANCE.currentProfile());
                })
                .build();
        Command delete = CommandBuilder.begin("delete")
                .parameter(profileParameter())
                .handler(context -> {
                    String name = (String) context.arg(0);
                    try {
                        if (!ConfigManager.INSTANCE.deleteProfile(name)) {
                            throw new CommandException("Config " + name + " was not found or cannot be deleted.");
                        }
                        reply("Deleted config " + name + ".");
                    } catch (IOException error) {
                        throw new CommandException("Failed to delete config " + name + ".", error, List.of());
                    }
                })
                .build();
        Command current = CommandBuilder.begin("current")
                .handler(context -> reply("Active config: " + ConfigManager.INSTANCE.currentProfile()))
                .build();
        Command browse = CommandBuilder.begin("browse")
                .handler(context -> {
                    try {
                        java.awt.Desktop.getDesktop().open(ConfigManager.INSTANCE.configDirectory().toFile());
                    } catch (Exception error) {
                        throw new CommandException("Failed to open the config directory.", error, List.of());
                    }
                })
                .build();
        return CommandBuilder.begin(rootName)
                .description("Manage local configurations")
                .hub()
                .subcommand(save)
                .subcommand(load)
                .subcommand(list)
                .subcommand(browse)
                .subcommand(delete)
                .subcommand(current)
                .build();
    }

    private static Command ping() {
        return CommandBuilder.begin("ping")
                .description("Show your latency")
                .requiresIngame()
                .handler(context -> {
                    PlayerInfo info = MC.getConnection() == null || MC.player == null
                            ? null
                            : MC.getConnection().getPlayerInfo(MC.player.getUUID());
                    if (info == null) throw new CommandException("Player info is null.");
                    reply("Your ping is " + info.getLatency() + " ms.");
                })
                .build();
    }

    private static Command say() {
        return CommandBuilder.begin("say")
                .description("Send a chat message")
                .requiresIngame()
                .parameter(ParameterBuilder.<String>begin("message")
                        .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                        .required()
                        .vararg()
                        .build())
                .handler(context -> {
                    if (MC.getConnection() == null) throw new CommandException("Network connection is null.");
                    Object[] words = (Object[]) context.arg(0);
                    MC.getConnection().sendChat(String.join(" ", Arrays.copyOf(words, words.length, String[].class)));
                })
                .build();
    }

    private static Command username() {
        return CommandBuilder.begin("username")
                .description("Show and copy your username")
                .requiresIngame()
                .handler(context -> {
                    String name = MC.player.getName().getString();
                    MC.keyboardHandler.setClipboard(name);
                    reply("Username: " + name);
                })
                .build();
    }

    private static Command coordinates() {
        Command whisper = CommandBuilder.begin("whisper")
                .parameter(playerNameParameter("playerName"))
                .handler(context -> {
                    if (MC.getConnection() == null) throw new CommandException("Network connection is null.");
                    MC.getConnection().sendCommand("msg " + context.arg(0) + " My coordinates are: " + coordinatesText());
                })
                .build();
        Command copy = CommandBuilder.begin("copy")
                .handler(context -> {
                    MC.keyboardHandler.setClipboard(coordinatesText());
                    reply("Coordinates copied.");
                })
                .build();
        Command info = CommandBuilder.begin("info")
                .handler(context -> reply(coordinatesText()))
                .build();
        return CommandBuilder.begin("coordinates")
                .alias("position", "coords")
                .description("Show or share your coordinates")
                .requiresIngame()
                .hub()
                .subcommand(whisper)
                .subcommand(copy)
                .subcommand(info)
                .build();
    }

    private static Command center() {
        return CommandBuilder.begin("center")
                .description("Center your player on the current block")
                .requiresIngame()
                .handler(context -> MC.player.setPos(
                        MC.player.getBlockX() + 0.5D,
                        MC.player.getY(),
                        MC.player.getBlockZ() + 0.5D))
                .build();
    }

    private static Command vclip() {
        Command by = CommandBuilder.begin("by")
                .parameter(ParameterBuilder.<Float>begin("distance")
                        .verifiedBy(ParameterBuilder.FLOAT_VALIDATOR).required().build())
                .handler(context -> {
                    float distance = (Float) context.arg(0);
                    MC.player.setPos(MC.player.getX(), MC.player.getY() + distance, MC.player.getZ());
                })
                .build();
        Command up = smartVclip("up", 1);
        Command down = smartVclip("down", -1);
        Command smart = CommandBuilder.begin("smart").hub().subcommand(up).subcommand(down).build();
        return CommandBuilder.begin("vclip")
                .description("Clip vertically")
                .requiresIngame()
                .hub()
                .subcommand(by)
                .subcommand(smart)
                .build();
    }

    private static Command smartVclip(String name, int direction) {
        return CommandBuilder.begin(name)
                .parameter(ParameterBuilder.<Integer>begin("max")
                        .verifiedBy(ParameterBuilder.INTEGER_VALIDATOR).optional().build())
                .handler(context -> {
                    int max = context.optionalArg(0) instanceof Integer value ? Math.abs(value) : 10;
                    BlockPos origin = MC.player.blockPosition();
                    for (int distance = 1; distance < max; distance++) {
                        int offset = distance * direction;
                        BlockPos feet = origin.offset(0, offset, 0);
                        if (MC.level.getBlockState(feet).getCollisionShape(MC.level, feet).isEmpty()
                                && MC.level.getBlockState(feet.above()).getCollisionShape(MC.level, feet.above()).isEmpty()) {
                            MC.player.setPos(MC.player.getX(), feet.getY(), MC.player.getZ());
                            return;
                        }
                    }
                    throw new CommandException("No position found.");
                })
                .build();
    }

    private static Command serverInfo() {
        return CommandBuilder.begin("serverinfo")
                .description("Show current server information")
                .requiresIngame()
                .parameter(choiceParameter("detect", List.of("Plugins", "Hosting"), false, false))
                .handler(context -> {
                    ServerData server = MC.getCurrentServer();
                    PlayerInfo info = MC.getConnection() == null || MC.player == null
                            ? null
                            : MC.getConnection().getPlayerInfo(MC.player.getUUID());
                    reply("Server Information:");
                    reply("Address: " + (server == null ? "Singleplayer" : server.ip));
                    reply("Brand: " + (MC.getConnection() == null ? "N/A" : MC.getConnection().serverBrand()));
                    reply("Advertised version: " + (server == null || server.version == null
                            ? "N/A" : server.version.getString() + " (" + server.protocol + ")"));
                    reply("Ping: " + (info == null ? "N/A" : info.getLatency() + " ms"));
                    if (context.optionalArg(0) != null) {
                        reply("Active Plugins/Hosting detection is unavailable in Setsuna.");
                    }
                })
                .build();
    }

    private static void bindModule(
            Module module, String keyName, String actionName, Object modifierArguments) {
        int key = keyName.equalsIgnoreCase("none") ? KeybindUtils.NONE : KeyNames.parse(keyName);
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            throw new CommandException("Key " + keyName + " was not found.");
        }
        if (key == KeybindUtils.NONE) {
            module.setBind(InputBind.UNBOUND);
        } else {
            InputBind.BindAction action = actionName == null
                    ? module.bind().action()
                    : InputBind.BindAction.fromTag(actionName);
            Set<InputBind.Modifier> modifiers = module.bind().modifiers();
            if (modifierArguments instanceof Object[] values) {
                EnumSet<InputBind.Modifier> parsed = EnumSet.noneOf(InputBind.Modifier.class);
                for (Object value : values) {
                    InputBind.Modifier modifier = InputBind.Modifier.fromTag(String.valueOf(value));
                    if (modifier != null) {
                        parsed.add(modifier);
                    }
                }
                modifiers = parsed;
            }
            module.setBind(new InputBind(key, action, modifiers));
        }
        saveConfig();
        reply(key == KeybindUtils.NONE
                ? module.displayName() + " unbound."
                : module.displayName() + " bound to " + module.bind().renderText() + ".");
    }

    private static void setHidden(Command.Context context, boolean hidden) {
        @SuppressWarnings("unchecked")
        Set<Module> modules = (Set<Module>) context.arg(0);
        modules.forEach(module -> module.setHidden(hidden));
        saveConfig();
        reply((hidden ? "Hidden: " : "Unhidden: ")
                + String.join(", ", modules.stream().map(Module::displayName).toList()));
    }

    private static Parameter<Module> moduleParameter(String name, Predicate<Module> predicate) {
        return ParameterBuilder.<Module>begin(name)
                .verifiedBy(input -> {
                    Module module = findModule(input);
                    return module != null && predicate.test(module)
                            ? new Parameter.Ok<>(module)
                            : new Parameter.Error<>("Module '" + input + "' not found");
                })
                .autocompletedWith((begin, ignored) -> ModuleManager.INSTANCE.modules().stream()
                        .filter(predicate)
                        .map(Module::id)
                        .filter(value -> startsWithIgnoreCase(value, begin))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList())
                .required()
                .build();
    }

    private static Parameter<Set<Module>> modulesParameter(String name, Predicate<Module> predicate) {
        return ParameterBuilder.<Set<Module>>begin(name)
                .verifiedBy(input -> {
                    Set<Module> modules = new LinkedHashSet<>();
                    for (String part : input.split(",")) {
                        Module module = findModule(part);
                        if (module != null && predicate.test(module)) modules.add(module);
                    }
                    return modules.isEmpty()
                            ? new Parameter.Error<>("'" + input + "' contains no valid Module")
                            : new Parameter.Ok<>(modules);
                })
                .autocompletedWith((begin, ignored) -> {
                    int splitAt = begin.lastIndexOf(',') + 1;
                    String prefix = begin.substring(0, splitAt);
                    String typed = begin.substring(splitAt);
                    return ModuleManager.INSTANCE.modules().stream()
                            .filter(predicate)
                            .map(Module::id)
                            .filter(value -> startsWithIgnoreCase(value, typed))
                            .map(value -> prefix + value)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                })
                .required()
                .build();
    }

    private static Parameter<String> keyParameter() {
        return ParameterBuilder.<String>begin("key")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedFrom(() -> KeyNames.suggestions())
                .required()
                .build();
    }

    private static Parameter<String> playerNameParameter(String name) {
        return ParameterBuilder.<String>begin(name)
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedWith((begin, ignored) -> MC.getConnection() == null
                        ? List.of()
                        : MC.getConnection().getOnlinePlayers().stream()
                                .map(info -> info.getProfile().name())
                                .filter(value -> startsWithIgnoreCase(value, begin))
                                .toList())
                .required()
                .build();
    }

    private static Parameter<String> friendNameParameter() {
        return ParameterBuilder.<String>begin("name")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedWith((begin, ignored) -> FriendManager.INSTANCE.all().stream()
                        .filter(value -> startsWithIgnoreCase(value, begin))
                        .toList())
                .required()
                .build();
    }

    private static Parameter<String> stringParameter(String name, boolean required) {
        ParameterBuilder<String> builder = ParameterBuilder.<String>begin(name)
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR);
        return (required ? builder.required() : builder.optional()).build();
    }

    private static Parameter<String> choiceParameter(
            String name, List<String> choices, boolean required, boolean vararg) {
        ParameterBuilder<String> builder = ParameterBuilder.<String>begin(name)
                .verifiedBy(input -> choices.stream().filter(value -> value.equalsIgnoreCase(input)).findFirst()
                        .<Parameter.VerificationResult<? extends String>>map(Parameter.Ok::new)
                        .orElseGet(() -> new Parameter.Error<>(input + " is not a valid choice")))
                .autocompletedWith((begin, ignored) -> choices.stream()
                        .filter(value -> startsWithIgnoreCase(value, begin)).toList());
        if (vararg) builder.vararg();
        return (required ? builder.required() : builder.optional()).build();
    }

    private static Parameter<String> profileParameter() {
        return ParameterBuilder.<String>begin("name")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedWith((begin, ignored) -> ConfigManager.INSTANCE.listProfiles().stream()
                        .filter(value -> startsWithIgnoreCase(value, begin)).toList())
                .required()
                .build();
    }

    private static Parameter<String> settingPathParameter() {
        return ParameterBuilder.<String>begin("path")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedWith((begin, ignored) -> allSettingPaths().stream()
                        .filter(value -> startsWithIgnoreCase(value, begin)).toList())
                .required()
                .build();
    }

    private static Parameter<String> valueParameter() {
        return ParameterBuilder.<String>begin("value")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .autocompletedWith((begin, args) -> {
                    if (args.size() < 2) return List.of();
                    Setting<?> setting = findSetting(args.get(1));
                    if (setting instanceof BooleanSetting) {
                        return List.of("true", "false").stream()
                                .filter(value -> startsWithIgnoreCase(value, begin)).toList();
                    }
                    if (setting instanceof EnumSetting<?> enums) {
                        return Arrays.stream(enums.values()).map(Enum::name)
                                .filter(value -> startsWithIgnoreCase(value, begin)).toList();
                    }
                    return List.of();
                })
                .required()
                .build();
    }

    private static Module findModule(String input) {
        if (input == null) return null;
        String normalized = StringUtil.slug(input).replace("_", "");
        return ModuleManager.INSTANCE.modules().stream()
                .filter(module -> module.id().equalsIgnoreCase(input)
                        || module.name().equalsIgnoreCase(input)
                        || StringUtil.slug(module.name()).replace("_", "").equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private static List<String> allSettingPaths() {
        List<String> paths = new ArrayList<>();
        for (Module module : ModuleManager.INSTANCE.modules()) {
            for (Setting<?> setting : module.settings()) {
                paths.add(module.id() + "." + StringUtil.slug(setting.name()));
            }
        }
        paths.sort(String.CASE_INSENSITIVE_ORDER);
        return paths;
    }

    private static Setting<?> findSetting(String path) {
        if (path == null) return null;
        int separator = path.indexOf('.');
        if (separator < 1 || separator == path.length() - 1) return null;
        Module module = findModule(path.substring(0, separator));
        if (module == null) return null;
        String key = StringUtil.slug(path.substring(separator + 1));
        return module.settings().stream()
                .filter(setting -> StringUtil.slug(setting.name()).equalsIgnoreCase(key)
                        || setting.name().equalsIgnoreCase(path.substring(separator + 1)))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setSettingFromString(Setting<?> setting, String input) {
        try {
            if (setting instanceof BooleanSetting booleanSetting) {
                switch (input.toLowerCase(Locale.ROOT)) {
                    case "yes", "on", "true" -> booleanSetting.set(true);
                    case "no", "off", "false" -> booleanSetting.set(false);
                    default -> throw new IllegalArgumentException("Not a boolean");
                }
            } else if (setting instanceof IntSetting intSetting) {
                intSetting.set(Integer.parseInt(input));
            } else if (setting instanceof DoubleSetting doubleSetting) {
                doubleSetting.set(Double.parseDouble(input));
            } else if (setting instanceof StringSetting stringSetting) {
                stringSetting.set(input);
            } else if (setting instanceof KeybindSetting keybindSetting) {
                int key = input.equalsIgnoreCase("none") ? KeybindSetting.NONE : KeyNames.parse(input);
                if (key == GLFW.GLFW_KEY_UNKNOWN) throw new IllegalArgumentException("Unknown key");
                keybindSetting.set(key);
            } else if (setting instanceof ColorSetting colorSetting) {
                String normalized = input.startsWith("#") ? input.substring(1) : input;
                long value = Long.parseUnsignedLong(normalized, 16);
                if (normalized.length() <= 6) value |= 0xFF000000L;
                colorSetting.set(new Color((int) value, true));
            } else if (setting instanceof EnumSetting enumSetting) {
                Enum<?> match = Arrays.stream(enumSetting.values())
                        .filter(value -> value.name().equalsIgnoreCase(input))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown choice"));
                enumSetting.set(match);
            } else {
                throw new IllegalArgumentException("Unsupported setting type");
            }
        } catch (RuntimeException error) {
            throw new CommandException("Could not set " + setting.name() + ": " + error.getMessage());
        }
    }

    private static String formatSetting(Setting<?> setting) {
        if (setting instanceof ColorSetting color) return String.format("#%08X", color.argb());
        if (setting instanceof KeybindSetting key) return KeybindUtils.format(key.get());
        if (setting instanceof EnumSetting<?> choice) return choice.get().name();
        return String.valueOf(setting.get());
    }

    private static String coordinatesText() {
        BlockPos pos = MC.player.blockPosition();
        String dimension = MC.level.dimension().identifier().getPath();
        return "x: " + pos.getX() + ", y: " + pos.getY() + ", z: " + pos.getZ()
                + " in the " + dimension;
    }

    private static void saveConfig() {
        try {
            ConfigManager.INSTANCE.saveChecked();
        } catch (IOException | RuntimeException error) {
            throw new CommandException("Could not save the config: " + safeMessage(error));
        }
    }

    private static void reply(String message) {
        ChatUtils.addChatMessage(message);
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "unknown error" : error.getMessage();
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static final class KeyNames {
        private static final Map<String, Integer> KEYS = buildKeys();
        private static final List<String> SUGGESTIONS = buildSuggestions();

        private static int parse(String input) {
            String normalized = normalize(input);
            if (normalized.contains("+")) {
                return GLFW.GLFW_KEY_UNKNOWN;
            }
            Integer mouse = parseMouse(normalized);
            if (mouse != null) {
                return KeybindUtils.encodeMouseButton(mouse);
            }
            return KEYS.getOrDefault(normalized, GLFW.GLFW_KEY_UNKNOWN);
        }

        private static List<String> suggestions() {
            return SUGGESTIONS;
        }

        private static Map<String, Integer> buildKeys() {
            Map<String, Integer> keys = new LinkedHashMap<>();
            for (Field field : GLFW.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class
                        || !field.getName().startsWith("GLFW_KEY_")
                        || field.getName().equals("GLFW_KEY_UNKNOWN")
                        || field.getName().equals("GLFW_KEY_LAST")) {
                    continue;
                }
                try {
                    keys.put(field.getName().substring("GLFW_KEY_".length()), field.getInt(null));
                } catch (IllegalAccessException ignored) {
                }
            }
            alias(keys, "ESC", "ESCAPE");
            alias(keys, "RETURN", "ENTER");
            alias(keys, "DEL", "DELETE");
            alias(keys, "INS", "INSERT");
            alias(keys, "PGUP", "PAGE_UP");
            alias(keys, "PGDN", "PAGE_DOWN");
            alias(keys, "LCTRL", "LEFT_CONTROL");
            alias(keys, "RCTRL", "RIGHT_CONTROL");
            alias(keys, "LSHIFT", "LEFT_SHIFT");
            alias(keys, "RSHIFT", "RIGHT_SHIFT");
            alias(keys, "LALT", "LEFT_ALT");
            alias(keys, "RALT", "RIGHT_ALT");
            return Map.copyOf(keys);
        }

        private static List<String> buildSuggestions() {
            Set<String> values = new LinkedHashSet<>();
            values.add("none");
            values.add("mouse.left");
            values.add("mouse.right");
            values.add("mouse.middle");
            for (int button = 4; button <= 8; button++) {
                values.add("mouse." + button);
            }
            KEYS.keySet().stream().map(value -> value.toLowerCase(Locale.ROOT)).forEach(values::add);
            return values.stream().sorted().toList();
        }

        private static void alias(Map<String, Integer> keys, String alias, String target) {
            Integer key = keys.get(target);
            if (key != null) keys.put(alias, key);
        }

        private static String normalize(String input) {
            String normalized = input == null ? "" : input.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            if (normalized.startsWith("KEY.")) {
                normalized = normalized.substring("KEY.".length());
            }
            if (normalized.startsWith("KEYBOARD.")) {
                normalized = normalized.substring("KEYBOARD.".length());
            }
            if (normalized.startsWith("GLFW_KEY_")) {
                normalized = normalized.substring("GLFW_KEY_".length());
            }
            return normalized.replace('.', '_');
        }

        private static Integer parseMouse(String normalized) {
            if (!normalized.startsWith("MOUSE")) {
                return null;
            }
            String name = normalized.substring("MOUSE".length());
            if (name.startsWith("_")) {
                name = name.substring(1);
            }
            return switch (name) {
                case "LEFT", "1" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
                case "RIGHT", "2" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                case "MIDDLE", "3" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
                case "4" -> GLFW.GLFW_MOUSE_BUTTON_4;
                case "5" -> GLFW.GLFW_MOUSE_BUTTON_5;
                case "6" -> GLFW.GLFW_MOUSE_BUTTON_6;
                case "7" -> GLFW.GLFW_MOUSE_BUTTON_7;
                case "8" -> GLFW.GLFW_MOUSE_BUTTON_8;
                default -> null;
            };
        }
    }
}
