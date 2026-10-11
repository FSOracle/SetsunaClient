package com.setsuna.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.setsuna.command.builder.CommandBuilder;
import com.setsuna.command.builder.ParameterBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandManagerTest {

    private final CommandManager manager = CommandManager.INSTANCE;

    @Test
    void tokenizesQuotesEscapesAndRepeatedSpacesLikeLiquidBounce() {
        CommandManager.TokenizationResult quoted = manager.tokenizeCommand("friend add \"Senk Ju\"");
        assertEquals(List.of("friend", "add", "Senk Ju"), quoted.tokens());
        assertEquals(List.of(0, 7, 11), quoted.tokenStartIndices());

        assertEquals(
                List.of("say", "one two", "three\"four"),
                manager.tokenizeCommand("say one\\ two three\\\"four").tokens());
        assertEquals(
                List.of("friend", "add"),
                manager.tokenizeCommand("  friend   add  ").tokens());
        assertEquals(
                List.of("say", "\"unterminated"),
                manager.tokenizeCommand("say \"unterminated").tokens());
    }

    @Test
    void executesCaseInsensitiveAliasesNestedCommandsAndTypedVarargs() {
        AtomicReference<Object[]> received = new AtomicReference<>();
        Parameter<Integer> number = ParameterBuilder.<Integer>begin("number")
                .verifiedBy(ParameterBuilder.INTEGER_VALIDATOR)
                .required()
                .vararg()
                .build();
        Command child = CommandBuilder.begin("child")
                .alias("c")
                .parameter(number)
                .handler(context -> received.set((Object[]) context.arg(0)))
                .build();
        Command root = CommandBuilder.begin("codex_command_test")
                .alias("codex_alias_test")
                .hub()
                .subcommand(child)
                .build();

        manager.addCommand(root);
        try {
            manager.execute("CODEX_ALIAS_TEST C 3 7 11");
            assertArrayEquals(new Object[]{3, 7, 11}, received.get());
        } finally {
            manager.removeCommand(root);
        }
    }

    @Test
    void rejectsRequiredParametersAfterOptionalParameters() {
        Parameter<String> optional = ParameterBuilder.<String>begin("optional").optional().build();
        Parameter<String> required = ParameterBuilder.<String>begin("required").required().build();

        assertThrows(IllegalStateException.class, () -> CommandBuilder.begin("invalid")
                .parameter(optional)
                .parameter(required)
                .handler(context -> {
                })
                .build());
    }

    @Test
    void generatesRecursiveUsageForHubCommands() {
        Command child = CommandBuilder.begin("set")
                .parameter(ParameterBuilder.<String>begin("value").required().build())
                .handler(context -> {
                })
                .build();
        Command root = CommandBuilder.begin("setting").hub().subcommand(child).build();

        assertEquals(List.of("setting set <value>"), root.usage());
    }

    @Test
    void completesRootAliasesAndNestedSubcommands() {
        Command child = CommandBuilder.begin("nested")
                .handler(context -> {
                })
                .build();
        Command root = CommandBuilder.begin("codex_completion_test")
                .alias("codex_short_test")
                .hub()
                .subcommand(child)
                .build();
        manager.addCommand(root);
        try {
            String aliasInput = manager.prefix() + "codex_sh";
            Suggestions rootSuggestions = manager.autoComplete(aliasInput, aliasInput.length()).join();
            assertTrue(rootSuggestions.getList().stream()
                    .anyMatch(suggestion -> suggestion.getText().equals("codex_completion_test")));

            String nestedInput = manager.prefix() + "codex_completion_test n";
            Suggestions nestedSuggestions = manager.autoComplete(nestedInput, nestedInput.length()).join();
            assertTrue(nestedSuggestions.getList().stream()
                    .anyMatch(suggestion -> suggestion.getText().equals("nested")));
        } finally {
            manager.removeCommand(root);
        }
    }

    @Test
    void validatesAndClampsGlobalCommandSettings() {
        String previousPrefix = manager.prefix();
        int previousHintCount = manager.hintCount();
        try {
            manager.setPrefix("!!");
            manager.setHintCount(99);
            assertEquals("!!", manager.prefix());
            assertEquals(10, manager.hintCount());
            assertThrows(IllegalArgumentException.class, () -> manager.setPrefix("bad prefix"));
        } finally {
            manager.setPrefix(previousPrefix);
            manager.setHintCount(previousHintCount);
        }
    }

    @Test
    void retainsLocalCommandsWithoutRegisteringCloudChat() {
        List<String> names = manager.commands().stream().map(Command::name).toList();
        assertTrue(names.containsAll(List.of("config", "localconfig", "toggle", "lua")));
        assertTrue(names.stream().noneMatch(name -> name.equalsIgnoreCase("irc")));
        assertThrows(CommandException.class, () -> manager.execute("irc hello"));
    }

    @Test
    void renameCommandExposesClientOrModuleSyntax() {
        Command rename = manager.commands().stream()
                .filter(command -> command.name().equals("rename"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("rename <moduelname> <name>..."), rename.usage());
    }
}
