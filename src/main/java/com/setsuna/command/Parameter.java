package com.setsuna.command;

import java.util.Objects;
import java.util.function.Supplier;

/** A typed command parameter and its parser. */
public final class Parameter<T> {

    private final String name;
    private final boolean required;
    private final T defaultValue;
    private final boolean vararg;
    private final Verifier<T> verifier;
    private final AutoCompletionProvider autocompletionHandler;

    private Command command;
    private int index = -1;

    public Parameter(
            String name,
            boolean required,
            T defaultValue,
            boolean vararg,
            Verifier<T> verifier,
            AutoCompletionProvider autocompletionHandler) {
        this.name = Objects.requireNonNull(name, "name");
        this.required = required;
        this.defaultValue = defaultValue;
        this.vararg = vararg;
        this.verifier = verifier;
        this.autocompletionHandler = autocompletionHandler;
    }

    public String name() {
        return name;
    }

    public boolean required() {
        return required;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public boolean vararg() {
        return vararg;
    }

    public Verifier<T> verifier() {
        return verifier;
    }

    public AutoCompletionProvider autocompletionHandler() {
        return autocompletionHandler;
    }

    public Command command() {
        return command;
    }

    public int index() {
        return index;
    }

    void attach(Command owner, int parameterIndex) {
        if (command != null) {
            throw new IllegalStateException("Parameter already has a command");
        }
        command = owner;
        index = parameterIndex;
    }

    public String usageName() {
        String value = required ? "<" + name + ">" : "[<" + name + ">]";
        return vararg ? value + "..." : value;
    }

    public T value(Command.Context context) {
        requireOwner(context.command());
        Object value = index < context.args().length ? context.args()[index] : null;
        if (value == null) {
            return Objects.requireNonNull(defaultValue, "Parameter '" + name + "' has no default value.");
        }
        @SuppressWarnings("unchecked")
        T cast = (T) value;
        return cast;
    }

    public T optionalValue(Command.Context context) {
        requireOwner(context.command());
        if (index >= context.args().length) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T cast = (T) context.args()[index];
        return cast;
    }

    public Object[] varargValues(Command.Context context) {
        requireOwner(context.command());
        if (index >= context.args().length || context.args()[index] == null) {
            return null;
        }
        return (Object[]) context.args()[index];
    }

    private void requireOwner(Command contextCommand) {
        if (command != contextCommand || index < 0 || command.parameters().get(index) != this) {
            throw new IllegalArgumentException("Parameter is not part of command '" + contextCommand.name() + "'");
        }
    }

    @FunctionalInterface
    public interface Verifier<T> {
        VerificationResult<? extends T> verifyAndParse(String sourceText);
    }

    public sealed interface VerificationResult<T> permits Ok, Error {

        static <T> VerificationResult<T> ofNullable(T value, Supplier<String> errorMessage) {
            return value == null ? new Error<>(errorMessage.get()) : new Ok<>(value);
        }
    }

    public record Ok<T>(T mappedResult) implements VerificationResult<T> {
    }

    public record Error<T>(String errorMessage) implements VerificationResult<T> {
        public Error {
            Objects.requireNonNull(errorMessage, "errorMessage");
        }
    }
}
