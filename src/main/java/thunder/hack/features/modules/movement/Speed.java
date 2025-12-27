package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import thunder.hack.ThunderHack;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.*;
import thunder.hack.injection.accesors.IInteractionManager;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.interfaces.IEntity;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.SearchInvResult;

import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.utility.player.MovementUtility.isMoving;

public class Speed extends Module {
    public Speed() {
        super("Speed", Category.MOVEMENT);
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);
    public Setting<Boolean> useTimer = new Setting<>("Use Timer", false);
    public Setting<Boolean> pauseInLiquids = new Setting<>("PauseInLiquids", false);
    public Setting<Boolean> pauseWhileSneaking = new Setting<>("PauseWhileSneaking", false);
    public final Setting<Integer> hurttime = new Setting<>("HurtTime", 0, 0, 10, v -> mode.is(Mode.MatrixDamage));
    public final Setting<Float> boostFactor = new Setting<>("BoostFactor", 2f, 0f, 10f, v -> mode.is(Mode.MatrixDamage) || mode.is(Mode.Vanilla));
    public final Setting<Boolean> allowOffGround = new Setting<>("AllowOffGround", true, v -> mode.is(Mode.MatrixDamage));
    public final Setting<Integer> shiftTicks = new Setting<>("ShiftTicks", 0, 0, 10, v -> mode.is(Mode.MatrixDamage));
    public final Setting<Integer> fireWorkSlot = new Setting<>("FireSlot", 1, 1, 9, v -> mode.getValue() == Mode.FireWork);
    public final Setting<Integer> delay = new Setting<>("Delay", 8, 1, 20, v -> mode.getValue() == Mode.FireWork);
    public final Setting<Boolean> strict = new Setting<>("Strict", false, v -> mode.is(Mode.GrimIce));
    public final Setting<Float> matrixJBSpeed = new Setting<>("TimerSpeed", 1.088f, 1f, 2f, v -> mode.is(Mode.MatrixJB));
    public final Setting<Boolean> armorStands = new Setting<>("ArmorStands", false, v -> mode.is(Mode.GrimCombo) || mode.is(Mode.GrimEntity2));

    public double baseSpeed;
    private int stage, ticks, prevSlot;
    private float prevForward = 0;
    private thunder.hack.utility.Timer elytraDelay = new thunder.hack.utility.Timer();
    private thunder.hack.utility.Timer startDelay = new thunder.hack.utility.Timer();

    public enum Mode {
        StrictStrafe, MatrixJB, NCP, ElytraLowHop, MatrixDamage, GrimEntity, GrimEntity2, FireWork, Vanilla, GrimIce, GrimCombo, GrimNew
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1f;
    }

    @Override
    public void onEnable() {
        stage = 1;
        ticks = 0;
        baseSpeed = 0.2873D;
        startDelay.reset();
        prevSlot = -1;
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mc.player.isInFluid() && pauseInLiquids.getValue() || mc.player.isSneaking() && pauseWhileSneaking.getValue()) {
            return;
        }

        if (mode.getValue() == Mode.MatrixJB) {
            boolean closeToGround = false;

            for (VoxelShape a : mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(0.5, 0.0, 0.5).offset(0.0, -1.0, 0.0)))
                if (a != VoxelShapes.empty()) {
                    closeToGround = true;
                    break;
                }

            if (MovementUtility.isMoving() && closeToGround && mc.player.fallDistance <= 0) {
                ThunderHack.TICK_TIMER = 1f;
                mc.player.setOnGround(true);
                mc.player.jump();
            } else if (mc.player.fallDistance > 0 && useTimer.getValue()) {
                ThunderHack.TICK_TIMER = matrixJBSpeed.getValue();
                mc.player.addVelocity(0f, -0.003f, 0f);
            }
        }
    }

    @EventHandler
    public void modifyVelocity(EventPlayerTravel e) {
        // New Grim bypass - uses friction modification instead of direct velocity changes
        if (mode.getValue() == Mode.GrimNew && !e.isPre() && MovementUtility.isMoving() && mc.player.isOnGround()) {
            // Grim checks movement predictions very carefully
            // We use very subtle friction modifications that look natural
            
            BlockPos pos = ((IEntity) mc.player).thunderHack_Recode$getVelocityBP();
            float slipperiness = mc.world.getBlockState(pos).getBlock().getSlipperiness();
            
            // Only apply on certain blocks to avoid patterns
            // Ice blocks naturally have different friction
            if (slipperiness > 0.98f) { // Ice, packed ice, blue ice, slime
                Vec3d velocity = mc.player.getVelocity();
                
                // Very subtle modification - only when moving fast enough
                if (Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) > 0.15) {
                    // Reduce friction slightly (makes player move faster)
                    // This is very natural on ice blocks
                    float modifiedFriction = slipperiness * 0.91f * 0.995f; // 0.5% reduction
                    float airFriction = slipperiness * 0.995f;
                    
                    double newX = velocity.x / 0.91f * modifiedFriction;
                    double newZ = velocity.z / 0.91f * modifiedFriction;
                    
                    // Only apply if change is very small (Grim tolerance)
                    if (Math.abs(newX - velocity.x) < 0.01 && Math.abs(newZ - velocity.z) < 0.01) {
                        mc.player.setVelocity(newX, velocity.y, newZ);
                    }
                }
            }
        }
        
        if (mode.getValue() == Mode.GrimEntity && !e.isPre() && ThunderHack.core.getSetBackTime() > 1000 && MovementUtility.isMoving()) {
            // Improved GrimEntity bypass - more subtle and natural entity collision simulation
            // Grim expands player bounding box by 0.2 and checks for collisions
            Box expandedBox = mc.player.getBoundingBox().expand(0.2);
            
            for (PlayerEntity ent : Managers.ASYNC.getAsyncPlayers()) {
                if (ent == mc.player || !ent.isAlive()) continue;
                
                // Check if entity bounding box actually intersects (real collision, not just proximity)
                Box entityBox = ent.getBoundingBox();
                if (!expandedBox.intersects(entityBox)) continue;
                
                // Only apply if we're very close (actual collision range)
                double distance = mc.player.squaredDistanceTo(ent);
                if (distance > 1.5) continue; // Only within ~1.22 blocks (more realistic collision)
                
                // Calculate subtle boost - much smaller to avoid detection
                double distanceFactor = 1.0 - (distance / 1.5); // 1.0 at 0 distance, 0.0 at 1.22 distance
                
                // Very small boost with randomization to avoid patterns
                double baseBoost = 0.04; // Reduced from 0.12 to 0.04
                double boost = distanceFactor * baseBoost * (0.85 + Math.random() * 0.3); // Randomize 85-115%
                
                // Get block slipperiness for accurate friction
                BlockPos pos = ((IEntity) mc.player).thunderHack_Recode$getVelocityBP();
                float slipperiness = mc.world.getBlockState(pos).getBlock().getSlipperiness();
                
                // Apply very subtle velocity modification
                Vec3d velocity = mc.player.getVelocity();
                
                // Only modify if we're moving (Grim checks for unnatural velocity changes)
                if (Math.abs(velocity.x) > 0.001 || Math.abs(velocity.z) > 0.001) {
                    // Calculate direction to entity (normalized)
                    Vec3d toEntity = ent.getPos().subtract(mc.player.getPos());
                    double horizontalDist = Math.sqrt(toEntity.x * toEntity.x + toEntity.z * toEntity.z);
                    if (horizontalDist > 0.001) {
                        Vec3d direction = new Vec3d(toEntity.x / horizontalDist, 0, toEntity.z / horizontalDist);
                        
                        // Apply very subtle boost in movement direction (not just towards entity)
                        // This makes it look more like natural entity pushing
                        Vec3d moveDir = new Vec3d(velocity.x, 0, velocity.z).normalize();
                        double dot = direction.x * moveDir.x + direction.z * moveDir.z;
                        
                        // Only boost if moving towards entity (more natural)
                        if (dot > 0.3) { // At least 30% aligned
                            double finalBoost = boost * Math.min(dot, 1.0);
                            double newX = velocity.x + direction.x * finalBoost * 0.3; // Very subtle
                            double newZ = velocity.z + direction.z * finalBoost * 0.3;
                            
                            mc.player.setVelocity(newX, velocity.y, newZ);
                        }
                    }
                }
                
                break; // Only apply once per tick
            }
        }

        if ((mode.is(Mode.GrimEntity2) || mode.is(Mode.GrimCombo)) && !e.isPre() && ThunderHack.core.getSetBackTime() > 1000 && MovementUtility.isMoving()) {
            // Improved GrimEntity2 - more subtle collision-based boost
            Box expandedBox = mc.player.getBoundingBox().expand(0.2); // Grim's expansion
            int collisions = 0;
            
            for (Entity ent : mc.world.getEntities()) {
                if (ent == mc.player || !ent.isAlive()) continue;
                
                // Only count pushable entities (LivingEntity, BoatEntity)
                // ArmorStands are optional based on setting
                if ((ent instanceof LivingEntity || ent instanceof BoatEntity) && 
                    (!(ent instanceof ArmorStandEntity) || armorStands.getValue())) {
                    
                    // Check intersection with expanded box (Grim's method)
                    if (expandedBox.intersects(ent.getBoundingBox())) {
                        // Only count if actually close (real collision)
                        double distance = mc.player.squaredDistanceTo(ent);
                        if (distance <= 1.5) { // Within ~1.22 blocks
                    collisions++;
                        }
                    }
                }
            }
            
            // Apply very subtle motion boost based on collisions
            // Reduced significantly to avoid detection
            if (collisions > 0) {
                // Cap collisions to avoid too much boost
                int cappedCollisions = Math.min(collisions, 3);
                // Much smaller boost per collision with randomization
                double boostPerCollision = 0.03 * (0.9 + Math.random() * 0.2); // 0.027-0.036 per collision
                double[] motion = MovementUtility.forward(boostPerCollision * cappedCollisions);
            mc.player.addVelocity(motion[0], 0.0, motion[1]);
            }
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        //first author: Delyfss
        if ((mode.is(Mode.GrimIce) || mode.is(Mode.GrimCombo)) && mc.player.isOnGround()) {
            BlockPos pos = ((IEntity) mc.player).thunderHack_Recode$getVelocityBP();
            SearchInvResult result = InventoryUtility.findBlockInHotBar(Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE);
            if (mc.world.isAir(pos) || !result.found() || !mc.options.jumpKey.isPressed())
                return;

            prevSlot = mc.player.getInventory().selectedSlot;
            result.switchTo();
            sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), 90, mc.player.isOnGround()));

            if (strict.getValue()) {
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP));
            }

            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
            sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(pos.down().toCenterPos().add(0, 0.5, 0), Direction.UP, pos.down(), false), id));
            mc.world.setBlockState(pos, Blocks.ICE.getDefaultState());
        }
    }

    @EventHandler
    public void onPostTick(EventPostTick e) {
        if ((mode.is(Mode.GrimIce) || mode.is(Mode.GrimCombo)) && prevSlot != -1) {
            mc.player.getInventory().selectedSlot = prevSlot;
            ((IInteractionManager) mc.interactionManager).syncSlot();
            prevSlot = -1;
        }
    }

    @Override
    public void onUpdate() {
        if (mc.player.isInFluid() && pauseInLiquids.getValue() || mc.player.isSneaking() && pauseWhileSneaking.getValue()) {
            return;
        }

        if (mode.getValue() == Mode.FireWork) {
            ticks--;
            int ellySlot = InventoryUtility.getElytra();
            int fireSlot = InventoryUtility.findItemInHotBar(Items.FIREWORK_ROCKET).slot();
            boolean inOffHand = mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET;
            if (fireSlot == -1) {
                int fireInInv = InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET).slot();
                if (fireInInv != -1)
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fireInInv, fireWorkSlot.getValue() - 1, SlotActionType.SWAP, mc.player);
            }

            if (ellySlot != -1 && (fireSlot != -1 || inOffHand) && !mc.player.isOnGround() && mc.player.fallDistance > 0) {
                if (ticks <= 0) {
                    if (ellySlot != -2) {
                        mc.interactionManager.clickSlot(0, ellySlot, 1, SlotActionType.PICKUP, mc.player);
                        mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
                    }
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    if (prevSlot != fireSlot && !inOffHand)
                        sendPacket(new UpdateSelectedSlotC2SPacket(fireSlot));
                    mc.interactionManager.interactItem(mc.player, inOffHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                    if (prevSlot != fireSlot && !inOffHand)
                        sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));

                    if (ellySlot != -2) {
                        mc.interactionManager.clickSlot(0, 6, 1, SlotActionType.PICKUP, mc.player);
                        mc.interactionManager.clickSlot(0, ellySlot, 1, SlotActionType.PICKUP, mc.player);
                    }
                    mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    ticks = delay.getValue();
                }
            }
        }

        if (mode.getValue() == Mode.ElytraLowHop) {
            if (mc.player.isOnGround()) {
                mc.player.jump();
                return;
            }
            if (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.29, 0, -0.29).offset(0.0, -3, 0.0f)).iterator().hasNext() && elytraDelay.passedMs(150) && startDelay.passedMs(500)) {
                int elytra = InventoryUtility.getElytra();
                if (elytra == -1) disable(isRu() ? "Для этого режима нужна элитра!" : "You need elytra for this mode!");
                else sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

                mc.player.setVelocity(mc.player.getVelocity().getX(), 0f, mc.player.getVelocity().getZ());
                if (isMoving())
                    MovementUtility.setMotion(0.85);
                elytraDelay.reset();
            }
        }
    }

    @EventHandler
    public void onPostPlayerUpdate(PostPlayerUpdateEvent event) {
        if (mode.getValue() == Mode.MatrixDamage) {
            if (MovementUtility.isMoving() && mc.player.hurtTime > hurttime.getValue()) {
                if (mc.player.isOnGround()) {
                    MovementUtility.setMotion(0.387f * boostFactor.getValue());
                } else if (mc.player.isTouchingWater()) {
                    MovementUtility.setMotion(0.346f * boostFactor.getValue());
                } else if (!mc.player.isOnGround() && allowOffGround.getValue()) {
                    MovementUtility.setMotion(0.448f * boostFactor.getValue());
                }

                if (shiftTicks.getValue() > 0) {
                    event.cancel();
                    event.setIterations(shiftTicks.getValue());
                }
            }
        }
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (mc.player.isInFluid() && pauseInLiquids.getValue() || mc.player.isSneaking() && pauseWhileSneaking.getValue()) {
            return;
        }
        
        // New Grim bypass - very subtle speed modification through friction
        if (mode.getValue() == Mode.GrimNew && MovementUtility.isMoving() && mc.player.isOnGround()) {
            // Grim uses prediction engine, so we need extremely subtle changes
            // Use friction modification instead of direct speed changes
            
            BlockPos pos = ((IEntity) mc.player).thunderHack_Recode$getVelocityBP();
            float slipperiness = mc.world.getBlockState(pos).getBlock().getSlipperiness();
            
            Vec3d velocity = mc.player.getVelocity();
            double currentSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            
            // Only apply on certain conditions to avoid patterns
            // Apply very small friction reduction (looks natural)
            if (currentSpeed > 0.1 && currentSpeed < 0.5) {
                // Very subtle modification - 0.3-0.5% friction reduction
                float frictionReduction = 0.996f + (float)(Math.random() * 0.002); // 0.996-0.998
                
                float groundFriction = slipperiness * 0.91f * frictionReduction;
                float airFriction = slipperiness * frictionReduction;
                
                double newX = velocity.x / 0.91f * groundFriction;
                double newZ = velocity.z / 0.91f * groundFriction;
                
                // Only apply if change is within Grim's tolerance (< 0.01)
                if (Math.abs(newX - velocity.x) < 0.008 && Math.abs(newZ - velocity.z) < 0.008) {
                    mc.player.setVelocity(newX, velocity.y, newZ);
                }
            }
            
            return;
        }
        
        if (mode.getValue() != Mode.NCP && mode.getValue() != Mode.StrictStrafe) return;
        if (mc.player.getAbilities().flying) return;
        if (mc.player.isFallFlying()) return;
        if (mc.player.getHungerManager().getFoodLevel() <= 6) return;
        if (event.isCancelled()) return;
        event.cancel();

        if (MovementUtility.isMoving()) {
            ThunderHack.TICK_TIMER = useTimer.getValue() ? 1.088f : 1f;
            float currentSpeed = mode.getValue() == Mode.NCP && mc.player.input.movementForward <= 0 && prevForward > 0 ? Managers.PLAYER.currentPlayerSpeed * 0.66f : Managers.PLAYER.currentPlayerSpeed;
            boolean canJump = !mc.player.horizontalCollision;

            if (stage == 1 && mc.player.isOnGround() && canJump) {
                mc.player.setVelocity(mc.player.getVelocity().x, MovementUtility.getJumpSpeed(), mc.player.getVelocity().z);
                event.setY(MovementUtility.getJumpSpeed());
                baseSpeed *= 2.149;
                stage = 2;
            } else if (stage == 2) {
                baseSpeed = currentSpeed - (0.66 * (currentSpeed - MovementUtility.getBaseMoveSpeed()));
                stage = 3;
            } else {
                if ((mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, mc.player.getVelocity().getY(), 0.0)).iterator().hasNext() || mc.player.verticalCollision))
                    stage = 1;
                baseSpeed = currentSpeed - currentSpeed / 159.0D;
            }

            baseSpeed = Math.max(baseSpeed, MovementUtility.getBaseMoveSpeed());

            double ncpSpeed = mode.getValue() == Mode.StrictStrafe || mc.player.input.movementForward < 1 ? 0.465 : 0.576;
            double ncpBypassSpeed = mode.getValue() == Mode.StrictStrafe || mc.player.input.movementForward < 1 ? 0.44 : 0.57;

            if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
                double amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
                ncpSpeed *= 1 + (0.2 * (amplifier + 1));
                ncpBypassSpeed *= 1 + (0.2 * (amplifier + 1));
            }

            if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
                double amplifier = mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier();
                ncpSpeed /= 1 + (0.2 * (amplifier + 1));
                ncpBypassSpeed /= 1 + (0.2 * (amplifier + 1));
            }

            baseSpeed = Math.min(baseSpeed, ticks > 25 ? ncpSpeed : ncpBypassSpeed);

            if (ticks++ > 50)
                ticks = 0;

            MovementUtility.modifyEventSpeed(event, baseSpeed);
            prevForward = mc.player.input.movementForward;
        } else {
            ThunderHack.TICK_TIMER = 1f;
            event.setX(0);
            event.setZ(0);
        }
    }
}
