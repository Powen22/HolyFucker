package thunder.hack.features.modules.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import thunder.hack.events.impl.EventEntitySpawn;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.*;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Trajectories extends Module {
    public Trajectories() {
        super("Trajectories", Category.RENDER);
    }

    private final Setting<Mode> mode = new Setting<>("ColorMode", Mode.Sync);
    private final Setting<ColorSetting> color = new Setting<>("Color", new ColorSetting(0x2250b4b4), v -> mode.getValue() == Mode.Custom);
    private final Setting<Mode> lmode = new Setting<>("LandedColorMode", Mode.Sync);
    private final Setting<ColorSetting> lcolor = new Setting<>("LandedColor", new ColorSetting(0x2250b4b4), v -> lmode.getValue() == Mode.Custom);
    private final Setting<Boolean> potionRadius = new Setting<>("PotionRadius", true);
    private final Setting<ColorSetting> radiusColor = new Setting<>("RadiusColor", new ColorSetting(0x5000FF00), v -> potionRadius.getValue());
    
    // Pearl tracking settings
    private final Setting<BooleanSettingGroup> pearlTracker = new Setting<>("PearlTracker", new BooleanSettingGroup(true));
    private final Setting<Boolean> showOwner = new Setting<>("ShowOwner", true).addToGroup(pearlTracker);
    private final Setting<Boolean> showFlightTime = new Setting<>("ShowFlightTime", true).addToGroup(pearlTracker);
    private final Setting<ColorSetting> pearlColor = new Setting<>("PearlColor", new ColorSetting(0xFF00FFFF)).addToGroup(pearlTracker);
    private final Setting<ColorSetting> pearlLandColor = new Setting<>("PearlLandColor", new ColorSetting(0xFFFF00FF)).addToGroup(pearlTracker);

    // Store pearl owners
    private final Map<Integer, PearlInfo> pearlOwners = new ConcurrentHashMap<>();

    private static class PearlInfo {
        String ownerName;
        long spawnTime;
        
        PearlInfo(String ownerName, long spawnTime) {
            this.ownerName = ownerName;
            this.spawnTime = spawnTime;
        }
    }

    @EventHandler
    public void onEntitySpawn(EventEntitySpawn e) {
        if (!pearlTracker.getValue().isEnabled()) return;
        
        if (e.getEntity() instanceof EnderPearlEntity pearl) {
            // Find closest player as owner
            mc.world.getPlayers().stream()
                    .min(Comparator.comparingDouble(p -> p.squaredDistanceTo(pearl.getPos())))
                    .ifPresent(player -> {
                        pearlOwners.put(pearl.getId(), new PearlInfo(player.getName().getString(), System.currentTimeMillis()));
                    });
        }
    }

    @Override
    public void onDisable() {
        pearlOwners.clear();
    }

    private boolean isThrowable(Item item) {
        return item instanceof EnderPearlItem || item instanceof TridentItem || item instanceof ExperienceBottleItem || item instanceof SnowballItem || item instanceof EggItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
    }

    private boolean isPotion(Item item) {
        return item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
    }

    private float getPotionRadius(Item item) {
        if (item instanceof LingeringPotionItem) return 3.0f;
        if (item instanceof SplashPotionItem) return 4.0f;
        return 0f;
    }

    private float getDistance(Item item) {
        return item instanceof BowItem ? 1.0f : 0.4f;
    }

    private float getThrowVelocity(Item item) {
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) return 0.5f;
        if (item instanceof ExperienceBottleItem) return 0.7f;
        if (item instanceof TridentItem) return 2.5f;
        return 1.5f;
    }

    private int getThrowPitch(Item item) {
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem || item instanceof ExperienceBottleItem)
            return 20;
        return 0;
    }

    private float getGravity(Item item) {
        if (item instanceof BowItem || item instanceof CrossbowItem) return 0.05f;
        if (item instanceof TridentItem) return 0.05f;
        if (item instanceof ExperienceBottleItem) return 0.07f;
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) return 0.05f;
        return 0.03f;
    }

    private float getDrag(Item item) {
        return 0.99f;
    }

    @Override
    public void onRender3D(MatrixStack stack) {
        if (mc.options.hudHidden) return;
        if (mc.player == null || mc.world == null) return;

        // Track thrown pearls
        if (pearlTracker.getValue().isEnabled()) {
            renderThrownPearls(stack);
        }

        // Original trajectory for held items
        if (!mc.options.getPerspective().isFirstPerson()) return;
        
        Hand hand;
        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();

        if (mainHand.getItem() instanceof BowItem || mainHand.getItem() instanceof CrossbowItem || isThrowable(mainHand.getItem())) {
            hand = Hand.MAIN_HAND;
        } else if (offHand.getItem() instanceof BowItem || offHand.getItem() instanceof CrossbowItem || isThrowable(offHand.getItem())) {
            hand = Hand.OFF_HAND;
        } else return;

        boolean prev_bob = mc.options.getBobView().getValue();
        mc.options.getBobView().setValue(false);

        ItemStack itemStack = hand == Hand.OFF_HAND ? offHand : mainHand;
        Item item = itemStack.getItem();

        if ((offHand.getItem() instanceof CrossbowItem && EnchantmentHelper.getLevel(mc.world.getRegistryManager().get(Enchantments.MULTISHOT.getRegistryRef()).getEntry(Enchantments.MULTISHOT).get(), offHand) != 0) ||
                (mainHand.getItem() instanceof CrossbowItem && EnchantmentHelper.getLevel(mc.world.getRegistryManager().get(Enchantments.MULTISHOT.getRegistryRef()).getEntry(Enchantments.MULTISHOT).get(), mainHand) != 0)) {

            calcTrajectory(stack, item, mc.player.getYaw() - 10, hand);
            calcTrajectory(stack, item, mc.player.getYaw(), hand);
            calcTrajectory(stack, item, mc.player.getYaw() + 10, hand);

        } else calcTrajectory(stack, item, mc.player.getYaw(), hand);
        mc.options.getBobView().setValue(prev_bob);
    }

    private void renderThrownPearls(MatrixStack stack) {
        // Clean up old pearl entries
        pearlOwners.entrySet().removeIf(entry -> {
            Entity pearl = mc.world.getEntityById(entry.getKey());
            return pearl == null || !pearl.isAlive();
        });

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EnderPearlEntity pearl)) continue;
            
            PearlInfo info = pearlOwners.get(pearl.getId());
            String ownerName = info != null ? info.ownerName : "Unknown";
            long spawnTime = info != null ? info.spawnTime : System.currentTimeMillis();
            
            // Calculate trajectory
            Vec3d velocity = pearl.getVelocity();
            double x = pearl.getX();
            double y = pearl.getY();
            double z = pearl.getZ();
            double mx = velocity.x;
            double my = velocity.y;
            double mz = velocity.z;

            Vec3d landingPos = null;
            int ticksToLand = 0;

            Color trailColor = pearlColor.getValue().getColorObject();
            Color landColor = pearlLandColor.getValue().getColorObject();

            Vec3d lastPos;
            for (int i = 0; i < 300; i++) {
                lastPos = new Vec3d(x, y, z);
                x += mx;
                y += my;
                z += mz;

                // Water drag
                BlockPos blockPos = BlockPos.ofFloored(x, y, z);
                if (mc.world.getBlockState(blockPos).getBlock() == Blocks.WATER) {
                    mx *= 0.8;
                    my *= 0.8;
                    mz *= 0.8;
                } else {
                    mx *= 0.99;
                    my *= 0.99;
                    mz *= 0.99;
                }

                // Gravity for pearl
                my -= 0.03;

                Vec3d pos = new Vec3d(x, y, z);

                // Check block collision
                BlockHitResult bhr = mc.world.raycast(new RaycastContext(lastPos, pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, pearl));
                if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                    landingPos = bhr.getPos();
                    ticksToLand = i;

                    // Draw landing marker
                    Render3DEngine.OUTLINE_SIDE_QUEUE.add(new Render3DEngine.OutlineSideAction(
                            new Box(bhr.getBlockPos()), landColor, 2f, bhr.getSide()
                    ));
                    Render3DEngine.FILLED_SIDE_QUEUE.add(new Render3DEngine.FillSideAction(
                            new Box(bhr.getBlockPos()), Render2DEngine.injectAlpha(landColor, 100), bhr.getSide()
                    ));
                    break;
                }

                // Check entity collision (not self, not other pearls)
                for (Entity ent : mc.world.getEntities()) {
                    if (ent instanceof ArrowEntity || ent instanceof EnderPearlEntity) continue;
                    if (ent.getBoundingBox().expand(0.3).contains(pos)) {
                        landingPos = pos;
                        ticksToLand = i;

                        Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(
                                ent.getBoundingBox(), landColor, 2f));
                        Render3DEngine.FILLED_QUEUE.add(new Render3DEngine.FillAction(
                                ent.getBoundingBox(), Render2DEngine.injectAlpha(landColor, 100)));
                        break;
                    }
                }

                if (landingPos != null) break;
                if (y <= mc.world.getBottomY() - 64) break;
                if (mx == 0 && my == 0 && mz == 0) continue;

                // Draw trajectory line
                Color lineColor = mode.getValue() == Mode.Sync ? HudEditor.getColor(i * 3) : trailColor;
                Render3DEngine.drawLine(lastPos, pos, lineColor);
            }

        }
    }

    private void calcTrajectory(MatrixStack stack, Item item, float yaw, Hand hand) {
        double x = Render2DEngine.interpolate(mc.player.prevX, mc.player.getX(), Render3DEngine.getTickDelta());
        double y = Render2DEngine.interpolate(mc.player.prevY, mc.player.getY(), Render3DEngine.getTickDelta());
        double z = Render2DEngine.interpolate(mc.player.prevZ, mc.player.getZ(), Render3DEngine.getTickDelta());

        y = y + mc.player.getEyeHeight(mc.player.getPose()) - 0.1000000014901161;

        if (hand == Hand.MAIN_HAND) {
            x = x - MathHelper.cos(yaw / 180.0f * (float) Math.PI) * 0.16f;
            z = z - MathHelper.sin(yaw / 180.0f * (float) Math.PI) * 0.16f;
        } else {
            x = x + MathHelper.cos(yaw / 180.0f * (float) Math.PI) * 0.16f;
            z = z + MathHelper.sin(yaw / 180.0f * (float) Math.PI) * 0.16f;
        }

        float pitch = mc.player.getPitch();
        final float maxDist = getDistance(item);
        
        double motionX = -MathHelper.sin(yaw / 180.0f * (float) Math.PI) * MathHelper.cos(pitch / 180.0f * (float) Math.PI) * maxDist;
        double motionY = -MathHelper.sin((pitch - getThrowPitch(item)) / 180.0f * (float) Math.PI) * maxDist;
        double motionZ = MathHelper.cos(yaw / 180.0f * (float) Math.PI) * MathHelper.cos(pitch / 180.0f * (float) Math.PI) * maxDist;

        float power = 1.0f;
        if (item instanceof BowItem && mc.player.isUsingItem()) {
            int useTime = mc.player.getItemUseTimeLeft();
            int maxUseTime = mc.player.getActiveItem().getMaxUseTime(mc.player);
            int chargeTime = maxUseTime - useTime;
            power = chargeTime / 20.0f;
        power = (power * power + power * 2.0f) / 3.0f;
            if (power > 1.0f) power = 1.0f;
            if (power < 0.1f) power = 0.1f;
        }

        final float distance = MathHelper.sqrt((float) (motionX * motionX + motionY * motionY + motionZ * motionZ));
        if (distance > 0) {
        motionX /= distance;
        motionY /= distance;
        motionZ /= distance;
        }

        float velocityMultiplier;
        if (item instanceof BowItem) {
            velocityMultiplier = power * 3.0f;
        } else if (item instanceof CrossbowItem) {
            velocityMultiplier = 3.15f;
        } else {
            velocityMultiplier = getThrowVelocity(item);
        }

        motionX *= velocityMultiplier;
        motionY *= velocityMultiplier;
        motionZ *= velocityMultiplier;
        
        if (!mc.player.isOnGround()) {
            motionY += mc.player.getVelocity().getY();
        }

        float gravity = getGravity(item);
        float drag = getDrag(item);

        Vec3d lastPos;
        Vec3d landingPos = null;
        boolean hitEntity = false;
        int hitIteration = 0;
        
        for (int i = 0; i < 300; i++) {
            lastPos = new Vec3d(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;
            
            BlockPos blockPos = BlockPos.ofFloored(x, y, z);
            if (mc.world.getBlockState(blockPos).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= drag;
                motionY *= drag;
                motionZ *= drag;
            }

            motionY -= gravity;

            Vec3d pos = new Vec3d(x, y, z);

            for (Entity ent : mc.world.getEntities()) {
                if (ent instanceof ArrowEntity || ent.equals(mc.player)) continue;
                if (ent.getBoundingBox().expand(0.3).contains(pos)) {
                    Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(
                            ent.getBoundingBox(),
                            lmode.getValue() == Mode.Sync ? HudEditor.getColor(i * 10) : lcolor.getValue().getColorObject(),
                            2f));
                    Render3DEngine.FILLED_QUEUE.add(new Render3DEngine.FillAction(
                            ent.getBoundingBox(), lmode.getValue() == Mode.Sync ? Render2DEngine.injectAlpha(HudEditor.getColor(i * 10), 100) : lcolor.getValue().getColorObject()
                    ));
                    hitEntity = true;
                    landingPos = pos;
                    hitIteration = i;
                    break;
                }
            }

            if (hitEntity) break;

            BlockHitResult bhr = mc.world.raycast(new RaycastContext(lastPos, pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                landingPos = bhr.getPos();
                hitIteration = i;
                
                Color landColor = lmode.getValue() == Mode.Sync ? HudEditor.getColor(i * 10) : lcolor.getValue().getColorObject();
                
                Render3DEngine.OUTLINE_SIDE_QUEUE.add(new Render3DEngine.OutlineSideAction(
                        new Box(bhr.getBlockPos()), landColor, 2f, bhr.getSide()
                ));
                Render3DEngine.FILLED_SIDE_QUEUE.add(new Render3DEngine.FillSideAction(
                        new Box(bhr.getBlockPos()), Render2DEngine.injectAlpha(landColor, 100), bhr.getSide()
                ));

                if (potionRadius.getValue() && isPotion(item)) {
                    float radius = getPotionRadius(item);
                    drawPotionRadius(stack, landingPos, radius, i);
                }

                break;
            }

            if (y <= mc.world.getBottomY() - 64) break;
            if (motionX == 0 && motionY == 0 && motionZ == 0) continue;

            Render3DEngine.drawLine(lastPos, pos, mode.getValue() == Mode.Sync ? HudEditor.getColor(i) : color.getValue().getColorObject());
        }

        if (hitEntity && potionRadius.getValue() && isPotion(item) && landingPos != null) {
            float radius = getPotionRadius(item);
            drawPotionRadius(stack, landingPos, radius, hitIteration);
        }
    }

    private void drawPotionRadius(MatrixStack stack, Vec3d center, float radius, int colorOffset) {
        if (center == null || mc.world == null) return;

        double x = center.x - mc.getEntityRenderDispatcher().camera.getPos().getX();
        double y = center.y - mc.getEntityRenderDispatcher().camera.getPos().getY();
        double z = center.z - mc.getEntityRenderDispatcher().camera.getPos().getZ();

        stack.push();
        stack.translate(x, y, z);

        Render3DEngine.setupRender();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f matrix = stack.peek().getPositionMatrix();
        int segments = 64;

        Color baseColor = radiusColor.getValue().getColorObject();
        Color fillColor = Render2DEngine.injectAlpha(baseColor, 50);
        
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, 0, 0.02f, 0).color(fillColor.getRGB());
        
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * Math.PI * 2 / segments);
            float px = (float) (radius * Math.cos(angle));
            float pz = (float) (radius * Math.sin(angle));
            Color edgeColor = mode.getValue() == Mode.Sync ? 
                    Render2DEngine.injectAlpha(HudEditor.getColor(colorOffset * 10 + i * 4), 35) : 
                    Render2DEngine.injectAlpha(baseColor, 25);
            buffer.vertex(matrix, px, 0.02f, pz).color(edgeColor.getRGB());
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * Math.PI * 2 / segments);
            float px = (float) (radius * Math.cos(angle));
            float pz = (float) (radius * Math.sin(angle));
            Color lineColor = mode.getValue() == Mode.Sync ? 
                    HudEditor.getColor(colorOffset * 10 + i * 4) : 
                    baseColor;
            lineBuffer.vertex(matrix, px, 0.02f, pz).color(lineColor.getRGB());
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        Render3DEngine.endRender();
        
        stack.pop();
    }

    private enum Mode {
        Custom,
        Sync
    }
}
