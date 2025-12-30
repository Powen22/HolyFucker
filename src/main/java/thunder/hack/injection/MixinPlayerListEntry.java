package thunder.hack.injection;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.utility.OptifineCapes;
import thunder.hack.utility.ThunderUtility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Objects;

@Mixin(PlayerListEntry.class)
public class MixinPlayerListEntry {

    @Unique
    private boolean loadedCapeTexture;

    @Unique
    private Identifier customCapeTexture;

    @Inject(method = "<init>(Lcom/mojang/authlib/GameProfile;Z)V", at = @At("TAIL"))
    private void initHook(GameProfile profile, boolean secureChatEnforced, CallbackInfo ci) {
        getTexture(profile);
    }

    @Inject(method = "getSkinTextures", at = @At("TAIL"), cancellable = true)
    private void getCapeTexture(CallbackInfoReturnable<SkinTextures> cir) {
        if (customCapeTexture != null) {
            SkinTextures prev = cir.getReturnValue();
            SkinTextures newTextures = new SkinTextures(prev.texture(), prev.textureUrl(), customCapeTexture, customCapeTexture, prev.model(), prev.secure());
            cir.setReturnValue(newTextures);
        }
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void getDisplayNameHook(CallbackInfoReturnable<Text> cir) {
        PlayerListEntry entry = (PlayerListEntry) (Object) this;
        String playerName = entry.getProfile().getName();
        
        // Добавляем [F] если игрок в друзьях
        if (Managers.FRIEND.isFriend(playerName)) {
            Text originalText = cir.getReturnValue();
            // Ярко-зеленый цвет для [F]
            TextColor greenColor = TextColor.fromRgb(0x00FF00); // Ярко-зеленый RGB
            Text modifiedText = Text.literal("[F] ").setStyle(Style.EMPTY.withColor(greenColor)).append(originalText);
            cir.setReturnValue(modifiedText);
        }
    }

    @Unique
    private void getTexture(GameProfile profile) {
        if (loadedCapeTexture) return;
        loadedCapeTexture = true;

        Util.getMainWorkerExecutor().execute(() -> {
            try {
                URL capesList = new URL("https://raw.githubusercontent.com/Pan4ur/THRecodeUtil/main/capes/capeBase.txt");
                BufferedReader in = new BufferedReader(new InputStreamReader(capesList.openStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    String colune = inputLine.trim();
                    String name = colune.split(":")[0];
                    String cape = colune.split(":")[1];
                    if (Objects.equals(profile.getName(), name)) {
                        customCapeTexture = Identifier.of("holyfacker", "textures/capes/" + cape + ".png");
                        return;
                    }
                }
            } catch (Exception ignored) {
            }

            for (String str : ThunderUtility.starGazer) {
                if (profile.getName().toLowerCase().equals(str.toLowerCase()))
                    customCapeTexture = Identifier.of("holyfacker", "textures/capes/starcape.png");
            }

            if (ModuleManager.optifineCapes.isEnabled())
                OptifineCapes.loadPlayerCape(profile, id -> {
                    customCapeTexture = id;
                });
        });
    }
}