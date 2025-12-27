package thunder.hack.features.cmd.args;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import thunder.hack.core.Managers;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public class CfgArgumentType implements ArgumentType<String> {
    private static final Collection<String> EXAMPLES = getExamplesList();

    private static Collection<String> getExamplesList() {
        List<String> configList = Managers.CONFIG.getConfigList();
        if (configList == null || configList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return configList.stream().limit(5).toList();
    }

    public static CfgArgumentType create() {
        return new CfgArgumentType();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String config = reader.readString();
        List<String> configList = Managers.CONFIG.getConfigList();
        if (configList == null || !configList.contains(config)) throw new DynamicCommandExceptionType(
                name -> Text.literal(isRu() ? "Конфига " + name.toString() + " не существует(" : "Config " + name.toString() + " does not exist :(")
        ).create(config);

        return config;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        List<String> configList = Managers.CONFIG.getConfigList();
        if (configList == null || configList.isEmpty()) {
            return Suggestions.empty();
        }
        return CommandSource.suggestMatching(configList, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
