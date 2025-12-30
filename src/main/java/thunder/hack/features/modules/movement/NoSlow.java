package thunder.hack.features.modules.movement;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.SettingGroup;

public class NoSlow extends Module {
    public NoSlow() {
        super("NoSlow", Category.MOVEMENT);
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.Matrix);
    private final Setting<Boolean> mainHand = new Setting<>("MainHand", true);
    
    public static int ticks = 0;
    private final Setting<SettingGroup> selection = new Setting<>("Selection", new SettingGroup(true, 0));
    private final Setting<Boolean> food = new Setting<>("Food", true).addToGroup(selection);
    private final Setting<Boolean> projectiles = new Setting<>("Projectiles", true).addToGroup(selection);
    private final Setting<Boolean> shield = new Setting<>("Shield", true).addToGroup(selection);
    public final Setting<Boolean> soulSand = new Setting<>("SoulSand", true).addToGroup(selection);
    public final Setting<Boolean> honey = new Setting<>("Honey", true).addToGroup(selection);
    public final Setting<Boolean> slime = new Setting<>("Slime", true).addToGroup(selection);
    public final Setting<Boolean> ice = new Setting<>("Ice", true).addToGroup(selection);
    public final Setting<Boolean> sweetBerryBush = new Setting<>("SweetBerryBush", true).addToGroup(selection);
    public final Setting<Boolean> sneak = new Setting<>("Sneak", false).addToGroup(selection);
    public final Setting<Boolean> crawl = new Setting<>("Crawl", false).addToGroup(selection);

    @Override
    public void onUpdate() {
        // Счетчик тиков для HolyTime
        if (mode.getValue() == Mode.HolyTime && mc.player != null && !mc.player.isFallFlying()) {
            if (mc.player.isUsingItem()) {
                ticks++;
            } else {
                ticks = 0;
            }
        }

        if (mc.player.isUsingItem() && !mc.player.isRiding() && !mc.player.isFallFlying()) {
            if (mode.getValue() == Mode.Matrix) {
                if (mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                    mc.player.setVelocity(mc.player.getVelocity().x * 0.3, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.3);
                } else if (mc.player.fallDistance > 0.2f)
                    mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
            }
            // HolyTime обрабатывается через счетчик тиков в canNoSlow()
        }
    }


    public boolean canNoSlow() {
        // HolyTime: отменяем замедление только когда ticks >= 2
        if (mode.getValue() == Mode.HolyTime) {
            if (ticks >= 2) {
                ticks = 0;
                return true;
            }
            return false;
        }

        // Matrix: проверяем настройки выбора
        if (!food.getValue() && mc.player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD))
            return false;

        if (!shield.getValue() && mc.player.getActiveItem().getItem() == Items.SHIELD)
            return false;

        if (!projectiles.getValue()
                && (mc.player.getActiveItem().getItem() == Items.CROSSBOW || mc.player.getActiveItem().getItem() == Items.BOW || mc.player.getActiveItem().getItem() == Items.TRIDENT))
            return false;

        if (!mainHand.getValue() && mc.player.getActiveHand() == Hand.MAIN_HAND)
            return false;

        return true;
    }

    public enum Mode {
        Matrix, HolyTime
    }
}
