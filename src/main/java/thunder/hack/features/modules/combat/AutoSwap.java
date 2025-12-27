package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.gui.notification.Notification;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

public final class AutoSwap extends Module {
    private final Setting<Bind> swapButton = new Setting<>("SwapButton", new Bind(GLFW.GLFW_KEY_CAPS_LOCK, false, false));
    private final Setting<Swap> swapMode = new Setting<>("Swap", Swap.GappleShield);
    private final Setting<Boolean> ignoreUnenchantedTotem = new Setting<>("IgnoreUnenchantedTotem", false);
    private final Setting<Boolean> swapNotify = new Setting<>("SwapNotify", true);
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Matrix);
    private final Setting<Boolean> ncpStrict = new Setting<>("NCPStrict", false);
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", false);
    private final Setting<Boolean> resetAttackCooldown = new Setting<>("ResetAttackCooldown", false);

    private enum Swap {GappleShield, BallShield, GappleBall, BallTotem, TotemTotem}
    private enum Mode {Default, Alternative, Matrix, MatrixPick, NewVersion}

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

        SearchInvResult gapple = InventoryUtility.findItemInInventory(Items.ENCHANTED_GOLDEN_APPLE);
        SearchInvResult crapple = InventoryUtility.findItemInInventory(Items.GOLDEN_APPLE);
        
        Item offHandItem = mc.player.getOffHandStack().getItem();
        Item item = null;
        int itemSlot = -1;

        switch (swapMode.getValue()) {
            case BallShield -> {
                if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.SHIELD)
                    item = Items.PLAYER_HEAD;
                else item = Items.SHIELD;
            }
            case GappleBall -> {
                if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.GOLDEN_APPLE)
                    item = Items.PLAYER_HEAD;
                else item = Items.GOLDEN_APPLE;
            }
            case GappleShield -> {
                if (mc.player.getOffHandStack().isEmpty() || offHandItem == Items.SHIELD) {
                    if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                        item = Items.ENCHANTED_GOLDEN_APPLE;
                    else if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                        item = Items.GOLDEN_APPLE;
                } else {
                    item = Items.SHIELD;
                }
            }
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
            if (!(item == Items.TOTEM_OF_UNDYING && ignoreUnenchantedTotem.getValue() 
                    && !isEnchanted(mc.player.getOffHandStack()))) {
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

            int nearestSlot = findNearestCurrentItem();
            int prevCurrentItem = mc.player.getInventory().selectedSlot;
            
            if (slot >= 9) {
                switch (mode.getValue()) {
                    case Default -> {
                        if (ncpStrict.getValue())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        clickSlot(slot);
                        clickSlot(45);
                        clickSlot(slot);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                    case Alternative -> {
                        if (ncpStrict.getValue())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        clickSlot(slot, nearestSlot, SlotActionType.SWAP);
                        clickSlot(45, nearestSlot, SlotActionType.SWAP);
                        clickSlot(slot, nearestSlot, SlotActionType.SWAP);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                    case Matrix -> {
                        if (ncpStrict.getValue())
                            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));

                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);
                        debug(slot + " " + nearestSlot);

                        sendPacket(new UpdateSelectedSlotC2SPacket(nearestSlot));
                        mc.player.getInventory().selectedSlot = nearestSlot;

                        ItemStack itemstack = mc.player.getOffHandStack();
                        mc.player.setStackInHand(Hand.OFF_HAND, mc.player.getMainHandStack());
                        mc.player.setStackInHand(Hand.MAIN_HAND, itemstack);
                        sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

                        sendPacket(new UpdateSelectedSlotC2SPacket(prevCurrentItem));
                        mc.player.getInventory().selectedSlot = prevCurrentItem;

                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, nearestSlot, SlotActionType.SWAP, mc.player);

                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                        if (resetAttackCooldown.getValue())
                            mc.player.resetLastAttackedTicks();
                    }
                    case MatrixPick -> {
                        debug(slot + " pick");
                        sendPacket(new PickFromInventoryC2SPacket(slot));
                        sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                        int prevSlot = mc.player.getInventory().selectedSlot;
                        mc.execute(() -> mc.player.getInventory().selectedSlot = prevSlot);
                    }
                    case NewVersion -> {
                        debug(slot + " swap");
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                        sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                }
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

    public static int findNearestCurrentItem() {
        int i = mc.player.getInventory().selectedSlot;
        if (i == 8) return 7;
        if (i == 0) return 1;
        return i - 1;
    }

    private int findOtherTotemSlot() {
        ItemStack offhandStack = mc.player.getOffHandStack();
        boolean offhandEnchanted = isEnchanted(offhandStack);
        
        int bestSlot = -1;
        boolean bestEnchanted = false;
        
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                boolean stackEnchanted = isEnchanted(stack);
                
                if (!offhandEnchanted && stackEnchanted) {
                    return i;
                }
                
                if (offhandEnchanted && !stackEnchanted) {
                    return i;
                }
                
                if (bestSlot == -1) {
                    bestSlot = i;
                    bestEnchanted = stackEnchanted;
                }
            }
        }
        
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                boolean stackEnchanted = isEnchanted(stack);
                
                if (!offhandEnchanted && stackEnchanted) {
                    return i;
                }
                
                if (offhandEnchanted && !stackEnchanted) {
                    return i;
                }
                
                if (bestSlot == -1) {
                    bestSlot = i;
                    bestEnchanted = stackEnchanted;
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

