package com.setsuna.command;

import com.setsuna.Setsuna;
import com.setsuna.util.player.ChatUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bridges command failures and non-blocking handlers to Minecraft chat. */
public final class CommandExecutor {

    private final CommandManager manager;
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<String> runningCommands = ConcurrentHashMap.newKeySet();

    CommandExecutor(CommandManager manager) {
        this.manager = manager;
    }

    public void submit(Command command, boolean allowParallel, ThrowingRunnable handler) {
        String key = command.name().toLowerCase(java.util.Locale.ROOT);
        if (!allowParallel && !runningCommands.add(key)) {
            ChatUtils.addChatMessage("Error: Command \"" + command.name()
                    + "\" is already executing, please wait...");
            return;
        }
        asyncExecutor.submit(() -> {
            try {
                handler.run();
            } catch (Throwable error) {
                Setsuna.mc().execute(() -> handleException(error));
            } finally {
                if (!allowParallel) {
                    runningCommands.remove(key);
                }
            }
        });
    }

    void handleException(Throwable error) {
        if (error instanceof CommandException commandError) {
            ChatUtils.addChatMessage("Error: " + commandError.getMessage());
            if (!commandError.usageInfo().isEmpty()) {
                ChatUtils.addChatMessage("Usage:");
                for (String usage : commandError.usageInfo()) {
                    ChatUtils.addChatMessage("  " + manager.prefix() + usage);
                }
            }
            return;
        }
        Throwable cause = error.getCause() == null ? error : error.getCause();
        ChatUtils.addChatMessage("Error: Failed to execute command. "
                + cause.getClass().getSimpleName() + ": "
                + (cause.getMessage() == null ? "No message" : cause.getMessage()));
        Setsuna.LOGGER.error("An exception occurred while executing a command", error);
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
