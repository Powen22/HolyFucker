package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.AsyncManager;
import thunder.hack.core.manager.world.WayPointManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.utility.Timer;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.features.modules.combat.Aura;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.features.modules.client.HudEditor;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerHelper extends Module {
    public ServerHelper() {
        super("ServerHelper", Category.MISC);
    }

    private final Setting<Bind> desorient = new Setting<>("Desorient", new Bind(-1, false, false));
    private final Setting<Bind> trap = new Setting<>("Trap", new Bind(-1, false, false));
    public final Setting<Boolean> aucHelper = new Setting<>("AucHelper", true);
    public final Setting<Boolean> autoWay = new Setting<>("AutoWay", true);

    private final Timer pvpTimer = new Timer();
    private AuctionItem cheapestItem = null;

    private final Timer disorientTimer = new Timer();
    private final Timer trapTimer = new Timer();

    // Паттерн цены (как в AhHelper)
    private static final Pattern PRICE_PATTERN = Pattern.compile("Цена.*?\\$([\\d,]+)");
    
    // Паттерны для парсинга событий
    private static final Pattern EVENT_COORDS_PATTERN = Pattern.compile("координатах\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile("\\[([^\\]]+)\\]|(Вулкан|Маяк|Метеорит)", Pattern.CASE_INSENSITIVE);



    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (fullNullCheck() || !autoWay.getValue()) return;
        
        if (event.getPacket() instanceof GameMessageS2CPacket mPacket) {
            String message = mPacket.content().getString();
            processEventMessage(message);
        }
    }
    
    private void processEventMessage(String message) {
        // Проверяем, содержит ли сообщение информацию о событии
        boolean isEvent = message.contains("Вулкан") || message.contains("Маяк") || message.contains("Метеорит") ||
                         message.contains("вулкан") || message.contains("маяк") || message.contains("метеорит");
        
        if (!isEvent) return;
        
        // Извлекаем координаты
        Matcher coordsMatcher = EVENT_COORDS_PATTERN.matcher(message);
        if (!coordsMatcher.find()) return;
        
        int x = Integer.parseInt(coordsMatcher.group(1));
        int y = Integer.parseInt(coordsMatcher.group(2));
        int z = Integer.parseInt(coordsMatcher.group(3));
        
        // Определяем название события
        String eventName = "Событие";
        if (message.contains("Вулкан") || message.contains("вулкан")) {
            eventName = "Вулкан";
        } else if (message.contains("Маяк") || message.contains("маяк")) {
            eventName = "Маяк";
        } else if (message.contains("Метеорит") || message.contains("метеорит")) {
            eventName = "Метеорит";
        } else {
            // Пытаемся извлечь из формата [Название]
            Matcher nameMatcher = EVENT_NAME_PATTERN.matcher(message);
            if (nameMatcher.find()) {
                String found = nameMatcher.group(1);
                if (found == null) found = nameMatcher.group(2);
                if (found != null && !found.isEmpty()) {
                    eventName = found.trim();
                }
            }
        }
        
        // Извлекаем уровень лута, если есть
        String lootLevel = "";
        if (message.contains("Уровень лута:")) {
            String[] parts = message.split("Уровень лута:");
            if (parts.length > 1) {
                String levelPart = parts[1].trim();
                // Берем первое слово после "Уровень лута:"
                String[] levelWords = levelPart.split("\\s+");
                if (levelWords.length > 0) {
                    lootLevel = " " + levelWords[0];
                }
            }
        }
        
        // Создаем название waypoint
        String waypointName = eventName + lootLevel;
        
        // Получаем информацию о сервере и измерении
        String server = mc.isInSingleplayer() ? "SinglePlayer" : mc.getNetworkHandler().getServerInfo().address;
        String dimension = mc.world != null ? mc.world.getRegistryKey().getValue().getPath() : "overworld";
        
        // Создаем waypoint
        WayPointManager.WayPoint wp = new WayPointManager.WayPoint(x, y, z, waypointName, server, dimension);
        Managers.WAYPOINT.addWayPoint(wp);
    }

    @Override
    public void onUpdate() {
        // Removed functions: AntiTpHere, ClanInvite, /feed, /fix all, /near, Farmilka

        if (mc.player.hurtTime > 0)
            pvpTimer.reset();

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest && aucHelper.getValue()) {
            String title = mc.currentScreen != null ? mc.currentScreen.getTitle().getString() : "";
            if (title.contains("Аукцион") || title.contains("Поиск") || title.contains("Auction")) {
                cheapestItem = null;
                long minPrice = Long.MAX_VALUE;

                int totalSlots = chest.slots.size();
                int containerSlots = Math.max(0, totalSlots - 36);

                for (int i = 0; i < containerSlots; i++) {
                    Slot chestSlot = chest.slots.get(i);
                    ItemStack stack = chestSlot.getStack();
                    if (stack.isEmpty()) continue;

                    long price = getPriceFromItem(stack);
                    if (price > 0 && price < minPrice) {
                        minPrice = price;
                        cheapestItem = new AuctionItem(i, stack.copy(), price);
                    }
                }
            } else {
                cheapestItem = null;
            }
        }
    }


    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        // Removed checktimer logic
    }

    @EventHandler
    private void onSync(EventSync event) {
        if (fullNullCheck()) return;

        if (isKeyPressed(desorient.getValue().getKey()) && disorientTimer.passedMs(500) && mc.currentScreen == null) {
            if (use(InventoryUtility.findInHotBar(i -> i.getItem() == Items.ENDER_EYE),
                    InventoryUtility.findInInventory(i -> i.getItem() == Items.ENDER_EYE))) {
                disorientTimer.reset();
            }
        }

        if (isKeyPressed(trap.getValue().getKey()) && trapTimer.passedMs(500) && mc.currentScreen == null) {
            if (use(InventoryUtility.findInHotBar(i -> i.getItem() == Items.NETHERITE_SCRAP),
                    InventoryUtility.findInInventory(i -> i.getItem() == Items.NETHERITE_SCRAP))) {
                trapTimer.reset();
            }
        }
    }

    public void onRenderChest(DrawContext context, Slot slot) {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest) {
            String title = mc.currentScreen != null ? mc.currentScreen.getTitle().getString() : "";
            if (title.contains("Аукцион") || title.contains("Поиск") || title.contains("Auction")) {
                // Подсветка самого дешевого лота золотым цветом
                if (cheapestItem != null && slot.id == cheapestItem.slotIndex && slot.id <= 44 && !slot.getStack().isEmpty()) {
                    int glowColor = 0x80FFD700; // Полупрозрачное золото
                    context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, glowColor);
                    
                    // Рамка
                    int borderColor = 0xFFFFD700; // Золотой
                    context.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, borderColor);         // Верх
                    context.fill(slot.x, slot.y + 15, slot.x + 16, slot.y + 16, borderColor);   // Низ
                    context.fill(slot.x, slot.y, slot.x + 1, slot.y + 16, borderColor);         // Лево
                    context.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 16, borderColor);   // Право
                }
            }
        }
    }

    public void onRenderChestOverlay(DrawContext context) {
        if (cheapestItem != null && mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString();
            if (title.contains("Аукцион") || title.contains("Поиск") || title.contains("Auction")) {
                int guiWidth = 176;
                int guiLeft = (screen.width - guiWidth) / 2;
                int guiTop = (screen.height - 222) / 2;

                String priceStr = "$" + formatPriceShort(cheapestItem.price);
                float priceWidth = FontRenderers.sf_bold.getStringWidth(priceStr);
                
                // 6 (отступ слева) + 16 (предмет) + 4 (между) + priceWidth + 6 (отступ справа)
                float boxWidth = 6 + 16 + 4 + priceWidth + 6;
                float boxHeight = 28;
                float xBox = guiLeft + (176 - boxWidth) / 2;
                float yBox = guiTop - boxHeight - 8;

                // Градиентная подсветка
                Render2DEngine.drawGradientBlurredShadow1(context.getMatrices(), xBox + 4, yBox + 4, boxWidth - 8, boxHeight - 8, 10, 
                        HudEditor.getColor(270), HudEditor.getColor(0), HudEditor.getColor(180), HudEditor.getColor(90));
                
                // Градиентная рамка
                Render2DEngine.renderRoundedGradientRect(context.getMatrices(), 
                        HudEditor.getColor(270), HudEditor.getColor(0), HudEditor.getColor(180), HudEditor.getColor(90), 
                        xBox, yBox, boxWidth, boxHeight, 7);
                
                // Темный фон внутри
                Render2DEngine.drawRound(context.getMatrices(), xBox + 1f, yBox + 1f, boxWidth - 2, boxHeight - 2, 6, new Color(0, 0, 0, 220));

                // Предмет
                float itemX = xBox + 6;
                float itemY = yBox + 6;
                context.drawItem(cheapestItem.stack, (int) itemX, (int) itemY);
                
                // Цена
                FontRenderers.sf_bold.drawString(context.getMatrices(), priceStr, itemX + 20, itemY + 5, -1);
            }
        }
    }

    private String formatPriceShort(long price) {
        if (price >= 1_000_000_000L) return String.format("%.1fB", price / 1_000_000_000.0);
        if (price >= 1_000_000L) return String.format("%.1fM", price / 1_000_000.0);
        if (price >= 1_000L) return String.format("%.1fK", price / 1_000.0);
        return String.valueOf(price);
    }

    // Метод для получения цены из лора (как в AhHelper)
    public static long getPriceFromItem(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return -1;

        for (Text line : lore.lines()) {
            String text = Formatting.strip(line.getString());
            Matcher m = PRICE_PATTERN.matcher(text);
            if (m.find()) {
                try {
                    String num = m.group(1).replace(",", "");
                    return Long.parseLong(num);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    private boolean use(SearchInvResult result, SearchInvResult invResult) {
        if (result.found()) {
            InventoryUtility.saveAndSwitchTo(result.slot());
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
            InventoryUtility.returnSlot();
            return true;
        } else if (invResult.found()) {
            int epSlot = invResult.slot();
            int originalSlot = mc.player.getInventory().selectedSlot;
            // Используем точно такую же логику как в MiddleClick PearlThread для инвентаря
            new Thread(() -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, epSlot, originalSlot, SlotActionType.SWAP, mc.player);
                AsyncManager.sleep(150);
                if (ModuleManager.aura.isEnabled() && Aura.target != null)
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
                InteractionUtility.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                AsyncManager.sleep(150);
                // Возвращаем предмет обратно
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, epSlot, originalSlot, SlotActionType.SWAP, mc.player);
            }).start();
            return true;
        }
        return false;
    }

    private static class AuctionItem {
        final int slotIndex;
        final ItemStack stack;
        final long price;

        AuctionItem(int i, ItemStack s, long p) {
            slotIndex = i;
            stack = s;
            price = p;
        }
    }
}
