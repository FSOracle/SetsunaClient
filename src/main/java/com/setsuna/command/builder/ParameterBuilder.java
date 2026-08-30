package com.setsuna.command.builder;

import com.setsuna.command.AutoCompletionProvider;
import com.setsuna.command.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Fluent parameter builder and LiquidBounce-compatible primitive validators. */
public final class ParameterBuilder<T> {

    public static final Parameter.Verifier<String> STRING_VALIDATOR = Parameter.Ok::new;
    public static final Parameter.Verifier<Integer> INTEGER_VALIDATOR = source -> {
        try {
            return new Parameter.Ok<>(Integer.parseInt(source));
        } catch (NumberFormatException ignored) {
            return new Parameter.Error<>("'" + source + "' is not a valid integer");
        }
    };
    public static final Parameter.Verifier<Integer> POSITIVE_INTEGER_VALIDATOR = source -> {
        Parameter.VerificationResult<? extends Integer> parsed = INTEGER_VALIDATOR.verifyAndParse(source);
        if (parsed instanceof Parameter.Error<? extends Integer> error) return error;
        int value = ((Parameter.Ok<? extends Integer>) parsed).mappedResult();
        return value > 0 ? new Parameter.Ok<>(value) : new Parameter.Error<>("The integer must be positive");
    };
    public static final Parameter.Verifier<Integer> NON_NEGATIVE_INTEGER_VALIDATOR = source -> {
        Parameter.VerificationResult<? extends Integer> parsed = INTEGER_VALIDATOR.verifyAndParse(source);
        if (parsed instanceof Parameter.Error<? extends Integer> error) return error;
        int value = ((Parameter.Ok<? extends Integer>) parsed).mappedResult();
        return value >= 0 ? new Parameter.Ok<>(value) : new Parameter.Error<>("The integer must not be negative");
    };
    public static final Parameter.Verifier<Float> FLOAT_VALIDATOR = source -> {
        try {
            return new Parameter.Ok<>(Float.parseFloat(source));
        } catch (NumberFormatException ignored) {
            return new Parameter.Error<>("'" + source + "' is not a valid float");
        }
    };
    public static final Parameter.Verifier<Float> POSITIVE_FLOAT_VALIDATOR = source -> {
        Parameter.VerificationResult<? extends Float> parsed = FLOAT_VALIDATOR.verifyAndParse(source);
        if (parsed instanceof Parameter.Error<? extends Float> error) return error;
        float value = ((Parameter.Ok<? extends Float>) parsed).mappedResult();
        return value > 0.0F ? new Parameter.Ok<>(value) : new Parameter.Error<>("The float must be positive");
    };
    public static final Parameter.Verifier<Boolean> BOOLEAN_VALIDATOR = source -> switch (source.toLowerCase(Locale.ROOT)) {
        case "yes", "on", "true" -> new Parameter.Ok<>(true);
        case "no", "off", "false" -> new Parameter.Ok<>(false);
        default -> new Parameter.Error<>("'" + source + "' is not a valid boolean");
    };

    private final String name;
    private Parameter.Verifier<T> verifier;
    private Boolean required;
    private T defaultValue;
    private boolean vararg;
    private AutoCompletionProvider autocompletionHandler;

    private ParameterBuilder(String name) {
        this.name = name;
    }

    public static <T> ParameterBuilder<T> begin(String name) {
        return new ParameterBuilder<>(name);
    }

    public static Parameter.Verifier<Integer> intRange(int min, int max) {
        return source -> {
            Parameter.VerificationResult<? extends Integer> parsed = INTEGER_VALIDATOR.verifyAndParse(source);
            if (parsed instanceof Parameter.Error<? extends Integer> error) return error;
            int value = ((Parameter.Ok<? extends Integer>) parsed).mappedResult();
            return value >= min && value <= max
                    ? new Parameter.Ok<>(value)
                    : new Parameter.Error<>("The integer must be between " + min + " and " + max);
        };
    }

    public static Parameter.Verifier<Float> floatRange(float min, float max) {
        return source -> {
            Parameter.VerificationResult<? extends Float> parsed = FLOAT_VALIDATOR.verifyAndParse(source);
            if (parsed instanceof Parameter.Error<? extends Float> error) return error;
            float value = ((Parameter.Ok<? extends Float>) parsed).mappedResult();
            return value >= min && value <= max
                    ? new Parameter.Ok<>(value)
                    : new Parameter.Error<>("The float must be between " + min + " and " + max);
        };
    }

    public ParameterBuilder<T> verifiedBy(Parameter.Verifier<T> verifier) {
        this.verifier = verifier;
        return this;
    }

    public ParameterBuilder<T> optional() {
        return optional(null);
    }

    public ParameterBuilder<T> optional(T defaultValue) {
        required = false;
        this.defaultValue = defaultValue;
        return this;
    }

    public ParameterBuilder<T> required() {
        required = true;
        return this;
    }

    public ParameterBuilder<T> vararg() {
        vararg = true;
        return this;
    }

    public ParameterBuilder<T> autocompletedWith(AutoCompletionProvider provider) {
        autocompletionHandler = provider;
        return this;
    }

    public ParameterBuilder<T> autocompletedFrom(Supplier<? extends Iterable<String>> provider) {
        return autocompletedFrom(true, false, provider);
    }

    public ParameterBuilder<T> autocompletedFrom(
            boolean ignoreCase,
            boolean minecraftPlaceholders,
            Supplier<? extends Iterable<String>> provider) {
        return autocompletedWith((begin, ignored) -> {
            Iterable<String> placeholders = provider.get();
            if (placeholders == null) {
                return List.of();
            }
            List<String> output = new ArrayList<>();
            for (String value : placeholders) {
                if (startsWith(value, begin, ignoreCase)
                        || minecraftPlaceholders && startsWith(removeMinecraftPrefix(value), begin, ignoreCase)) {
                    output.add(value);
                }
            }
            return output;
        });
    }

    public Parameter<T> build() {
        if (required == null) {
            throw new IllegalArgumentException("The parameter was neither marked as required nor as optional.");
        }
        return new Parameter<>(
                name, required, defaultValue, vararg, verifier, autocompletionHandler);
    }

    private static boolean startsWith(String value, String prefix, boolean ignoreCase) {
        return ignoreCase
                ? value.regionMatches(true, 0, prefix, 0, prefix.length())
                : value.startsWith(prefix);
    }

    private static String removeMinecraftPrefix(String value) {
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }
}
