package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.gui.notification.Notification;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IMinecraftClient;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.world.ExplosionUtility;
import thunder.hack.utility.math.PredictUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

public final class AutoTotem extends Module {
    // Constants
    private static final double DANGER_DISTANCE_SQ = 36.0; // 6 blocks squared
    private static final int ANCHOR_CHECK_RADIUS = 6;
    private static final int BASE_DELAY = 2;
    private static final float PING_DELAY_DIVISOR = 25f;
    private static final int INSTANT_DELAY = 20;

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Matrix);
    private final Setting<OffHand> offhand = new Setting<>("Item", OffHand.Totem);
    private final Setting<Boolean> ncpStrict = new Setting<>("NCPStrict", false);
    private final Setting<Boolean> saveEnchantedTotem = new Setting<>("SaveEnchantedTotem", false);
    private final Setting<Boolean> restoreAfterPop = new Setting<>("RestoreAfterPop", true);
    private final Setting<Float> healthF = new Setting<>("HP", 16f, 0f, 36f);
    private final Setting<Float> healthS = new Setting<>("ShieldGappleHp", 16f, 0f, 20f, v -> offhand.getValue() == OffHand.Shield);
    private final Setting<Boolean> calcAbsorption = new Setting<>("CalcAbsorption", true);
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", false);
    private final Setting<Boolean> resetAttackCooldown = new Setting<>("ResetAttackCooldown", false);
    private final Setting<SettingGroup> safety = new Setting<>("Safety", new SettingGroup(true, 0));
    private final Setting<Boolean> hotbarFallBack = new Setting<>("HotbarFallback", false).addToGroup(safety);
    private final Setting<Boolean> fallBackCalc = new Setting<>("FallBackCalc", true, v -> hotbarFallBack.getValue()).addToGroup(safety);
    private final Setting<Boolean> onElytra = new Setting<>("OnElytra", false).addToGroup(safety);
    private final Setting<Boolean> onFall = new Setting<>("OnFall", true).addToGroup(safety);
    private final Setting<Boolean> onCrystal = new Setting<>("OnCrystal", true).addToGroup(safety);
    private final Setting<Boolean> onObsidianPlace = new Setting<>("OnObsidianPlace", false).addToGroup(safety);
    private final Setting<Boolean> onCrystalInHand = new Setting<>("OnCrystalInHand", false).addToGroup(safety);
    private final Setting<Boolean> onMinecartTnt = new Setting<>("OnMinecartTNT", true).addToGroup(safety);
    private final Setting<Boolean> onCreeper = new Setting<>("OnCreeper", true).addToGroup(safety);
    private final Setting<Boolean> onAnchor = new Setting<>("OnAnchor", true).addToGroup(safety);
    private final Setting<Boolean> onTnt = new Setting<>("OnTNT", true).addToGroup(safety);
    public final Setting<RCGap> rcGap = new Setting<>("RightClickGapple", RCGap.Off);
    private final Setting<Boolean> crappleSpoof = new Setting<>("CrappleSpoof", true, v -> offhand.getValue() == OffHand.GApple);

    private enum OffHand {Totem, Crystal, GApple, Shield}

    private enum Mode {Default, Alternative, Matrix, MatrixPick, NewVersion}

    public enum RCGap {Off, Always, OnlySafe}

    private int delay = 0;

    private Item itemBeforeTotem = null; // Предмет который был до тотема
    private boolean totemWasPlaced = false; // Флаг что тотем был положен в оффхенд

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT);
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (fullNullCheck()) return;
        swapTo(getItemSlot());

        if (rcGap.not(RCGap.Off) && (mc.player.getMainHandStack().getItem() instanceof SwordItem) && mc.options.useKey.isPressed() && !mc.player.isUsingItem())
            ((IMinecraftClient) mc).idoItemUse();

        delay--;
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (fullNullCheck()) return;
        // Детект попа тотема (status 35 = totem of undying used)
        if (e.getPacket() instanceof EntityStatusS2CPacket status) {
            if (status.getStatus() == 35 && status.getEntity(mc.world) == mc.player) {
                // Тотем был использован - восстанавливаем предыдущий предмет
                if (restoreAfterPop.getValue() && itemBeforeTotem != null && totemWasPlaced) {
                    // Небольшая задержка перед восстановлением
                    mc.execute(() -> {
                        delay = 0; // Сбрасываем delay чтобы свап сработал
                        totemWasPlaced = false;
                    });
                }
            }
        }
        
        if (e.getPacket() instanceof EntitySpawnS2CPacket spawn) {
            if (spawn.getEntityType() == EntityType.END_CRYSTAL) {
                Vec3d spawnPos = new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ());
                if (getPlayerPos().squaredDistanceTo(spawnPos) < DANGER_DISTANCE_SQ) {
                    if (hotbarFallBack.getValue()) {
                        if (fallBackCalc.getValue() && ExplosionUtility.getExplosionDamageWPredict(spawnPos, mc.player, PredictUtility.createBox(getPlayerPos(), mc.player), false) < getTriggerHealth() + 4f)
                            return;
                        runInstant();
                    }

                    if (onCrystal.getValue()) {
                        if (getTriggerHealth() - ExplosionUtility.getExplosionDamageWPredict(spawnPos, mc.player, PredictUtility.createBox(getPlayerPos(), mc.player), false) < 0.5) {
                            int slot = findTotemSlot();
                            swapTo(slot);
                            debug("spawn switch");
                        }
                    }
                }
            }
        }

        if (e.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (blockUpdate.getState().getBlock() == Blocks.OBSIDIAN && onObsidianPlace.getValue()) {
                if (getPlayerPos().squaredDistanceTo(blockUpdate.getPos().toCenterPos()) < DANGER_DISTANCE_SQ && delay <= 0) {
                    runInstant();
                }
            }
        }
    }

    private int findTotemSlot() {
        if (fullNullCheck()) return -1;
        // Сначала проверяем инвентарь (9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                // Пропускаем зачарованные тотемы если включено сохранение
                if (saveEnchantedTotem.getValue() && isEnchanted(stack)) {
                    continue;
                }
                return i;
            }
        }
        // Потом хотбар (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                // Пропускаем зачарованные тотемы если включено сохранение
                if (saveEnchantedTotem.getValue() && isEnchanted(stack)) {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private float getTriggerHealth() {
        if (fullNullCheck()) return 0f;
        return mc.player.getHealth() + (calcAbsorption.getValue() ? mc.player.getAbsorptionAmount() : 0f);
    }

    private void runInstant() {
        if (fullNullCheck()) return;
        int totemSlot = findAvailableTotemSlot();
        if (totemSlot != -1) {
            if (totemSlot < 9) {
                // Hotbar - switch to it
                InventoryUtility.switchTo(totemSlot);
            } else {
                // Inventory - swap to offhand
                if (!hotbarFallBack.getValue()) swapTo(totemSlot);
                else mc.interactionManager.pickFromInventory(totemSlot);
            }
            delay = INSTANT_DELAY;
        }
    }

    // Находит слот доступного тотема с учётом saveEnchantedTotem
    private int findAvailableTotemSlot() {
        if (fullNullCheck()) return -1;
        // Сначала проверяем инвентарь (9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                if (!saveEnchantedTotem.getValue() || !isEnchanted(stack)) {
                    return i;
                }
            }
        }
        // Потом хотбар (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                if (!saveEnchantedTotem.getValue() || !isEnchanted(stack)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    public void swapTo(int slot) {
        if (fullNullCheck() || slot == -1 || delay > 0) return;
        // Block when container is open, unless GuiMove is enabled
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

    public static int findNearestCurrentItem() {
        int i = mc.player.getInventory().selectedSlot;
        if (i == 8) return 7;
        if (i == 0) return 1;
        return i - 1;
    }

    public int getItemSlot() {
        if (mc.player == null || mc.world == null) return -1;

        SearchInvResult gapple = InventoryUtility.findItemInInventory(Items.ENCHANTED_GOLDEN_APPLE);
        SearchInvResult crapple = InventoryUtility.findItemInInventory(Items.GOLDEN_APPLE);
        SearchInvResult shield = InventoryUtility.findItemInInventory(Items.SHIELD);
        Item offHandItem = mc.player.getOffHandStack().getItem();

        int itemSlot = -1;
        Item item = null;
        switch (offhand.getValue()) {
            case Totem -> {
                // Восстановление предмета после попа тотема
                if (restoreAfterPop.getValue() && !totemWasPlaced && itemBeforeTotem != null 
                        && offHandItem != Items.TOTEM_OF_UNDYING && mc.player.getOffHandStack().isEmpty()) {
                    item = itemBeforeTotem;
                    itemBeforeTotem = null; // Сбрасываем после восстановления
                }
            }

            case Crystal -> item = Items.END_CRYSTAL;

            case GApple -> {
                if (crappleSpoof.getValue()) {
                    var absorption = mc.player.getStatusEffect(StatusEffects.ABSORPTION);
                    if (absorption != null && absorption.getAmplifier() > 2) {
                        if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                            item = Items.GOLDEN_APPLE;
                        else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                            item = Items.ENCHANTED_GOLDEN_APPLE;
                    } else {
                        if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                            item = Items.ENCHANTED_GOLDEN_APPLE;
                        else if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                            item = Items.GOLDEN_APPLE;
                    }
                } else {
                    if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                        item = Items.GOLDEN_APPLE;
                    else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                        item = Items.ENCHANTED_GOLDEN_APPLE;
                }
            }

            case Shield -> {
                if (shield.found() || offHandItem == Items.SHIELD) {
                    if (getTriggerHealth() <= healthS.getValue()) {
                        if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                            item = Items.GOLDEN_APPLE;
                        else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                            item = Items.ENCHANTED_GOLDEN_APPLE;
                    } else {
                        if (!mc.player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) item = Items.SHIELD;
                        else {
                            if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                                item = Items.GOLDEN_APPLE;
                            else if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                                item = Items.ENCHANTED_GOLDEN_APPLE;
                        }
                    }
                } else if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                    item = Items.GOLDEN_APPLE;
            }
        }

        if (getTriggerHealth() <= healthF.getValue() && hasAvailableTotem())
            item = Items.TOTEM_OF_UNDYING;

        if (!rcGap.is(RCGap.Off) && (mc.player.getMainHandStack().getItem() instanceof SwordItem) && mc.options.useKey.isPressed() && !(offHandItem instanceof ShieldItem)) {
            if (rcGap.is(RCGap.Always) || (rcGap.is(RCGap.OnlySafe) && getTriggerHealth() > healthF.getValue())) {
                if (crapple.found() || offHandItem == Items.GOLDEN_APPLE)
                    item = Items.GOLDEN_APPLE;
                if (gapple.found() || offHandItem == Items.ENCHANTED_GOLDEN_APPLE)
                    item = Items.ENCHANTED_GOLDEN_APPLE;
            }
        }

        if (onFall.getValue() && (getTriggerHealth()) - (((mc.player.fallDistance - 3) / 2F) + 3.5F) < 0.5)
            item = Items.TOTEM_OF_UNDYING;

        if (onElytra.getValue() && mc.player.isFallFlying())
            item = Items.TOTEM_OF_UNDYING;

        if (onCrystalInHand.getValue()) {
            for (PlayerEntity pl : Managers.ASYNC.getAsyncPlayers()) {
                if (Managers.FRIEND.isFriend(pl)) continue;
                if (pl == mc.player) continue;
                if (getPlayerPos().squaredDistanceTo(pl.getPos()) < DANGER_DISTANCE_SQ) {
                    Item mainHand = pl.getMainHandStack().getItem();
                    Item offHand = pl.getOffHandStack().getItem();
                    if (mainHand == Items.OBSIDIAN || mainHand == Items.END_CRYSTAL
                            || offHand == Items.OBSIDIAN || offHand == Items.END_CRYSTAL) {
                        item = Items.TOTEM_OF_UNDYING;
                        break;
                    }
                }
            }
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || !entity.isAlive()) continue;
            if (getPlayerPos().squaredDistanceTo(entity.getPos()) > DANGER_DISTANCE_SQ) continue;

            if (onCrystal.getValue()) {
                if (entity instanceof EndCrystalEntity) {
                    if (getTriggerHealth() - ExplosionUtility.getExplosionDamageWPredict(entity.getPos(), mc.player, PredictUtility.createBox(getPlayerPos(), mc.player), false) < 0.5) {
                        item = Items.TOTEM_OF_UNDYING;
                        break;
                    }
                }
            }

            if (onTnt.getValue()) {
                if (entity instanceof TntEntity) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }

            if (onMinecartTnt.getValue()) {
                if (entity instanceof TntMinecartEntity) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }

            if (onCreeper.getValue()) {
                if (entity instanceof CreeperEntity) {
                    item = Items.TOTEM_OF_UNDYING;
                    break;
                }
            }
        }

        if (onAnchor.getValue()) {
            BlockPos playerPos = mc.player.getBlockPos();
            anchorSearch:
            for (int x = -ANCHOR_CHECK_RADIUS; x <= ANCHOR_CHECK_RADIUS; x++) {
                for (int y = -ANCHOR_CHECK_RADIUS; y <= ANCHOR_CHECK_RADIUS; y++) {
                    for (int z = -ANCHOR_CHECK_RADIUS; z <= ANCHOR_CHECK_RADIUS; z++) {
                        BlockPos bp = playerPos.add(x, y, z);
                        if (mc.world.getBlockState(bp).getBlock() == Blocks.RESPAWN_ANCHOR) {
                            item = Items.TOTEM_OF_UNDYING;
                            break anchorSearch;
                        }
                    }
                }
            }
        }

        // Свап Totem на Totem: если в оффхенде тотем, ищем лучший в инвентаре
        if (offHandItem == Items.TOTEM_OF_UNDYING) {
            ItemStack offhandTotem = mc.player.getOffHandStack();
            boolean offhandTotemEnchanted = isEnchanted(offhandTotem);
            
            // Ищем лучший тотем в инвентаре
            int bestTotemSlot = -1;
            boolean bestTotemEnchanted = false;
            
            // Сначала проверяем инвентарь (9-35)
            for (int i = 9; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    boolean stackEnchanted = isEnchanted(stack);
                    
                    // Пропускаем зачарованные тотемы если включено сохранение
                    if (saveEnchantedTotem.getValue() && stackEnchanted) {
                        continue;
                    }
                    
                    // Выбираем лучший тотем:
                    // 1. Зачарованный лучше обычного (если saveEnchantedTotem выключен)
                    // 2. Если оба зачарованные или оба обычные - берем первый найденный
                    if (bestTotemSlot == -1) {
                        bestTotemSlot = i;
                        bestTotemEnchanted = stackEnchanted;
                    } else if (!saveEnchantedTotem.getValue()) {
                        // Если нашли зачарованный, а текущий лучший обычный - заменяем
                        if (stackEnchanted && !bestTotemEnchanted) {
                            bestTotemSlot = i;
                            bestTotemEnchanted = true;
                        }
                    }
                }
            }
            
            // Если не нашли в инвентаре, проверяем хотбар (0-8)
            if (bestTotemSlot == -1) {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                        boolean stackEnchanted = isEnchanted(stack);
                        
                        // Пропускаем зачарованные тотемы если включено сохранение
                        if (saveEnchantedTotem.getValue() && stackEnchanted) {
                            continue;
                        }
                        
                        if (bestTotemSlot == -1) {
                            bestTotemSlot = i;
                            bestTotemEnchanted = stackEnchanted;
                        } else if (!saveEnchantedTotem.getValue()) {
                            if (stackEnchanted && !bestTotemEnchanted) {
                                bestTotemSlot = i;
                                bestTotemEnchanted = true;
                            }
                        }
                    }
                }
            }
            
            // Если нашли лучший тотем, используем его
            if (bestTotemSlot != -1) {
                // Заменяем только если новый тотем лучше текущего
                // (зачарованный лучше обычного, если saveEnchantedTotem выключен)
                boolean shouldSwap = false;
                if (!saveEnchantedTotem.getValue()) {
                    // Заменяем если нашли зачарованный, а в оффхенде обычный
                    shouldSwap = bestTotemEnchanted && !offhandTotemEnchanted;
                } else {
                    // Если saveEnchantedTotem включен, заменяем только если нашли другой тотем
                    // (не тот же самый)
                    shouldSwap = true; // Всегда заменяем на другой тотем из инвентаря
                }
                
                if (shouldSwap) {
                    // Устанавливаем item и itemSlot для свапа
                    item = Items.TOTEM_OF_UNDYING;
                    itemSlot = bestTotemSlot;
                    // Сохраняем предмет который был до тотема для восстановления после попа
                    if (offHandItem != Items.TOTEM_OF_UNDYING) {
                        if (offHandItem != Items.AIR && !mc.player.getOffHandStack().isEmpty()) {
                            itemBeforeTotem = offHandItem;
                        }
                        totemWasPlaced = true;
                    }
                    // Возвращаем слот сразу, пропуская дальнейшие проверки
                    return itemSlot;
                }
            }
        }
        
        // Check if item is already in offhand or mainhand being used
        if (item == null) return -1;
        
        Item offhandItemCurrent = mc.player.getOffHandStack().getItem();
        // For skulls, check if any skull is in offhand
        boolean itemAlreadyInOffhand = (item == Items.PLAYER_HEAD && isSkullItem(offhandItemCurrent)) 
                || offhandItemCurrent == item;
        
        // Если itemSlot уже установлен для свапа Totem на Totem, пропускаем проверку itemAlreadyInOffhand
        boolean isTotemToTotemSwap = item == Items.TOTEM_OF_UNDYING 
                && offHandItem == Items.TOTEM_OF_UNDYING 
                && itemSlot != -1;
        
        if (itemAlreadyInOffhand && !isTotemToTotemSwap) {
            return -1;
        }
        
        if (item == mc.player.getMainHandStack().getItem() && mc.options.useKey.isPressed()) return -1;

        // Сначала проверяем инвентарь (9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            
            // Check if item matches (for skulls, match any skull type)
            boolean itemMatches = (item == Items.PLAYER_HEAD && isSkullItem(stack.getItem())) 
                    || stack.getItem() == item;
            
            if (itemMatches) {
                // Пропускаем зачарованные тотемы если включено сохранение
                if (item == Items.TOTEM_OF_UNDYING && saveEnchantedTotem.getValue() && isEnchanted(stack)) {
                    continue;
                }
                itemSlot = i;
                break;
            }
        }
        
        // Если не нашли в инвентаре, проверяем хотбар (0-8)
        if (itemSlot == -1) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                
                // Check if item matches (for skulls, match any skull type)
                boolean itemMatches = (item == Items.PLAYER_HEAD && isSkullItem(stack.getItem())) 
                        || stack.getItem() == item;
                
                if (itemMatches) {
                    // Пропускаем зачарованные тотемы если включено сохранение
                    if (item == Items.TOTEM_OF_UNDYING && saveEnchantedTotem.getValue() && isEnchanted(stack)) {
                        continue;
                    }
                    itemSlot = i;
                    break;
                }
            }
        }

        // Сохраняем предмет который был до тотема для восстановления после попа
        if (item == Items.TOTEM_OF_UNDYING && itemSlot != -1 && offHandItem != Items.TOTEM_OF_UNDYING) {
            if (offHandItem != Items.AIR && !mc.player.getOffHandStack().isEmpty()) {
                itemBeforeTotem = offHandItem;
            }
            totemWasPlaced = true;
        }

        return itemSlot;
    }

    private Vec3d getPlayerPos() {
        if (fullNullCheck()) return Vec3d.ZERO;
        return mc.player.getPos();
    }

    private boolean isEnchanted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.hasEnchantments();
    }

    private boolean hasEnchantedTotem() {
        if (fullNullCheck()) return false;
        // Check offhand
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING && isEnchanted(mc.player.getOffHandStack())) {
            return true;
        }
        // Check inventory
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING && isEnchanted(stack)) {
                return true;
            }
        }
        return false;
    }

    // Проверяет есть ли доступный тотем с учётом saveEnchantedTotem
    private boolean hasAvailableTotem() {
        if (fullNullCheck()) return false;
        // Check offhand
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (!saveEnchantedTotem.getValue() || !isEnchanted(mc.player.getOffHandStack())) {
                return true;
            }
        }
        // Check inventory
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                if (!saveEnchantedTotem.getValue() || !isEnchanted(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSkullItem(Item item) {
        return item == Items.PLAYER_HEAD;
    }
}
