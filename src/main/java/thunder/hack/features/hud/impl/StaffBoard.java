package thunder.hack.features.hud.impl;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import thunder.hack.core.manager.client.ConfigManager;
import thunder.hack.features.cmd.impl.StaffCommand;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.features.hud.HudElement;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.animation.AnimationUtility;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StaffBoard extends HudElement {
    private static final Pattern validUserPattern = Pattern.compile("^\\w{3,16}$");
    private List<String> players = new ArrayList<>();
    private List<String> notSpec = new ArrayList<>();
    private Map<String, Identifier> skinMap = new HashMap<>();
    private static final Set<String> staffList = new HashSet<>(); // Список игроков, которые когда-либо были в gm3

    private float vAnimation, hAnimation;

    private static boolean loaded = false;

    public StaffBoard() {
        super("StaffBoard", 50, 50);
        if (!loaded) {
            loadStaffList();
            loaded = true;
        }
    }

    private static boolean check(String prefix) {
        if (prefix == null || prefix.isEmpty()) return false;
        String lower = prefix.toLowerCase();
        return lower.contains("admin") || lower.contains("mod") || lower.contains("helper")
                || lower.contains("staff") || lower.contains("owner") || lower.contains("dev")
                || lower.contains("developer") || lower.contains("manager") || lower.contains("builder");
    }

    public static List<String> getOnlinePlayer() {
        return mc.player.networkHandler.getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(GameProfile::getName)
                .filter(profileName -> validUserPattern.matcher(profileName).matches())
                .collect(Collectors.toList());
    }

    public static List<String> getOnlinePlayerD() {
        List<String> S = new ArrayList<>();

        for (PlayerListEntry player : mc.player.networkHandler.getPlayerList()) {
            if (mc.isInSingleplayer() || player.getScoreboardTeam() == null) continue;

            String profileName = player.getProfile().getName();
            String displayName = Arrays.asList(player.getScoreboardTeam().getPlayerList().toArray())
                    .toString().replace("[", "").replace("]", "");

            String profileNameLower = profileName.toLowerCase();
            String displayNameLower = displayName.toLowerCase();

            // Проверяем, есть ли игрок в списке модерации
            boolean isInStaffCommand = StaffCommand.staffNames.stream()
                    .anyMatch(staffName -> staffName.equalsIgnoreCase(profileName) ||
                            staffName.equalsIgnoreCase(displayName));

            // Проверяем, был ли игрок раньше в gm3
            boolean wasInGm3 = staffList.contains(profileNameLower) || staffList.contains(displayNameLower);

            // Если игрок сейчас в gm3 - ЗАПОМИНАЕМ его и показываем
                if (player.getGameMode() == GameMode.SPECTATOR) {
                // ДОБАВЛЯЕМ В ПАМЯТЬ только если в gm3
                boolean wasAdded = staffList.add(profileNameLower) || staffList.add(displayNameLower);
                if (wasAdded) {
                    saveStaffList(); // Сохраняем при добавлении нового игрока
                }
                S.add(displayName + ":gm3");
            }
            // Если игрок НЕ в gm3, но в списке модерации или был раньше в gm3
            else if (isInStaffCommand || wasInGm3) {
                S.add(displayName + ":active");
            }
        }

        return S;
    }

    public List<String> getVanish() {
        List<String> list = new ArrayList<>();
        for (Team s : mc.world.getScoreboard().getTeams()) {
            if (s.getPrefix().getString().isEmpty() || mc.isInSingleplayer()) continue;
            String name = Arrays.asList(s.getPlayerList().toArray()).toString().replace("[", "").replace("]", "");

            if (getOnlinePlayer().contains(name) || name.isEmpty())
                continue;
            if (StaffCommand.staffNames.toString().toLowerCase().contains(name.toLowerCase())
                    && check(s.getPrefix().getString().toLowerCase())
                    || check(s.getPrefix().getString().toLowerCase())
                    || name.toLowerCase().contains("1danil_mansoru1")
                    || name.toLowerCase().contains("barslan_")
                    || name.toLowerCase().contains("timmings")
                    || name.toLowerCase().contains("timings")
                    || name.toLowerCase().contains("ruthless")
                    || s.getPrefix().getString().contains("YT")
                    || (s.getPrefix().getString().contains("Y") && s.getPrefix().getString().contains("T"))
            )
                list.add(s.getPrefix().getString() + name + ":vanish");
        }
        return list;
    }

    public void onRender2D(DrawContext context) {
        super.onRender2D(context);
        List<String> all = new java.util.ArrayList<>();
        all.addAll(players);
        all.addAll(notSpec);

        int y_offset1 = 0;
        float max_width = 0;
        float titleWidth = FontRenderers.sf_bold.getStringWidth("Staff");

        float pointerX = 0;
        for (String player : all) {
            if (y_offset1 == 0)
                y_offset1 += 4;

            y_offset1 += 9;

            String playerName = player.split(":")[0];
            float nameWidth = FontRenderers.sf_bold_mini.getStringWidth(playerName);
            float timeWidth = FontRenderers.sf_bold_mini.getStringWidth((player.split(":")[1].equalsIgnoreCase("vanish") ? Formatting.RED + "V" : player.split(":")[1].equalsIgnoreCase("gm3") ? Formatting.YELLOW + "Spec" : player.split(":")[1].equalsIgnoreCase("active") ? Formatting.GREEN + "A" : Formatting.GREEN + "Z"));

            float width = nameWidth + timeWidth + 20; // 20 - отступы (13 для иконки + 7 для правого отступа)

            if (width > max_width)
                max_width = width;

            if (timeWidth > pointerX)
                pointerX = timeWidth;
        }

        // Минимальная ширина должна быть не меньше ширины заголовка "Staff" + отступы
        if (max_width < titleWidth + 8)
            max_width = titleWidth + 8;

        vAnimation = AnimationUtility.fast(vAnimation, 14 + y_offset1, 15);
        hAnimation = AnimationUtility.fast(hAnimation, max_width, 15);

        Render2DEngine.drawHudBase(context.getMatrices(), getPosX(), getPosY(), hAnimation, vAnimation, HudEditor.hudRound.getValue());

        if (HudEditor.hudStyle.is(HudEditor.HudStyle.Glowing)) {
            FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), "Staff", getPosX() + hAnimation / 2, getPosY() + 4, HudEditor.textColor.getValue().getColorObject());
        } else {
            FontRenderers.sf_bold.drawGradientCenteredString(context.getMatrices(), "Staff", getPosX() + hAnimation / 2, getPosY() + 4, 10);
        }

        if (y_offset1 > 0) {
            if (HudEditor.hudStyle.is(HudEditor.HudStyle.Blurry)) {
                Render2DEngine.drawRectDumbWay(context.getMatrices(), getPosX() + 4, getPosY() + 13, getPosX() + getWidth() - 8, getPosY() + 14, new Color(0x54FFFFFF, true));
            } else {
                Render2DEngine.horizontalGradient(context.getMatrices(), getPosX() + 2, getPosY() + 13.7f, getPosX() + 2 + hAnimation / 2f - 2, getPosY() + 13.5f, Render2DEngine.injectAlpha(HudEditor.textColor.getValue().getColorObject(), 0), HudEditor.textColor.getValue().getColorObject());
                Render2DEngine.horizontalGradient(context.getMatrices(), getPosX() + 2 + hAnimation / 2f - 2, getPosY() + 13.7f, getPosX() + 2 + hAnimation - 4, getPosY() + 14, HudEditor.textColor.getValue().getColorObject(), Render2DEngine.injectAlpha(HudEditor.textColor.getValue().getColorObject(), 0));
            }
        }

        Render2DEngine.addWindow(context.getMatrices(), getPosX(), getPosY(), getPosX() + hAnimation, getPosY() + vAnimation, 1f);
        int y_offset = 0;

        for (String player : all) {
            float px = getPosX() + (max_width - pointerX - 10);

            Identifier tex = getTexture(player);
            if (tex != null) {
                context.drawTexture(tex, (int) (getPosX() + 3), (int) (getPosY() + 16 + y_offset), 8, 8, 8, 8, 8, 8, 64, 64);
                context.drawTexture(tex, (int) (getPosX() + 3), (int) (getPosY() + 16 + y_offset), 8, 8, 40, 8, 8, 8, 64, 64);
            }

            String displayName = player.split(":")[0];
            FontRenderers.sf_bold_mini.drawString(context.getMatrices(), displayName, getPosX() + 13, getPosY() + 19 + y_offset, HudEditor.textColor.getValue().getColor());
            FontRenderers.sf_bold_mini.drawCenteredString(context.getMatrices(), (player.split(":")[1].equalsIgnoreCase("vanish") ? Formatting.RED + "V" : player.split(":")[1].equalsIgnoreCase("gm3") ? Formatting.YELLOW + "Spec" : player.split(":")[1].equalsIgnoreCase("active") ? Formatting.GREEN + "A" : Formatting.GREEN + "O"),
                    px + (getPosX() + max_width - px) / 2f, getPosY() + 19 + y_offset, HudEditor.textColor.getValue().getColor());
            y_offset += 9;
        }
        Render2DEngine.popWindow();
        setBounds(getPosX(), getPosY(), hAnimation, vAnimation);
    }

    @Override
    public void onUpdate() {
        if (mc.player != null && mc.player.age % 10 == 0) {
            players = new ArrayList<>();
            notSpec = getOnlinePlayerD();
            players.sort(String::compareTo);
            notSpec.sort(String::compareTo);
        }
    }

    private Identifier getTexture(String n) {
        Identifier id = null;
        if (skinMap.containsKey(n))
            id = skinMap.get(n);

        for (PlayerListEntry ple : mc.getNetworkHandler().getPlayerList())
            if (n.contains(ple.getProfile().getName())) {
                id = ple.getSkinTextures().texture();
                if (!skinMap.containsKey(n))
                    skinMap.put(n, id);
                break;
            }

        return id;
    }

    // Метод для очистки staffList (опционально)
    public static void clearStaffList() {
        staffList.clear();
    }

    // Метод для удаления конкретного игрока из памяти (опционально)
    public static void removeFromStaffList(String playerName) {
        staffList.remove(playerName.toLowerCase());
        saveStaffList();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void saveStaffList() {
        File file = new File(ConfigManager.MISC_FOLDER, "stafflist.txt");
        try {
            ConfigManager.MISC_FOLDER.mkdirs();
            file.createNewFile();
        } catch (Exception ignored) {
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Сохраняем автоматически добавленных (staffList)
            writer.write("# Auto-added staff (from gm3)\n");
            for (String name : staffList) {
                writer.write(name + "\n");
            }
            // Сохраняем вручную добавленных (StaffCommand.staffNames)
            writer.write("# Manually added staff\n");
            for (String name : StaffCommand.staffNames) {
                writer.write("!" + name + "\n"); // Префикс "!" для различения
            }
        } catch (Exception ignored) {
        }
    }

    public static void loadStaffList() {
        try {
            File file = new File(ConfigManager.MISC_FOLDER, "stafflist.txt");

            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    boolean isManualSection = false;
                    while (reader.ready()) {
                        String line = reader.readLine().trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            if (line.contains("Manually")) {
                                isManualSection = true;
                            } else if (line.contains("Auto-added")) {
                                isManualSection = false;
                            }
                            continue;
                        }
                        
                        if (isManualSection && line.startsWith("!")) {
                            // Вручную добавленные
                            String name = line.substring(1);
                            if (!StaffCommand.staffNames.contains(name)) {
                                StaffCommand.staffNames.add(name);
                            }
                        } else if (!isManualSection) {
                            // Автоматически добавленные
                            staffList.add(line.toLowerCase());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
