package com.setsuna.command.builder;

import com.setsuna.command.Command;
import com.setsuna.command.Parameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fluent builder matching LiquidBounce's command node invariants. */
public final class CommandBuilder {

    private final String name;
    private List<String> aliases = List.of();
    private final List<Parameter<?>> parameters = new ArrayList<>();
    private final List<Command> subcommands = new ArrayList<>();
    private Command.Handler handler;
    private boolean executable = true;
    private boolean ingame;
    private String description = "";

    private CommandBuilder(String name) {
        this.name = name;
    }

    public static CommandBuilder begin(String name) {
        return new CommandBuilder(name);
    }

    public CommandBuilder alias(String... aliases) {
        this.aliases = List.copyOf(Arrays.asList(aliases));
        return this;
    }

    public CommandBuilder parameter(Parameter<?> parameter) {
        parameters.add(parameter);
        return this;
    }

    public CommandBuilder subcommand(Command subcommand) {
        subcommands.add(subcommand);
        return this;
    }

    public CommandBuilder subcommand(Command.Factory factory) {
        return subcommand(factory.createCommand());
    }

    public CommandBuilder handler(Command.Handler handler) {
        this.handler = handler;
        return this;
    }

    public CommandBuilder requiresIngame() {
        ingame = true;
        return this;
    }

    public CommandBuilder hub() {
        executable = false;
        return this;
    }

    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    public Command build() {
        if (!executable && handler != null) {
            throw new IllegalStateException("The command is marked as not executable (hub), but a handler was specified");
        }
        if (executable && handler == null) {
            throw new IllegalStateException("The command is marked as executable, but no handler was specified.");
        }

        boolean wasOptional = false;
        boolean wasVararg = false;
        for (Parameter<?> parameter : parameters) {
            if (parameter.required() && wasOptional) {
                throw new IllegalStateException("Optional parameters are only allowed at the end");
            }
            if (parameter.required() && wasVararg) {
                throw new IllegalStateException("VarArgs are only allowed at the end");
            }
            wasOptional = !parameter.required();
            wasVararg = parameter.vararg();
        }

        return new Command(
                name, aliases, parameters, subcommands, executable, handler, ingame, description);
    }
}
