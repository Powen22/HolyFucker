package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.gui.notification.Notification;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;

public final class AutoSwap extends Module {
    private final Setting<Bind> swapButton = new Setting<>("SwapButton", new Bind(GLFW.GLFW_KEY_CAPS_LOCK, false, false));
    private final Setting<Swap> swapMode = new Setting<>("Swap", Swap.BallTotem);
    private final Setting<Boolean> ignoreUnenchantedTotem = new Setting<>("IgnoreUnenchantedTotem", false);
    private final Setting<Boolean> swapNotify = new Setting<>("SwapNotify", true);
    private final Setting<Boolean> ncpStrict = new Setting<>("NCPStrict", false);
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", false);
    private final Setting<Boolean> resetAttackCooldown = new Setting<>("ResetAttackCooldown", false);

    private enum Swap {BallTotem, TotemTotem}

    private int delay = 0;
    private boolean bindWasPressed = false;
    private boolean totemTotemSwapDone = false;

    private static final int BASE_DELAY = 2;
    private static final float PING_DELAY_DIVISOR = 25f;

    public AutoSwap() {
        super("AutoSwap", Category.COMBAT);
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mc.player == null || mc.world == null) return;

        boolean bindCurrentlyPressed = isKeyPressed(swapButton);
        boolean shouldTrigger = bindCurrentlyPressed && !bindWasPressed;
        
        if (!bindCurrentlyPressed && bindWasPressed) {
            totemTotemSwapDone = false;
        }
        bindWasPressed = bindCurrentlyPressed;

        if (shouldTrigger) {
            int slot = getItemSlot();
            if (slot != -1) {
                swapTo(slot);
            }
        }

        delay--;
    }

    private int getItemSlot() {
        if (mc.player == null || mc.world == null) return -1;
        
        Item offHandItem = mc.player.getOffHandStack().getItem();
        Item item = null;
        int itemSlot = -1;

        switch (swapMode.getValue()) {
            case BallTotem -> {
                boolean hasTotemInOffhand = offHandItem == Items.TOTEM_OF_UNDYING;
                if (hasTotemInOffhand && ignoreUnenchantedTotem.getValue() && !isEnchanted(mc.player.getOffHandStack())) {
                    hasTotemInOffhand = false;
                }
                boolean hasSkullInOffhand = isSkullItem(offHandItem);
                
                if (mc.player.getOffHandStack().isEmpty() || hasTotemInOffhand) {
                    item = Items.PLAYER_HEAD;
                } else if (hasSkullInOffhand) {
                    if (ignoreUnenchantedTotem.getValue() && !hasEnchantedTotem()) {
                        item = null;
                    } else {
                        item = Items.TOTEM_OF_UNDYING;
                    }
                } else {
                    item = Items.PLAYER_HEAD;
                }
            }
            case TotemTotem -> {
                boolean hasTotemInOffhand = offHandItem == Items.TOTEM_OF_UNDYING;
                if (hasTotemInOffhand && ignoreUnenchantedTotem.getValue() && !isEnchanted(mc.player.getOffHandStack())) {
                    hasTotemInOffhand = false;
                }
                
                if (mc.player.getOffHandStack().isEmpty() || !hasTotemInOffhand) {
                    if (ignoreUnenchantedTotem.getValue() && !hasEnchantedTotem()) {
                        item = null;
                    } else {
                        item = Items.TOTEM_OF_UNDYING;
                    }
                } else {
                    int otherTotemSlot = findOtherTotemSlot();
                    if (otherTotemSlot != -1) {
                        item = Items.TOTEM_OF_UNDYING;
                        itemSlot = otherTotemSlot;
                    } else {
                        item = null;
                    }
                }
            }
        }

        if (item == null) return itemSlot != -1 ? itemSlot : -1;

        Item offhandItemCurrent = mc.player.getOffHandStack().getItem();
        boolean itemAlreadyInOffhand = (item == Items.PLAYER_HEAD && isSkullItem(offhandItemCurrent)) 
                || offhandItemCurrent == item;

        boolean isTotemTotemBindSwap = swapMode.getValue() == Swap.TotemTotem 
                && item == Items.TOTEM_OF_UNDYING;
        
        boolean isTotemToTotemSwap = item == Items.TOTEM_OF_UNDYING 
                && offHandItem == Items.TOTEM_OF_UNDYING 
                && itemSlot != -1;
        
        if (isTotemTotemBindSwap && offhandItemCurrent == Items.TOTEM_OF_UNDYING && !totemTotemSwapDone) {
            if (itemSlot == -1) {
                int otherTotemSlot = findOtherTotemSlot();
                if (otherTotemSlot == -1) {
                    return -1;
                }
                itemSlot = otherTotemSlot;
            }
        } else if (isTotemTotemBindSwap && totemTotemSwapDone) {
            return -1;
        }
        
        if (itemAlreadyInOffhand && !isTotemToTotemSwap && !isTotemTotemBindSwap) {
            // Если в оффхенде не зачарованный тотем и включено игнорирование, продолжаем поиск зачарованного
            if (item == Items.TOTEM_OF_UNDYING && ignoreUnenchantedTotem.getValue() 
                    && !isEnchanted(mc.player.getOffHandStack())) {
                // Продолжаем поиск зачарованного тотема
            } else {
                return itemSlot != -1 ? itemSlot : -1;
            }
        }
        
        if (item == mc.player.getMainHandStack().getItem() && mc.options.useKey.isPressed()) return -1;

        boolean requireEnchantedTotem = item == Items.TOTEM_OF_UNDYING 
                && ignoreUnenchantedTotem.getValue();

        if (itemSlot == -1) {
            for (int i = 9; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                
                boolean itemMatches = (item == Items.PLAYER_HEAD && isSkullItem(stack.getItem())) 
                        || stack.getItem() == item;
                
                if (itemMatches) {
                    if (requireEnchantedTotem && !isEnchanted(stack)) {
                        continue;
                    }
                    itemSlot = i;
                    break;
                }
            }
        }
        
        if (itemSlot == -1) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                
                boolean itemMatches = (item == Items.PLAYER_HEAD && isSkullItem(stack.getItem())) 
                        || stack.getItem() == item;
                
                if (itemMatches) {
                    if (requireEnchantedTotem && !isEnchanted(stack)) {
                        continue;
                    }
                    itemSlot = i;
                    break;
                }
            }
        }

        if (swapNotify.getValue() && itemSlot != -1) {
            ItemStack swapStack = mc.player.getInventory().getStack(itemSlot);
            String itemName = swapStack.getName().getString();
            Managers.NOTIFICATION.publicity("AutoSwap", "Свапнул на " + itemName, 2, Notification.Type.SUCCESS);
        }

        return itemSlot;
    }

    public void swapTo(int slot) {
        if (slot != -1 && delay <= 0) {
            if (swapMode.getValue() == Swap.TotemTotem 
                    && mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
                totemTotemSwapDone = true;
            }
            
            if (mc.currentScreen instanceof GenericContainerScreen && !ModuleManager.guiMove.isEnabled()) return;

            if (stopMotion.getValue()) mc.player.setVelocity(0, mc.player.getVelocity().getY(), 0);

            int prevCurrentItem = mc.player.getInventory().selectedSlot;
            
            if (slot >= 9) {
                if (ncpStrict.getValue())
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                clickSlot(slot);
                clickSlot(45);
                clickSlot(slot);
                sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            } else {
                sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                mc.player.getInventory().selectedSlot = slot;
                debug(slot + " select");
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                mc.player.getInventory().selectedSlot = prevCurrentItem;
                if (resetAttackCooldown.getValue())
                    mc.player.resetLastAttackedTicks();
            }
            delay = (int) (BASE_DELAY + (Managers.SERVER.getPing() / PING_DELAY_DIVISOR));
        }
    }

    private int findOtherTotemSlot() {
        ItemStack offhandStack = mc.player.getOffHandStack();
        boolean offhandEnchanted = isEnchanted(offhandStack);
        
        int bestSlot = -1;
        
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                boolean stackEnchanted = isEnchanted(stack);
                
                // Пропускаем не зачарованные тотемы если включено игнорирование
                if (ignoreUnenchantedTotem.getValue() && !stackEnchanted) {
                    continue;
                }
                
                if (!offhandEnchanted && stackEnchanted) {
                    return i;
                }
                
                if (offhandEnchanted && !stackEnchanted) {
                    return i;
                }
                
                if (bestSlot == -1) {
                    bestSlot = i;
                }
            }
        }
        
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                boolean stackEnchanted = isEnchanted(stack);
                
                // Пропускаем не зачарованные тотемы если включено игнорирование
                if (ignoreUnenchantedTotem.getValue() && !stackEnchanted) {
                    continue;
                }
                
                if (!offhandEnchanted && stackEnchanted) {
                    return i;
                }
                
                if (offhandEnchanted && !stackEnchanted) {
                    return i;
                }
                
                if (bestSlot == -1) {
                    bestSlot = i;
                }
            }
        }
        
        return bestSlot;
    }

    private boolean isEnchanted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.hasEnchantments();
    }

    private boolean hasEnchantedTotem() {
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING && isEnchanted(mc.player.getOffHandStack())) {
            return true;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING && isEnchanted(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSkullItem(Item item) {
        return item == Items.PLAYER_HEAD;
    }
}

