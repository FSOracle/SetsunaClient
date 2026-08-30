package com.setsuna.command;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.setsuna.Setsuna;
import com.setsuna.command.commands.BuiltInCommands;
import com.setsuna.util.player.ChatUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/** LiquidBounce-compatible command registry, tokenizer, parser and completer. */
public final class CommandManager {

    public static final CommandManager INSTANCE = new CommandManager();
    public static final String DEFAULT_PREFIX = ".";
    public static final int DEFAULT_HINT_COUNT = 5;

    private final NavigableMap<String, Command> rootCommandMap =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final TreeSet<Command> commandSet = new TreeSet<>();
    private final CommandExecutor executor = new CommandExecutor(this);

    private volatile String prefix = DEFAULT_PREFIX;
    private volatile int hintCount = DEFAULT_HINT_COUNT;
    private boolean initialized;

    private CommandManager() {
        BuiltInCommands.register(this);
    }

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Setsuna.LOGGER.info("Registered {} client commands with prefix {}", commandSet.size(), prefix);
    }

    /** @return true when the message is a client command and must not be sent. */
    public boolean handle(String message) {
        String activePrefix = prefix;
        if (message == null || activePrefix.isEmpty() || !message.startsWith(activePrefix)) {
            return false;
        }
        if (!initialized) {
            return true;
        }

        String commandBody = message.substring(activePrefix.length());
        try {
            execute(commandBody);
        } catch (Throwable error) {
            executor.handleException(error);
        } finally {
            appendHistory(commandBody);
        }
        return true;
    }

    /** Applies the first LiquidBounce-style completion to the chat input. */
    public String complete(String message, int cursor) {
        if (message == null || !message.startsWith(prefix) || !initialized) {
            return null;
        }
        int safeCursor = Math.max(0, Math.min(cursor, message.length()));
        Suggestions suggestions = autoComplete(message, safeCursor).getNow(null);
        if (suggestions == null || suggestions.getList().isEmpty()) {
            return null;
        }
        Suggestion suggestion = suggestions.getList().getFirst();
        return suggestion.apply(message);
    }

    public synchronized void addCommand(Command command) {
        Objects.requireNonNull(command, "command");
        if (!commandSet.add(command)) {
            throw new IllegalStateException("Command '" + command.name() + "' already exists");
        }
        try {
            Command.putCommand(rootCommandMap, command);
        } catch (RuntimeException error) {
            commandSet.remove(command);
            throw error;
        }
    }

    public synchronized void removeCommand(Command command) {
        if (!commandSet.remove(command)
                || rootCommandMap.remove(command.name()) != command) {
            throw new IllegalStateException("Command '" + command.name() + "' does not exist");
        }
        for (String alias : command.aliases()) {
            if (rootCommandMap.remove(alias) != command) {
                throw new IllegalStateException("Command alias '" + alias + "' is not registered");
            }
        }
    }

    public List<Command> commands() {
        return List.copyOf(commandSet);
    }

    public String prefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty() || prefix.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Command prefix must be non-empty and contain no whitespace");
        }
        this.prefix = prefix;
    }

    public int hintCount() {
        return hintCount;
    }

    public void setHintCount(int hintCount) {
        this.hintCount = Math.max(0, Math.min(10, hintCount));
    }

    public void execute(String commandLine) {
        List<String> args = tokenizeCommand(commandLine).tokens();
        if (args.isEmpty()) {
            return;
        }

        ResolvedSubCommand resolved = getSubCommand(args, null, 0);
        if (resolved == null) {
            throw new CommandException(
                    "Command \"" + args.getFirst() + "\" was not found.",
                    unknownCommandHints(args.getFirst()));
        }

        Command command = resolved.command();
        if (!command.executable()) {
            throw new CommandException(
                    "Invalid usage of command \"" + args.getFirst() + "\".", command.usage());
        }
        if (command.requiresIngame() && (Setsuna.mc().player == null || Setsuna.mc().level == null)) {
            throw new CommandException("This command can only be used in game.", command.usage());
        }

        int commandIndex = resolved.index();
        int remainingArgsCount = args.size() - commandIndex - 1;
        if (command.parameters().isEmpty() && commandIndex != args.size() - 1) {
            throw new CommandException("Command does not take any arguments.", command.usage());
        }
        if (remainingArgsCount < command.parameters().size()
                && command.parameters().get(remainingArgsCount).required()) {
            throw new CommandException(
                    "Parameter \"" + command.parameters().get(remainingArgsCount).name() + "\" is required.",
                    command.usage());
        }

        Object[] parsedParameters = new Object[remainingArgsCount];
        for (int inputIndex = commandIndex + 1; inputIndex < args.size(); inputIndex++) {
            int parameterIndex = inputIndex - commandIndex - 1;
            if (parameterIndex >= command.parameters().size()) {
                throw new CommandException(
                        "Unknown parameter \"" + args.get(inputIndex) + "\".", command.usage());
            }

            Parameter<?> parameter = command.parameters().get(parameterIndex);
            Object value;
            if (parameter.vararg()) {
                Object[] values = new Object[args.size() - inputIndex];
                for (int varargIndex = inputIndex; varargIndex < args.size(); varargIndex++) {
                    values[varargIndex - inputIndex] = parseParameter(
                            command, args.get(varargIndex), parameter);
                }
                value = values;
            } else {
                value = parseParameter(command, args.get(inputIndex), parameter);
            }
            parsedParameters[parameterIndex] = value;
            if (parameter.vararg()) {
                break;
            }
        }

        Command.Context context = new Command.Context(command, parsedParameters);
        try {
            command.handler().handle(context);
        } catch (CommandException error) {
            throw error;
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private Object parseParameter(Command command, String argument, Parameter<?> parameter) {
        if (parameter.verifier() == null) {
            return argument;
        }
        Parameter.VerificationResult<?> result = parameter.verifier().verifyAndParse(argument);
        if (result instanceof Parameter.Ok<?> ok) {
            return ok.mappedResult();
        }
        Parameter.Error<?> error = (Parameter.Error<?>) result;
        // The argument order intentionally matches LiquidBounce's current implementation.
        throw new CommandException(
                "Invalid argument \"" + parameter.name() + "\" for parameter \"" + argument
                        + "\". " + error.errorMessage(),
                command.usage());
    }

    private ResolvedSubCommand getSubCommand(
            List<String> args, ResolvedSubCommand currentCommand, int index) {
        if (index >= args.size()) {
            return currentCommand;
        }
        Map<String, Command> commandMap = currentCommand == null
                ? rootCommandMap
                : currentCommand.command().subcommandMap();
        Command match = commandMap.get(args.get(index));
        if (match != null) {
            return getSubCommand(args, new ResolvedSubCommand(match, index), index + 1);
        }
        return currentCommand;
    }

    public TokenizationResult tokenizeCommand(String line) {
        List<String> output = new ArrayList<>();
        List<Integer> outputIndices = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        outputIndices.add(0);
        boolean escaped = false;
        boolean quote = false;
        int index = 0;

        for (char character : line.toCharArray()) {
            index++;
            if (escaped) {
                buffer.append(character);
                escaped = false;
                continue;
            }
            switch (character) {
                case '\\' -> escaped = true;
                case '"' -> {
                    quote = !quote;
                    buffer.append(character);
                }
                case ' ' -> {
                    if (quote) {
                        buffer.append(character);
                    } else if (!isBlank(buffer)) {
                        output.add(stripOuterQuotes(buffer));
                        buffer.setLength(0);
                        outputIndices.add(index);
                    }
                }
                default -> buffer.append(character);
            }
        }
        if (!isBlank(buffer)) {
            output.add(stripOuterQuotes(buffer));
        }
        return new TokenizationResult(List.copyOf(output), List.copyOf(outputIndices));
    }

    public CompletableFuture<Suggestions> autoComplete(String originalCommand, int start) {
        String activePrefix = prefix;
        if (start < activePrefix.length()) {
            return Suggestions.empty();
        }
        try {
            String commandLine = originalCommand.substring(activePrefix.length(), start);
            TokenizationResult tokenized = tokenizeCommand(commandLine);
            List<String> args = tokenized.tokens();
            if (args.isEmpty()) {
                args = List.of("");
            }

            boolean nextParameter = !args.getLast().endsWith(" ") && commandLine.endsWith(" ");
            int currentArgumentStart = tokenized.tokenStartIndices().isEmpty()
                    ? 0
                    : tokenized.tokenStartIndices().getLast();
            if (nextParameter) {
                currentArgumentStart = commandLine.length();
            }
            SuggestionsBuilder builder = new SuggestionsBuilder(
                    originalCommand, currentArgumentStart + activePrefix.length());
            ResolvedSubCommand resolved = getSubCommand(args, null, 0);

            if (args.size() == 1 && (resolved == null || !nextParameter)) {
                String argument = args.getFirst();
                for (Map.Entry<String, Command> entry : rootCommandMap.entrySet()) {
                    if (startsWithIgnoreCase(entry.getKey(), argument)) {
                        builder.suggest(entry.getValue().name());
                    }
                }
                return builder.buildFuture();
            }
            if (resolved == null) {
                return Suggestions.empty();
            }
            resolved.command().autoComplete(
                    builder, new TokenizationResult(args, tokenized.tokenStartIndices()),
                    resolved.index(), nextParameter);
            return builder.buildFuture();
        } catch (Exception error) {
            Setsuna.LOGGER.error("Failed to supply autocompletion suggestions for '{}'", originalCommand, error);
            return Suggestions.empty();
        }
    }

    CommandExecutor executor() {
        return executor;
    }

    private List<String> unknownCommandHints(String unknown) {
        if (rootCommandMap.isEmpty() || hintCount == 0) {
            return List.of();
        }
        return commandSet.stream()
                .sorted(Comparator.comparingInt(command -> commandDistance(unknown, command)))
                .limit(hintCount)
                .map(command -> command.aliases().isEmpty()
                        ? command.name()
                        : command.name() + " (" + String.join(", ", command.aliases()) + ")")
                .toList();
    }

    private static int commandDistance(String unknown, Command command) {
        int distance = levenshtein(unknown, command.name());
        for (String alias : command.aliases()) {
            distance = Math.min(distance, levenshtein(unknown, alias));
        }
        return distance;
    }

    static int levenshtein(CharSequence left, CharSequence right) {
        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0) return rightLength;
        if (rightLength == 0) return leftLength;
        int[] cost = new int[leftLength + 1];
        for (int index = 0; index <= leftLength; index++) cost[index] = index;
        for (int i = 1; i <= rightLength; i++) {
            int previousCost = cost[0];
            cost[0] = i;
            for (int j = 1; j <= leftLength; j++) {
                int currentCost = cost[j];
                int match = left.charAt(j - 1) == right.charAt(i - 1) ? 0 : 1;
                cost[j] = Math.min(Math.min(cost[j] + 1, cost[j - 1] + 1), previousCost + match);
                previousCost = currentCost;
            }
        }
        return cost[leftLength];
    }

    private void appendHistory(String commandBody) {
        try {
            Path directory = Setsuna.mc().gameDirectory.toPath().resolve(Setsuna.MOD_ID);
            Files.createDirectories(directory);
            Files.writeString(
                    directory.resolve("command_history.txt"),
                    commandBody + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException error) {
            Setsuna.LOGGER.warn("Could not append command history", error);
        }
    }

    private static String stripOuterQuotes(CharSequence token) {
        if (token.length() >= 2 && token.charAt(0) == '"' && token.charAt(token.length() - 1) == '"') {
            return token.subSequence(1, token.length() - 1).toString();
        }
        return token.toString();
    }

    private static boolean isBlank(CharSequence value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) return false;
        }
        return true;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private record ResolvedSubCommand(Command command, int index) {
    }

    public record TokenizationResult(List<String> tokens, List<Integer> tokenStartIndices) {
    }
}
