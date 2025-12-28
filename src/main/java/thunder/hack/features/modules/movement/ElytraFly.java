package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec2f;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import static thunder.hack.features.modules.client.ClientSettings.isRu;

public class ElytraFly extends Module {
    public ElytraFly() {
        super("ElytraFly", Category.MOVEMENT);
    }

    private final Setting<Float> stopY = new Setting<>("Stop Y", 255.0f, 100.0f, 10000.0f);
    
    private final Timer timer1 = new Timer();  // Задержка между попытками активации
    private final Timer timer2 = new Timer();  // Вспомогательный таймер
    private int oldItem = -1;                  // Запоминаем слот элитр для возврата брони
    public Vec2f rotateVector = new Vec2f(0.0f, 0.0f);

    @Override
    public void onUpdate() {
        if (fullNullCheck()) return;

        // Проверка сервера Funtime
        // В оригинале: !isConnectedToServer("funtime") означает, что если НЕ подключен к funtime, то это funtime режим
        boolean isFuntime = mc.getNetworkHandler() == null || 
                           mc.getNetworkHandler().getServerInfo() == null || 
                           !mc.getNetworkHandler().getServerInfo().address.contains("funtime");
        
        // Задержка активации (0мс для Funtime, 10мс для других серверов)
        int activationDelay = isFuntime ? 0 : 0;
        
        // Усиление подъема (разное для серверов)
        // Funtime более строгий → меньше подъем (0.051)
        // Другие серверы - очень быстрый подъем (0.12 для максимальной скорости)
        double liftBoost = isFuntime ? 0.051 : 10;
        
        // Поиск элитр в хотбаре (слоты 0-8)
        boolean hasElytra = false;
        int elytraSlot = -1;
        
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == Items.ELYTRA) {
                hasElytra = true;
                elytraSlot = slot;
                break; // Нашли первую элитру - выходим
            }
        }
        
        // Если элитр нет - выключить модуль
        if (!hasElytra) {
            sendMessage(isRu() ? "Элитры не найдены в хотбаре!" : "Elytra not found in hotbar!");
            disable();
            return;
        }
        
        // Если на земле - прыгнуть (элитры активируются только в воздухе)
        if (mc.player.isOnGround()) {
            mc.player.jump();
            return; // Пропустить остальную логику в этом тике
        }
        
        // Проверка условий активации
        // Все условия должны быть выполнены:
        // ✅ Не на земле — нельзя активировать элитры на земле
        // ✅ Не в воде — элитры не работают под водой
        // ✅ Не в лаве — элитры не работают в лаве
        // ✅ Полет не активен — не пытаться активировать повторно
        boolean canActivate = !mc.player.isOnGround() && 
                              !mc.player.isTouchingWater() && 
                              !mc.player.isInLava() && 
                              !mc.player.isFallFlying();
        
        // Активация полета с проверкой таймера (защита от спама пакетов)
        if (canActivate && timer1.passedMs(activationDelay)) {
            activateElytra(elytraSlot);
        }
        
        // Управление полетом (если полет уже активен)
        if (mc.player.isFallFlying()) {
            controlFlight(liftBoost);
        }
    }

    /**
     * Активировать элитры через свап с нагрудником
     * 
     * КРИТИЧЕСКИЙ МЕХАНИЗМ ОБХОДА:
     * 1. Свапаем элитры из хотбара в нагрудник
     * 2. Активируем полет (клиент + сервер)
     * 3. МГНОВЕННО возвращаем элитры обратно в хотбар
     * 
     * Почему это работает:
     * - Сервер проверяет наличие элитр только в момент получения пакета активации
     * - После активации сервер не проверяет повторно каждый тик
     * - Игрок продолжает лететь даже без элитр в нагруднике
     */
    private void activateElytra(int elytraSlot) {
        timer2.reset();
        
        // ШАГ 1: Свап элитр в нагрудник
        // windowClick(0, 6, elytraSlot, ClickType.SWAP) в оригинале
        // Берет предмет из слота 6 (нагрудник) и меняет местами с предметом из слота elytraSlot (хотбар)
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,  // 0 = inventoryId (инвентарь игрока)
            6,                                      // slotId (6 = слот нагрудника в контейнере)
            elytraSlot,                             // mouseButton (номер слота в хотбаре для свапа)
            SlotActionType.SWAP,                     // Тип клика (SWAP = обмен F+1-9)
            mc.player
        );
        
        // ШАГ 2: Активация полета на клиенте
        // Устанавливает флаг elytraFlying = true на клиенте, запускает анимацию полета
        mc.player.startFallFlying();
        
        // ШАГ 3: Отправка пакета на сервер
        // Сервер проверяет: игрок в воздухе? элитры в нагруднике? элитры не сломаны?
        // Если все ОК → сервер разрешает полет
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        
        // ШАГ 4: МГНОВЕННЫЙ обратный свап
        // Элитры возвращаются в хотбар, броня возвращается в нагрудник
        // Но полет уже активирован! Сервер не проверяет повторно каждый тик
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            6,
            elytraSlot,
            SlotActionType.SWAP,
            mc.player
        );
        
        // Финализация: запоминаем слот элитр для возврата брони при выключении
        oldItem = elytraSlot;
        timer1.reset(); // Сбрасываем таймер для следующей попытки через activationDelay мс
    }
    
    /**
     * Управление высотой и скоростью полета
     * 
     * Физика:
     * - getMotion().y — текущая вертикальная скорость (м/тик)
     * - + liftBoost — добавляем ускорение каждый тик
     * - При достижении stopY → полная остановка
     */
    private void controlFlight(double liftBoost) {
        double currentY = mc.player.getY();
        double maxY = stopY.getValue();
        
        if (currentY >= maxY) {
            // Достигли максимальной высоты - остановить (зависание в воздухе)
            mc.player.setVelocity(0.0, 0.0, 0.0);
        } else {
            // Продолжить подъем
            // Добавляем вертикальную скорость каждый тик
            mc.player.setVelocity(0.0, mc.player.getVelocity().y + liftBoost, 0.0);
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        // Фиксация ротации для обхода античита
        // 
        // Античиты детектируют читы по:
        // 🚫 Резким поворотам головы (snapAim)
        // 🚫 Полету с неестественными углами
        // 🚫 Движению без изменения взгляда
        //
        // Фиксация на 0°:
        // - Yaw 0 = взгляд строго на север
        // - Pitch 0 = взгляд горизонтально
        // - Выглядит как статичный полет вперед
        mc.player.setYaw(0.0f);
        mc.player.setPitch(0.0f);
        mc.player.prevYaw = 0.0f;
        mc.player.prevPitch = 0.0f;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        
        // Логика восстановления: вернуть броню обратно при выключении модуля
        if (oldItem != -1 && !fullNullCheck()) {
            // Проверить, что в слоте нагрудника элитры (armor.get(2) = нагрудник)
            ItemStack chestplate = mc.player.getInventory().armor.get(2);
            boolean elytraEquipped = chestplate.getItem() == Items.ELYTRA;
            
            // И что в сохраненном слоте хотбара есть броня
            ItemStack hotbarItem = mc.player.getInventory().getStack(oldItem);
            boolean armorInHotbar = hotbarItem.getItem() instanceof ArmorItem;
            
            if (elytraEquipped && armorInHotbar) {
                // Свапнуть обратно: броня возвращается в нагрудник, элитры в хотбар
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    6,  // Слот нагрудника в контейнере
                    oldItem,
                    SlotActionType.SWAP,
                    mc.player
                );
            }
            
            oldItem = -1;
        }
    }

    public Vec2f getRotateVector() {
        return this.rotateVector;
    }
}

