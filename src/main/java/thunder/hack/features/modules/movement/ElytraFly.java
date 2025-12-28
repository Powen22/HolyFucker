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
        
        boolean isFuntime = mc.getNetworkHandler() == null || 
                           mc.getNetworkHandler().getServerInfo() == null || 
                           !mc.getNetworkHandler().getServerInfo().address.contains("funtime");

        int activationDelay = isFuntime ? 0 : 0;

        double liftBoost = isFuntime ? 0.051 : 10;

        boolean hasElytra = false;
        int elytraSlot = -1;
        
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.getItem() == Items.ELYTRA) {
                hasElytra = true;
                elytraSlot = slot;
                break;
            }
        }

        if (!hasElytra) {
            sendMessage(isRu() ? "Элитры не найдены в хотбаре!" : "Elytra not found in hotbar!");
            disable();
            return;
        }
        

        if (mc.player.isOnGround()) {
            mc.player.jump();
            return;
        }

        boolean canActivate = !mc.player.isOnGround() && 
                              !mc.player.isTouchingWater() && 
                              !mc.player.isInLava() && 
                              !mc.player.isFallFlying();

        if (canActivate && timer1.passedMs(activationDelay)) {
            activateElytra(elytraSlot);
        }

        if (mc.player.isFallFlying()) {
            controlFlight(liftBoost);
        }
    }

    private void activateElytra(int elytraSlot) {
        timer2.reset();

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,  // 0 = inventoryId (инвентарь игрока)
            6,                                      // slotId (6 = слот нагрудника в контейнере)
            elytraSlot,                             // mouseButton (номер слота в хотбаре для свапа)
            SlotActionType.SWAP,                     // Тип клика (SWAP = обмен F+1-9)
            mc.player
        );

        mc.player.startFallFlying();

        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            6,
            elytraSlot,
            SlotActionType.SWAP,
            mc.player
        );

        oldItem = elytraSlot;
        timer1.reset();
    }

    private void controlFlight(double liftBoost) {
        double currentY = mc.player.getY();
        double maxY = stopY.getValue();
        
        if (currentY >= maxY) {
            mc.player.setVelocity(0.0, 0.0, 0.0);
        } else {
            mc.player.setVelocity(0.0, mc.player.getVelocity().y + liftBoost, 0.0);
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        mc.player.setYaw(0.0f);
        mc.player.setPitch(0.0f);
        mc.player.prevYaw = 0.0f;
        mc.player.prevPitch = 0.0f;
    }

    @Override
    public void onDisable() {
        super.onDisable();

        if (oldItem != -1 && !fullNullCheck()) {
            ItemStack chestplate = mc.player.getInventory().armor.get(2);
            boolean elytraEquipped = chestplate.getItem() == Items.ELYTRA;

            ItemStack hotbarItem = mc.player.getInventory().getStack(oldItem);
            boolean armorInHotbar = hotbarItem.getItem() instanceof ArmorItem;
            
            if (elytraEquipped && armorInHotbar) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    6,
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

