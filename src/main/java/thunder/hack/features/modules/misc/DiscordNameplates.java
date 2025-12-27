package thunder.hack.features.modules.misc;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.discord.DiscordNameService;

public class DiscordNameplates extends Module {
    public DiscordNameplates() {
        super("DiscordNameplates", Category.MISC);
    }

    public final Setting<Boolean> enableDiscordNameplates = new Setting<>("Enable", false);
    public final Setting<Boolean> showPrefix = new Setting<>("ShowPrefix", true);
    public final Setting<String> backendBaseUrl = new Setting<>("BackendUrl", "https://discord-nameplates-backend-production.up.railway.app", v -> false);

    public DiscordNameService nameService;

    @Override
    public void onEnable() {
        initializeService();
    }
    
    public void initializeService() {
        if (nameService == null && backendBaseUrl.getValue() != null && !backendBaseUrl.getValue().isEmpty()) {
            try {
                nameService = new DiscordNameService(backendBaseUrl.getValue());
            } catch (Exception e) {
                // Игнорируем ошибки инициализации, чтобы не сломать загрузку
            }
        }
    }
    
    public void onSettingChange() {
        // Переинициализируем сервис при изменении BackendUrl
        if (nameService != null) {
            nameService.shutdown();
            nameService = null;
        }
        initializeService();
    }

    @Override
    public void onDisable() {
        if (nameService != null) {
            nameService.shutdown();
        }
    }

    public String getDiscordName(@NotNull PlayerEntity player) {
        if (!enableDiscordNameplates.getValue() || nameService == null) {
            return null;
        }
        return nameService.getDiscordName(player.getUuid());
    }

    public String formatDiscordName(String discordName) {
        if (discordName == null) return null;
        return showPrefix.getValue() ? "§d[DC] §r" + discordName : discordName;
    }
}

