package com.setsuna.command;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable command node with nested subcommands and typed parameters. */
public final class Command implements Comparable<Command> {

    private final String name;
    private final List<String> aliases;
    private final List<Parameter<?>> parameters;
    private final List<Command> subcommands;
    private final boolean executable;
    private final Handler handler;
    private final boolean requiresIngame;
    private final String description;
    private final Map<String, Command> subcommandMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private Command parentCommand;
    private int index = -1;

    public Command(
            String name,
            List<String> aliases,
            List<Parameter<?>> parameters,
            List<Command> subcommands,
            boolean executable,
            Handler handler,
            boolean requiresIngame,
            String description) {
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = List.copyOf(aliases);
        this.parameters = List.copyOf(parameters);
        this.subcommands = List.copyOf(subcommands);
        this.executable = executable;
        this.handler = handler;
        this.requiresIngame = requiresIngame;
        this.description = description == null ? "" : description;

        for (int i = 0; i < this.subcommands.size(); i++) {
            Command subcommand = this.subcommands.get(i);
            if (subcommand.parentCommand != null) {
                throw new IllegalStateException("Subcommand already has parent command");
            }
            putCommand(subcommandMap, subcommand);
            subcommand.index = i;
            subcommand.parentCommand = this;
        }
        for (int i = 0; i < this.parameters.size(); i++) {
            this.parameters.get(i).attach(this, i);
        }
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public List<Parameter<?>> parameters() {
        return parameters;
    }

    public List<Command> subcommands() {
        return subcommands;
    }

    public boolean executable() {
        return executable;
    }

    public Handler handler() {
        return handler;
    }

    public boolean requiresIngame() {
        return requiresIngame;
    }

    public String description() {
        return description;
    }

    public Command parentCommand() {
        return parentCommand;
    }

    public int index() {
        return index;
    }

    Map<String, Command> subcommandMap() {
        return subcommandMap;
    }

    public List<String> usage() {
        List<String> output = new ArrayList<>();
        if (executable) {
            List<String> parts = new ArrayList<>();
            for (Command command = this; command != null; command = command.parentCommand) {
                parts.add(command.name);
            }
            Collections.reverse(parts);
            for (Parameter<?> parameter : parameters) {
                parts.add(parameter.usageName());
            }
            output.add(String.join(" ", parts));
        }
        for (Command subcommand : subcommands) {
            output.addAll(subcommand.usage());
        }
        return output;
    }

    void autoComplete(
            SuggestionsBuilder builder,
            CommandManager.TokenizationResult tokenizationResult,
            int commandIndex,
            boolean isNewParameter) {
        List<String> args = tokenizationResult.tokens();
        int offset = args.size() - commandIndex - 1;
        boolean atSecondParameterBeginning = offset == 0 && isNewParameter;
        boolean inSecondParameter = offset == 1 && !isNewParameter;

        if (atSecondParameterBeginning || inSecondParameter) {
            String comparedAgainst = !isNewParameter ? args.get(offset) : "";
            for (Command subcommand : subcommands) {
                if (startsWithIgnoreCase(subcommand.name, comparedAgainst)) {
                    builder.suggest(subcommand.name);
                }
                for (String alias : subcommand.aliases) {
                    if (startsWithIgnoreCase(alias, comparedAgainst)) {
                        builder.suggest(alias);
                    }
                }
            }
        }

        int parameterIndex = args.size() - commandIndex - 2;
        if (isNewParameter) {
            parameterIndex++;
        }
        if (parameterIndex < 0) {
            return;
        }

        int tokenIndex = commandIndex + parameterIndex + 1;
        Parameter<?> parameter;
        if (parameterIndex >= parameters.size()) {
            Parameter<?> last = parameters.isEmpty() ? null : parameters.getLast();
            if (last == null || !last.vararg()) {
                return;
            }
            parameter = last;
        } else {
            parameter = parameters.get(parameterIndex);
        }

        AutoCompletionProvider provider = parameter.autocompletionHandler();
        if (provider == null) {
            return;
        }
        String begin = tokenIndex < args.size() ? args.get(tokenIndex) : "";
        for (String suggestion : provider.autocomplete(begin, args)) {
            builder.suggest(suggestion);
        }
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    static void putCommand(Map<String, Command> map, Command command) {
        putName(map, command.name, command);
        for (String alias : command.aliases) {
            putName(map, alias, command);
        }
    }

    private static void putName(Map<String, Command> map, String name, Command command) {
        Command previous = map.put(name, command);
        if (previous != null) {
            map.put(name, previous);
            throw new IllegalStateException(
                    "Command name '" + name + "' already used by command '" + previous.name + "'");
        }
    }

    @Override
    public int compareTo(Command other) {
        return String.CASE_INSENSITIVE_ORDER.compare(name, other.name);
    }

    @FunctionalInterface
    public interface Handler {
        void handle(Context context) throws Exception;
    }

    public record Context(Command command, Object[] args) {
        public Context {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(args, "args");
        }

        public Object arg(int index) {
            return args[index];
        }

        public Object optionalArg(int index) {
            return index < args.length ? args[index] : null;
        }
    }

    @FunctionalInterface
    public interface Factory {
        Command createCommand();
    }
}
