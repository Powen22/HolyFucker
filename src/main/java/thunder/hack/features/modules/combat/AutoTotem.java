package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.world.ExplosionUtility;
import thunder.hack.utility.math.PredictUtility;
import thunder.hack.utility.player.InventoryUtility;

public final class AutoTotem extends Module {
    // Constants
    private static final double DANGER_DISTANCE_SQ = 36.0; // 6 blocks squared
    private static final int BASE_DELAY = 2;
    private static final float PING_DELAY_DIVISOR = 25f;
    private static final int INSTANT_DELAY = 20;

    private final Setting<Boolean> ncpStrict = new Setting<>("NCPStrict", false);
    private final Setting<Boolean> saveEnchantedTotem = new Setting<>("SaveEnchantedTotem", false);
    private final Setting<Boolean> restoreAfterPop = new Setting<>("RestoreAfterPop", true);
    private final Setting<Float> healthF = new Setting<>("HP", 16f, 0f, 36f);
    private final Setting<Boolean> calcAbsorption = new Setting<>("CalcAbsorption", true);
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", false);
    private final Setting<Boolean> resetAttackCooldown = new Setting<>("ResetAttackCooldown", false);
    private final Setting<SettingGroup> safety = new Setting<>("Safety", new SettingGroup(true, 0));
    private final Setting<Boolean> hotbarFallBack = new Setting<>("HotbarFallback", false).addToGroup(safety);
    private final Setting<Boolean> fallBackCalc = new Setting<>("FallBackCalc", true, v -> hotbarFallBack.getValue()).addToGroup(safety);
    private final Setting<Boolean> onFall = new Setting<>("OnFall", true).addToGroup(safety);

    private int delay = 0;

    private Item itemBeforeTotem = null; // Предмет который был до тотема
    private boolean totemWasPlaced = false; // Флаг что тотем был положен в оффхенд
    private int enchantedTotemSlot = -1; // Слот зачарованного тотема, который был заменен на обычный
    private boolean wasEnchantedTotemReplaced = false; // Флаг что зачарованный тотем был заменен на обычный
    private Item itemBeforeCriticalTotem = null; // Предмет который был до тотема при критическом здоровье
    private boolean wasCriticalTotemPlaced = false; // Флаг что тотем был взят при критическом здоровье

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT);
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (fullNullCheck()) return;
        swapTo(getItemSlot());

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
                // Сбрасываем флаг восстановления после отхила, так как тотем был использован
                wasCriticalTotemPlaced = false;
                itemBeforeCriticalTotem = null;
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
                }
            }
        }
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

    public int getItemSlot() {
        if (mc.player == null || mc.world == null) return -1;

        Item offHandItem = mc.player.getOffHandStack().getItem();

        int itemSlot = -1;
        Item item = null;
        
        // Восстановление зачарованного тотема после отхила (приоритетная проверка)
        if (saveEnchantedTotem.getValue() && wasEnchantedTotemReplaced 
                && offHandItem == Items.TOTEM_OF_UNDYING && !isEnchanted(mc.player.getOffHandStack())
                && getTriggerHealth() > healthF.getValue()) {
            // Ищем зачарованный тотем для восстановления
            int enchantedSlot = -1;
            
            // Сначала проверяем сохраненный слот
            if (enchantedTotemSlot != -1 && enchantedTotemSlot < 36) {
                ItemStack stack = mc.player.getInventory().getStack(enchantedTotemSlot);
                if (stack.getItem() == Items.TOTEM_OF_UNDYING && isEnchanted(stack)) {
                    enchantedSlot = enchantedTotemSlot;
                }
            }
            
            // Если не нашли в сохраненном слоте, ищем в инвентаре
            if (enchantedSlot == -1) {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() == Items.TOTEM_OF_UNDYING && isEnchanted(stack)) {
                        enchantedSlot = i;
                        break;
                    }
                }
            }
            
            // Если нашли зачарованный тотем, восстанавливаем его
            if (enchantedSlot != -1) {
                wasEnchantedTotemReplaced = false;
                enchantedTotemSlot = -1;
                return enchantedSlot;
            } else {
                // Если не нашли зачарованный тотем, сбрасываем флаг
                wasEnchantedTotemReplaced = false;
                enchantedTotemSlot = -1;
            }
        }
        
        // Восстановление предмета после отхила (когда здоровье становится выше критического)
        // Не восстанавливаем если был восстановлен зачарованный тотем или если в оффхенде зачарованный тотем
        if (wasCriticalTotemPlaced && itemBeforeCriticalTotem != null
                && offHandItem == Items.TOTEM_OF_UNDYING && getTriggerHealth() > healthF.getValue()
                && !(saveEnchantedTotem.getValue() && isEnchanted(mc.player.getOffHandStack()))) {
            // Ищем предмет который был до тотема
            int itemSlotToRestore = -1;
            
            // Ищем предмет в инвентаре
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == itemBeforeCriticalTotem) {
                    itemSlotToRestore = i;
                    break;
                }
            }
            
            // Если нашли предмет, восстанавливаем его
            if (itemSlotToRestore != -1) {
                wasCriticalTotemPlaced = false;
                Item restoredItem = itemBeforeCriticalTotem;
                itemBeforeCriticalTotem = null;
                // Устанавливаем item для восстановления
                item = restoredItem;
                return itemSlotToRestore;
            } else {
                // Если не нашли предмет, сбрасываем флаг
                wasCriticalTotemPlaced = false;
                itemBeforeCriticalTotem = null;
            }
        }
        // Восстановление предмета после попа тотема
        if (restoreAfterPop.getValue() && !totemWasPlaced && itemBeforeTotem != null 
                && offHandItem != Items.TOTEM_OF_UNDYING && mc.player.getOffHandStack().isEmpty()) {
            item = itemBeforeTotem;
            itemBeforeTotem = null; // Сбрасываем после восстановления
        }

        if (getTriggerHealth() <= healthF.getValue() && hasAvailableTotem()) {
            // Сохраняем предмет который был в оффхенде до тотема при критическом здоровье
            if (offHandItem != Items.TOTEM_OF_UNDYING && offHandItem != Items.AIR 
                    && !mc.player.getOffHandStack().isEmpty()) {
                // Сохраняем только если еще не сохранен (чтобы не перезаписывать при повторном падении здоровья)
                if (!wasCriticalTotemPlaced) {
                    itemBeforeCriticalTotem = offHandItem;
                    wasCriticalTotemPlaced = true;
                }
            }
            item = Items.TOTEM_OF_UNDYING;
        } else if (getTriggerHealth() > healthF.getValue() && wasCriticalTotemPlaced) {
            // Если здоровье выше критического, но тотем еще в оффхенде, не сбрасываем флаг
            // (восстановление произойдет в проверке выше)
        }

        if (onFall.getValue() && (getTriggerHealth()) - (((mc.player.fallDistance - 3) / 2F) + 3.5F) < 0.5)
            item = Items.TOTEM_OF_UNDYING;

        // Свап Totem на Totem: если в оффхенде тотем, ищем лучший в инвентаре
        if (offHandItem == Items.TOTEM_OF_UNDYING) {
            ItemStack offhandTotem = mc.player.getOffHandStack();
            boolean offhandTotemEnchanted = isEnchanted(offhandTotem);
            
            // Если в оффхенде зачарованный тотем и включено сохранение
            if (saveEnchantedTotem.getValue() && offhandTotemEnchanted) {
                // Заменяем зачарованный тотем на обычный только при критическом здоровье
                if (getTriggerHealth() <= healthF.getValue()) {
                    // Ищем обычный тотем для замены
                    int normalTotemSlot = -1;
                    
                    // Сначала проверяем инвентарь (9-35)
                    for (int i = 9; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (stack.getItem() == Items.TOTEM_OF_UNDYING && !isEnchanted(stack)) {
                            normalTotemSlot = i;
                            break;
                        }
                    }
                    
                    // Если не нашли в инвентаре, проверяем хотбар (0-8)
                    if (normalTotemSlot == -1) {
                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = mc.player.getInventory().getStack(i);
                            if (stack.getItem() == Items.TOTEM_OF_UNDYING && !isEnchanted(stack)) {
                                normalTotemSlot = i;
                                break;
                            }
                        }
                    }
                    
                    // Если нашли обычный тотем, заменяем зачарованный на него
                    if (normalTotemSlot != -1) {
                        // Помечаем что зачарованный тотем был заменен
                        wasEnchantedTotemReplaced = true;
                        // Сохраняем слот обычного тотема - после свапа зачарованный может быть там
                        enchantedTotemSlot = normalTotemSlot;
                        
                        item = Items.TOTEM_OF_UNDYING;
                        itemSlot = normalTotemSlot;
                        return itemSlot;
                    }
                }
                // Если здоровье не критическое и не было замены, не заменяем зачарованный тотем
                return -1;
            }
            
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
                    // Если saveEnchantedTotem включен, заменяем только если в оффхенде обычный тотем
                    // (зачарованный уже обработан выше и не будет заменен)
                    shouldSwap = !offhandTotemEnchanted;
                }
                
                if (shouldSwap) {
                    // Устанавливаем item и itemSlot для свапа
                    item = Items.TOTEM_OF_UNDYING;
                    itemSlot = bestTotemSlot;
                    // Сохраняем предмет который был до тотема для восстановления после попа
                    // В этом блоке мы знаем, что в оффхенде тотем, но сохраняем для случая свапа
                    totemWasPlaced = true;
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
        
        // Если в оффхенде уже зачарованный тотем и включено сохранение
        if (item == Items.TOTEM_OF_UNDYING && offhandItemCurrent == Items.TOTEM_OF_UNDYING 
                && saveEnchantedTotem.getValue() && isEnchanted(mc.player.getOffHandStack())) {
            // Разрешаем замену только при критическом здоровье (зачарованный на обычный)
            // Если здоровье не критическое, не заменяем
            if (getTriggerHealth() > healthF.getValue()) {
                return -1;
            }
            // Если здоровье критическое, продолжаем поиск обычного тотема для замены
        }
        
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
