package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.*;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.combat.AntiBot;
import thunder.hack.gui.notification.Notification;
import thunder.hack.setting.Setting;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class ItemUseTracker extends Module {
    public ItemUseTracker() {
        super("ItemUseTracker", Category.MISC);
    }

    private final Setting<Boolean> trackFood = new Setting<>("TrackFood", true);
    private final Setting<Boolean> trackGapple = new Setting<>("TrackGapple", true);
    private final Setting<Boolean> trackPotions = new Setting<>("TrackPotions", true);
    private final Setting<Boolean> trackPearls = new Setting<>("TrackPearls", true);
    private final Setting<Boolean> notification = new Setting<>("Notification", true);
    private final Setting<Boolean> ignoreSelf = new Setting<>("IgnoreSelf", true);
    private final Setting<Integer> range = new Setting<>("Range", 50, 10, 100);

    // Отслеживаем кто что использует
    private final Map<String, ItemStack> playerUsingItem = new HashMap<>();
    private final Map<String, Integer> playerUseTicksLeft = new HashMap<>();

    @Override
    public void onDisable() {
        playerUsingItem.clear();
        playerUseTicksLeft.clear();
    }

    @Override
    public void onUpdate() {
        if (mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null) continue;
            if (ignoreSelf.getValue() && player == mc.player) continue;
            if (AntiBot.bots.contains(player)) continue;
            if (mc.player.distanceTo(player) > range.getValue()) continue;

            String playerName = player.getName().getString();

            if (player.isUsingItem()) {
                ItemStack activeItem = player.getActiveItem();
                if (!activeItem.isEmpty()) {
                    // Сохраняем что игрок использует
                    if (!playerUsingItem.containsKey(playerName)) {
                        playerUsingItem.put(playerName, activeItem.copy());
                        playerUseTicksLeft.put(playerName, player.getItemUseTimeLeft());
                    }
                }
            } else {
                // Игрок перестал использовать предмет
                if (playerUsingItem.containsKey(playerName)) {
                    ItemStack usedItem = playerUsingItem.get(playerName);
                    int ticksLeft = playerUseTicksLeft.getOrDefault(playerName, 0);
                    
                    // Проверяем что предмет был использован (не просто отменён)
                    // Если осталось мало тиков - значит использовал
                    if (ticksLeft > 5) {
                        handleItemUsed(player, usedItem);
                    }
                    
                    playerUsingItem.remove(playerName);
                    playerUseTicksLeft.remove(playerName);
                }
            }

            // Обновляем оставшееся время использования
            if (playerUsingItem.containsKey(playerName) && player.isUsingItem()) {
                playerUseTicksLeft.put(playerName, player.getItemUseTimeLeft());
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (!(e.getPacket() instanceof EntitySpawnS2CPacket spawn)) return;
        
        // Отслеживаем бросок перла
        if (spawn.getEntityType() == EntityType.ENDER_PEARL && trackPearls.getValue()) {
            PlayerEntity thrower = findNearestPlayer(spawn.getX(), spawn.getY(), spawn.getZ());
            if (thrower != null) {
                reportPearlThrow(thrower);
            }
        }
        
        // Отслеживаем бросок взрывного/долгого зелья
        if (spawn.getEntityType() == EntityType.POTION && trackPotions.getValue()) {
            PlayerEntity thrower = findNearestPlayer(spawn.getX(), spawn.getY(), spawn.getZ());
            if (thrower != null) {
                // Получаем зелье которое игрок держал
                ItemStack mainHand = thrower.getMainHandStack();
                ItemStack offHand = thrower.getOffHandStack();
                
                ItemStack potionStack = null;
                if (mainHand.getItem() instanceof SplashPotionItem || mainHand.getItem() instanceof LingeringPotionItem) {
                    potionStack = mainHand;
                } else if (offHand.getItem() instanceof SplashPotionItem || offHand.getItem() instanceof LingeringPotionItem) {
                    potionStack = offHand;
                }
                
                if (potionStack != null) {
                    reportPotionUse(thrower, potionStack);
                } else {
                    // Если не можем получить стак, просто сообщаем о броске
                    reportUse(thrower.getName().getString(), Formatting.AQUA + "Splash Potion");
                }
            }
        }
    }
    
    private PlayerEntity findNearestPlayer(double x, double y, double z) {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (ignoreSelf.getValue() && player == mc.player) continue;
            if (AntiBot.bots.contains(player)) continue;
            if (mc.player.distanceTo(player) > range.getValue()) continue;
            
            double dist = player.squaredDistanceTo(x, y, z);
            if (dist < 16) { // В пределах 4 блоков от точки спавна
                return player;
            }
        }
        return null;
    }

    private void handleItemUsed(PlayerEntity player, ItemStack stack) {
        Item item = stack.getItem();
        String playerName = player.getName().getString();

        // Зачарованное золотое яблоко
        if (item == Items.ENCHANTED_GOLDEN_APPLE && trackGapple.getValue()) {
            reportUse(playerName, Formatting.LIGHT_PURPLE + "Enchanted Golden Apple");
            return;
        }

        // Золотое яблоко
        if (item == Items.GOLDEN_APPLE && trackGapple.getValue()) {
            reportUse(playerName, Formatting.GOLD + "Golden Apple");
            return;
        }

        // Зелья
        if ((item instanceof PotionItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem) && trackPotions.getValue()) {
            reportPotionUse(player, stack);
            return;
        }

        // Любая еда
        if (stack.getComponents().contains(DataComponentTypes.FOOD) && trackFood.getValue()) {
            String itemName = stack.getName().getString();
            reportUse(playerName, Formatting.GREEN + itemName);
        }
    }

    private void reportPotionUse(PlayerEntity player, ItemStack potionStack) {
        String playerName = player.getName().getString();
        String potionName = potionStack.getName().getString();
        
        StringBuilder message = new StringBuilder();
        message.append(Formatting.AQUA).append(potionName);

        // Получаем эффекты зелья
        PotionContentsComponent potionContents = potionStack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents != null) {
            boolean hasEffects = false;
            StringBuilder effectsBuilder = new StringBuilder();
            effectsBuilder.append("\n").append(Formatting.GRAY).append("Эффекты: ");
            
            // Получаем эффекты из базового типа зелья (основной источник)
            if (potionContents.potion().isPresent()) {
                for (StatusEffectInstance effect : potionContents.potion().get().value().getEffects()) {
                    hasEffects = true;
                    addEffectToBuilder(effectsBuilder, effect);
                }
            }
            
            // Добавляем кастомные эффекты (если есть) - только если нет базовых
            if (!hasEffects) {
                for (StatusEffectInstance effect : potionContents.getEffects()) {
                    hasEffects = true;
                    addEffectToBuilder(effectsBuilder, effect);
                }
            }
            
            if (hasEffects) {
                message.append(effectsBuilder);
            }
        }

        reportUse(playerName, message.toString());
    }
    
    private void addEffectToBuilder(StringBuilder builder, StatusEffectInstance effect) {
        String effectName = effect.getEffectType().value().getName().getString();
        int amplifier = effect.getAmplifier() + 1;
        int durationTicks = effect.getDuration();
        String durationStr = formatDuration(durationTicks);
        
        builder.append("\n  ").append(Formatting.YELLOW).append(effectName);
        if (amplifier > 1) {
            builder.append(" ").append(amplifier);
        }
        builder.append(Formatting.WHITE).append(" - ").append(Formatting.GREEN).append(durationStr);
    }

    private void reportPearlThrow(PlayerEntity player) {
        String playerName = player.getName().getString();
        reportUse(playerName, Formatting.DARK_PURPLE + "Ender Pearl");
    }

    private void reportUse(String playerName, String itemInfo) {
        String message = Formatting.WHITE + playerName + Formatting.GRAY + " использовал " + itemInfo;
        
        sendMessage(message);
        
        if (notification.getValue()) {
            // Убираем форматирование для уведомления
            String cleanItem = Formatting.strip(itemInfo.split("\n")[0]);
            Managers.NOTIFICATION.publicity(playerName, "использовал " + cleanItem, 3, Notification.Type.INFO);
        }
    }

    private String formatDuration(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return seconds + "с";
        }
    }
}

