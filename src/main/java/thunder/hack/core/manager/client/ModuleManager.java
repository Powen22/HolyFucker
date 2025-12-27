package thunder.hack.core.manager.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import thunder.hack.ThunderHack;
import thunder.hack.core.manager.IManager;
import thunder.hack.gui.clickui.ClickGUI;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.features.hud.HudElement;
import thunder.hack.features.hud.impl.*;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.*;
import thunder.hack.features.modules.combat.*;
import thunder.hack.features.modules.misc.*;
import thunder.hack.features.modules.movement.*;
import thunder.hack.features.modules.player.*;
import thunder.hack.features.modules.render.Particles;
import thunder.hack.features.modules.render.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class ModuleManager implements IManager {
    public ArrayList<Module> modules = new ArrayList<>();
    public List<Module> sortedModules = new ArrayList<>();
    public List<Integer> activeMouseKeys = new ArrayList<>();

    public static AntiPacketException antiPacketException = new AntiPacketException();
    public static LevitationControl levitationControl = new LevitationControl();
    public static NoCommentExploit noCommentExploit = new NoCommentExploit();
    public static BaritoneSettings baritoneSettings = new BaritoneSettings();
    public static PortalInventory portalInventory = new PortalInventory();
    public static TotemPopCounter totemPopCounter = new TotemPopCounter();
    public static DurabilityAlert durabilityAlert = new DurabilityAlert();
    public static CrosshairArrows crosshairArrows = new CrosshairArrows();
    public static PearlBlockThrow pearlBlockThrow = new PearlBlockThrow();
    public static AutoCrystalInfo autoCrystalInfo = new AutoCrystalInfo();
    public static ChatTranslator chatTranslator = new ChatTranslator();
    public static PacketCanceler packetCanceler = new PacketCanceler();
    public static ClientSettings clientSettings = new ClientSettings();
    public static ThunderHackGui thunderHackGui = new ThunderHackGui();
    public static NoServerRotate noServerRotate = new NoServerRotate();
    public static BreakHighLight breakHighLight = new BreakHighLight();
    public static BlockHighLight blockHighLight = new BlockHighLight();
    public static MouseElytraFix mouseElytraFix = new MouseElytraFix();
    public static TotemAnimation totemAnimation = new TotemAnimation();
    public static OptifineCapes optifineCapes = new OptifineCapes();
    public static Notifications notifications = new Notifications();
    public static NoEntityTrace noEntityTrace = new NoEntityTrace();
    public static MessageAppend messageAppend = new MessageAppend();
    public static EntityControl entityControl = new EntityControl();
    public static ElytraReplace elytraReplace = new ElytraReplace();
    public static AntiServerAdd antiServerAdd = new AntiServerAdd();
    public static AntiBallPlace antiBallPlace = new AntiBallPlace();
    public static Trajectories trajectories = new Trajectories();
    public static TargetStrafe targetStrafe = new TargetStrafe();
    public static RadarRewrite radarRewrite = new RadarRewrite();
    public static PVPResources pvpResources = new PVPResources();
    public static NoServerSlot noServerSlot = new NoServerSlot();
    public static NoCameraClip noCameraClip = new NoCameraClip();
    public static ItemScroller itemScroller = new ItemScroller();
    public static ItemUseTracker itemUseTracker = new ItemUseTracker();
    public static HitParticles hitParticles = new HitParticles();
    public static ElytraRecast elytraRecast = new ElytraRecast();
    public static ChestStealer chestStealer = new ChestStealer();
    public static AutoTpAccept autoTpAccept = new AutoTpAccept();
    public static AntiServerRP antiServerRP = new AntiServerRP();
    public static TotemCounter totemCounter = new TotemCounter();
    public static PerfectDelay perfectDelay = new PerfectDelay();
    public static ServerHelper serverHelper = new ServerHelper();
    public static ChestCounter chestCounter = new ChestCounter();
    public static StashLogger stashLogger = new StashLogger();
    public static FastLatency fastLatency = new FastLatency();
    public static PearlChaser pearlChaser = new PearlChaser();
    public static WorldTweaks worldTweaks = new WorldTweaks();
    public static Speedometer speedometer = new Speedometer();
    public static NoJumpDelay noJumpDelay = new NoJumpDelay();
    public static NameProtect nameProtect = new NameProtect();
    public static MiddleClick middleClick = new MiddleClick();
    public static LagNotifier lagNotifier = new LagNotifier();
    public static BreadCrumbs breadCrumbs = new BreadCrumbs();
    public static AutoRespawn autoRespawn = new AutoRespawn();
    public static AspectRatio aspectRatio = new AspectRatio();
    public static ClientSpoof clientSpoof = new ClientSpoof();
    public static WaterSpeed waterSpeed = new WaterSpeed();
    public static TPSCounter tpsCounter = new TPSCounter();
    public static StorageEsp storageEsp = new StorageEsp();
    public static StaffBoard staffBoard = new StaffBoard();
    public static PistonAura pistonAura = new PistonAura();
    public static NoInteract noInteract = new NoInteract();
    public static ModuleList moduleList = new ModuleList();
    public static JumpCircle jumpCircle = new JumpCircle();
    public static Fullbright fullbright = new Fullbright();
    public static FpsCounter fpsCounter = new FpsCounter();
    public static FakePlayer fakePlayer = new FakePlayer();
    public static ElytraSwap elytraSwap = new ElytraSwap();
    public static AutoSprint autoSprint = new AutoSprint();
    public static AutoGApple autoGApple = new AutoGApple();
    public static Animations animations = new Animations();
    public static DamageTint damageTint = new DamageTint();
    public static AntiAttack antiAttack = new AntiAttack();
    public static GapplesHud gapplesHud = new GapplesHud();
    public static HitBubbles hitBubbles = new HitBubbles();
    public static AutoTrader autoTrader = new AutoTrader();
    public static KillStats killStats = new KillStats();
    public static AutoAnvil autoAnvil = new AutoAnvil();
    public static CandleHud candleHud = new CandleHud();
    public static Particles particles = new Particles();
    public static ToolSaver toolSaver = new ToolSaver();
    public static WayPoints wayPoints = new WayPoints();
    public static WaterMark waterMark = new WaterMark();
    public static ViewModel viewModel = new ViewModel();
    public static TargetHud targetHud = new TargetHud();
    public static SpeedMine speedMine = new SpeedMine();
    public static PotionHud potionHud = new PotionHud();
    public static MultiTask multitask = new MultiTask();
    public static LegacyHud legacyHud = new LegacyHud();
    public static HudEditor hudEditor = new HudEditor();
    public static Crosshair crosshair = new Crosshair();
    public static Criticals criticals = new Criticals();
    public static AutoTotem autoTotem = new AutoTotem();
    public static AutoArmor autoArmor = new AutoArmor();
    public static Cooldowns cooldowns = new Cooldowns();
    public static Rotations rotations = new Rotations();
    public static MemoryHud memoryHud = new MemoryHud();
    public static Companion companion = new Companion();
    public static AntiCrash antiCrash = new AntiCrash();
    public static Velocity velocity = new Velocity();
    public static PopChams popChams = new PopChams();
    public static NoRender noRender = new NoRender();
    public static NameTags nameTags = new NameTags();
    public static LongJump longJump = new LongJump();
    public static KeyBinds keyBinds = new KeyBinds();
    public static ExtraTab extraTab = new ExtraTab();
    public static ClickGui clickGui = new ClickGui();
    public static AutoTool autoTool = new AutoTool();
    public static AutoFish autoFish = new AutoFish();
    public static AutoBuff autoBuff = new AutoBuff();
    public static ArmorHud armorHud = new ArmorHud();
    public static KillFeed killFeed = new KillFeed();
    public static BlockESP blockESP = new BlockESP();
    public static Windows windows = new Windows();
    public static AutoEat autoEat = new AutoEat();
    public static AntiAFK antiAFK = new AntiAFK();
    public static SoundFX soundFX = new SoundFX();
    public static Tracker tracker = new Tracker();
    public static TpsSync tpsSync = new TpsSync();
    public static PingHud pingHud = new PingHud();
    public static ItemESP itemESP = new ItemESP();
    public static GuiMove guiMove = new GuiMove();
    public static FreeCam freeCam = new FreeCam();
    public static FastUse fastUse = new FastUse();
    public static AntiBot antiBot = new AntiBot();
    public static Tracers tracers = new Tracers();
    public static XCarry xCarry = new XCarry();
    public static Trails trails = new Trails();
    public static Spider spider = new Spider();
    public static NoSlow noSlow = new NoSlow();
    public static NoFall noFall = new NoFall();
    public static Hotbar hotbar = new Hotbar();
    public static HitBox hitBox = new HitBox();
    public static Flight flight = new Flight();
    public static Coords coords = new Coords();
    public static AutoEZ autoEZ = new AutoEZ();
    public static AimBot aimBot = new AimBot();
    public static NoPush noPush = new NoPush();
    public static UnHook unHook = new UnHook();
    public static Speed speed = new Speed();
    public static Reach reach = new Reach();
    public static Radar radar = new Radar();
    public static Nuker nuker = new Nuker();
    public static Media media = new Media();
    public static Ghost ghost = new Ghost();
    public static Chams chams = new Chams();
    public static Phase phase = new Phase();
    public static NoBob noBob = new NoBob();
    public static Jesus jesus = new Jesus();
    public static XRay xray = new XRay();
    public static Aura aura = new Aura();
    public static FOV fov = new FOV();
    public static ESP esp = new ESP();
    public static RPC rpc = new RPC();
    public static DiscordNameplates discordNameplates = new DiscordNameplates();

    public ModuleManager() {
        for (Field field : getClass().getDeclaredFields()) {
            if (Module.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    modules.add((Module) field.get(this));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Module get(String name) {
        for (Module module : modules) {
            if (!module.getName().equalsIgnoreCase(name)) continue;
            return module;
        }
        return null;
    }

    public ArrayList<Module> getEnabledModules() {
        ArrayList<Module> enabledModules = new ArrayList<>();
        for (Module module : modules) {
            if (!module.isEnabled()) continue;
            enabledModules.add(module);
        }
        return enabledModules;
    }

    public ArrayList<Module> getModulesByCategory(Module.Category category) {
        ArrayList<Module> modulesCategory = new ArrayList<>();
        modules.forEach(module -> {
            if (module.getCategory() == category) {
                modulesCategory.add(module);
            }
        });
        return modulesCategory;
    }

    public List<Module.Category> getCategories() {
        return new ArrayList<>(Module.Category.values());
    }

    public void onLoad(String category) {
        try {
            ThunderHack.EVENT_BUS.unsubscribe(unHook);
        } catch (Exception ignored) {
        }
        unHook.setEnabled(false);

        modules.sort(Comparator.comparing(Module::getName));

        modules.forEach(m -> {
            if (m.isEnabled() && (m.getCategory().getName().equalsIgnoreCase(category) || category.equals("none")))
                ThunderHack.EVENT_BUS.subscribe(m);
        });

        if (ConfigManager.firstLaunch) {
            ModuleManager.notifications.enable();
            rpc.enable();
            soundFX.enable();
        }
    }

    public void onUpdate() {
        if (Module.fullNullCheck()) return;
        modules.stream().filter(Module::isEnabled).forEach(Module::onUpdate);
    }

    public void onRender2D(DrawContext context) {
        if (mc.getDebugHud().shouldShowDebugHud() || mc.options.hudHidden) return;
        HudElement.anyHovered = false;
        modules.stream().filter(Module::isEnabled).forEach(module -> module.onRender2D(context));
        if (!HudElement.anyHovered && !ClickGUI.anyHovered)
            if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND) {
                GLFW.glfwSetCursor(mc.getWindow().getHandle(), GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
            }
        ThunderHack.core.onRender2D(context);
    }

    public void onRender3D(MatrixStack stack) {
        modules.stream().filter(Module::isEnabled).forEach(module -> module.onRender3D(stack));
    }

    public void sortModules() {
        sortedModules = getEnabledModules().stream().filter(Module::isDrawn).sorted(Comparator.comparing(module -> FontRenderers.getModulesRenderer().getStringWidth(module.getFullArrayString()) * -1)).collect(Collectors.toList());
    }

    public void onLogout() {
        modules.forEach(Module::onLogout);
    }

    public void onLogin() {
        modules.forEach(Module::onLogin);
    }

    public void onUnload(String category) {
        modules.forEach(module -> {
            if (module.isEnabled() && (module.getCategory().getName().equalsIgnoreCase(category) || category.equals("none"))) {
                ThunderHack.EVENT_BUS.unsubscribe(module);
                module.setEnabled(false);
            }
        });
        modules.forEach(Module::onUnload);
    }

    public void onKeyPressed(int eventKey) {
        if (eventKey == -1 || eventKey == 0 || mc.currentScreen instanceof ClickGUI) {
            return;
        }
        modules.forEach(module -> {
            if (module.getBind().getKey() == eventKey)
                module.toggle();
        });
    }

    public void onKeyReleased(int eventKey) {
        if (eventKey == -1 || eventKey == 0 || mc.currentScreen instanceof ClickGUI)
            return;

        modules.forEach(module -> {
            if (module.getBind().getKey() == eventKey && module.getBind().isHold())
                module.disable();
        });
    }

    public void onMoseKeyPressed(int eventKey) {
        if (eventKey == -1 || mc.currentScreen instanceof ClickGUI) {
            return;
        }

        modules.forEach(module -> {
            if (Objects.equals(module.getBind().getBind(), "M" + eventKey)) {
                module.toggle();
            }
        });
    }

    public void onMoseKeyReleased(int eventKey) {
        if (eventKey == -1 || mc.currentScreen instanceof ClickGUI)
            return;

        activeMouseKeys.add(eventKey);

        modules.forEach(module -> {
            if (Objects.equals(module.getBind().getBind(), "M" + eventKey) && module.getBind().isHold())
                module.disable();
        });
    }

    public ArrayList<Module> getModulesSearch(String string) {
        ArrayList<Module> modulesCategory = new ArrayList<>();
        modules.forEach(module -> {
            if (module.getName().toLowerCase().contains(string.toLowerCase()))
                modulesCategory.add(module);
        });
        return modulesCategory;
    }

    public void registerModule(Module module) {
        if (module == null) return;

        this.modules.add(module);

        if (module.isEnabled())
            ThunderHack.EVENT_BUS.subscribe(module);
    }

    public void registerHudElement(HudElement hudElement) {
        if (hudElement == null) return;

        this.modules.add(hudElement);
    }
}
