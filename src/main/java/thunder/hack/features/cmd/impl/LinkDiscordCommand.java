package thunder.hack.features.cmd.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import thunder.hack.features.cmd.Command;
import thunder.hack.features.modules.misc.DiscordNameplates;
import thunder.hack.core.manager.client.ModuleManager;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class LinkDiscordCommand extends Command {
    public LinkDiscordCommand() {
        super("linkdiscord");
    }

    @Override
    public void executeBuild(@NotNull LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                sendMessage(Formatting.RED + "You must be in-game to link your Discord account.");
                return SINGLE_SUCCESS;
            }

            try {
                DiscordNameplates module = ModuleManager.discordNameplates;
                if (module == null) {
                    sendMessage(Formatting.RED + "DiscordNameplates module is not available.");
                    return SINGLE_SUCCESS;
                }
                
                if (!module.isOn()) {
                    sendMessage(Formatting.RED + "DiscordNameplates module is not enabled.");
                    return SINGLE_SUCCESS;
                }

                // Попытка инициализировать сервис, если еще не инициализирован
                if (module.nameService == null) {
                    if (module.backendBaseUrl.getValue() == null || module.backendBaseUrl.getValue().isEmpty()) {
                        sendMessage(Formatting.RED + "DiscordNameplates service is not initialized. Please set BackendUrl in module settings.");
                        return SINGLE_SUCCESS;
                    }
                    try {
                        module.initializeService();
                    } catch (Exception e) {
                        sendMessage(Formatting.RED + "Failed to initialize DiscordNameplates service: " + e.getMessage());
                        return SINGLE_SUCCESS;
                    }
                    if (module.nameService == null) {
                        sendMessage(Formatting.RED + "DiscordNameplates service is not initialized. Please check BackendUrl setting.");
                        return SINGLE_SUCCESS;
                    }
                }

                module.nameService.openLinkPage(mc.player.getUuid());
                sendMessage(Formatting.GREEN + "Opening Discord link page in your browser...");
                sendMessage(Formatting.GRAY + "Complete the authorization in your browser to link your account.");
            } catch (Exception e) {
                sendMessage(Formatting.RED + "Failed to open link page: " + e.getMessage());
            }

            return SINGLE_SUCCESS;
        });
    }
}

