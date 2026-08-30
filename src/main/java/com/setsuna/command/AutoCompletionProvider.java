package com.setsuna.command;

import java.util.List;

/** Supplies completions for one command parameter. */
@FunctionalInterface
public interface AutoCompletionProvider {

    Iterable<String> autocomplete(String begin, List<String> args);
}
