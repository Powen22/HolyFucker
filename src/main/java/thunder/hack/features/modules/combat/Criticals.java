package thunder.hack.features.modules.combat;

import io.netty.buffer.Unpooled;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.*;
import org.jetbrains.annotations.NotNull;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.injection.accesors.IClientPlayerEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public final class Criticals extends Module {
    public final Setting<Mode> mode = new Setting<>("Mode", Mode.UpdatedNCP);

    public static boolean cancelCrit;
    
    // Grim mode - delayed attack after jump
    private boolean grimWaitingForFall = false;
    private Entity grimPendingTarget = null;
    private int grimJumpTicks = 0;

    public Criticals() {
        super("Criticals", Category.COMBAT);
    }
    
    @Override
    public void onDisable() {
        grimWaitingForFall = false;
        grimPendingTarget = null;
        grimJumpTicks = 0;
    }
    
    @Override
    public void onUpdate() {
        if (!mode.is(Mode.Grim) || mc.player == null) return;
        
        // Grim mode: wait for player to start falling after jump, then attack
        if (grimWaitingForFall && grimPendingTarget != null) {
            grimJumpTicks++;
            
            // Check if player is now falling (velocity Y < 0) or at peak (velocity Y <= 0.1)
            boolean isFallingOrPeak = mc.player.getVelocity().y <= 0.1 && !mc.player.isOnGround();
            
            // Attack when falling or at peak, or timeout after 10 ticks
            if (isFallingOrPeak || grimJumpTicks > 10) {
                // Perform the attack
                if (grimPendingTarget.isAlive() && mc.player.canSee(grimPendingTarget)) {
                    mc.interactionManager.attackEntity(mc.player, grimPendingTarget);
                    mc.player.swingHand(mc.player.getActiveHand());
                }
                
                // Reset state
                grimWaitingForFall = false;
                grimPendingTarget = null;
                grimJumpTicks = 0;
            }
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.@NotNull Send event) {
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket && getInteractType(event.getPacket()) == InteractType.ATTACK) {
            Entity ent = getEntity(event.getPacket());
            if (ent == null || ent instanceof EndCrystalEntity || cancelCrit)
                return;
            
            // Grim mode: intercept attack and delay it
            if (mode.is(Mode.Grim) && mc.player != null) {
                // If already in air and falling - allow attack
                if (!mc.player.isOnGround() && mc.player.getVelocity().y <= 0.1) {
                    return; // Let the attack through
                }
                
                // If on ground - jump and delay attack
                if (mc.player.isOnGround() && !grimWaitingForFall) {
                    mc.player.jump();
                    grimWaitingForFall = true;
                    grimPendingTarget = ent;
                    grimJumpTicks = 0;
                    event.cancel(); // Cancel this attack, will attack later
                    return;
                }
                
                // If already waiting for fall - cancel duplicate attacks
                if (grimWaitingForFall) {
                    event.cancel();
                    return;
                }
            }
            
            doCrit();
        }
    }

    public void doCrit() {
        if (isDisabled() || mc.player == null || mc.world == null)
            return;
        if ((mc.player.isOnGround() || mc.player.getAbilities().flying) && !mc.player.isInLava() && !mc.player.isSubmergedInWater()) {
            switch (mode.getValue()) {
                case OldNCP -> {
                    critPacket(0.00001058293536, false);
                    critPacket(0.00000916580235, false);
                    critPacket(0.00000010371854, false);
                }
                case Ncp -> {
                    critPacket(0.0625D, false);
                    critPacket(0., false);
                }
                case UpdatedNCP -> {
                    critPacket(0.000000271875, false);
                    critPacket(0., false);
                }
                case Strict -> {
                    critPacket(0.062600301692775, false);
                    critPacket(0.07260029960661, false);
                    critPacket(0., false);
                    critPacket(0., false);
                }
                case Grim -> {
                    // Handled in onPacketSend - jump and delay attack
                }
            }
        }
    }

    private void critPacket(double yDelta, boolean full) {
        if (!full)
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + yDelta, mc.player.getZ(), false));
        else
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + yDelta, mc.player.getZ(), ((IClientPlayerEntity) mc.player).getLastYaw(), ((IClientPlayerEntity) mc.player).getLastPitch(), false));
    }

    public static Entity getEntity(@NotNull PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf packetBuf = new PacketByteBuf(Unpooled.buffer());
        packet.write(packetBuf);
        return mc.world.getEntityById(packetBuf.readVarInt());
    }

    public static InteractType getInteractType(@NotNull PlayerInteractEntityC2SPacket packet) {
        PacketByteBuf packetBuf = new PacketByteBuf(Unpooled.buffer());
        packet.write(packetBuf);
        packetBuf.readVarInt();
        return packetBuf.readEnumConstant(InteractType.class);
    }

    public enum InteractType {
        INTERACT, ATTACK, INTERACT_AT
    }

    public enum Mode {
        Ncp, Strict, OldNCP, UpdatedNCP, Grim
    }
}
