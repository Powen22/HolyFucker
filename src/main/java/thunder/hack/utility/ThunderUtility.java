package thunder.hack.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import thunder.hack.HolyFacker;
import thunder.hack.utility.math.MathUtility;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static thunder.hack.core.manager.client.ConfigManager.IMAGES_FOLDER;
import static thunder.hack.features.modules.Module.mc;

public final class ThunderUtility {
    public static List<String> changeLog = new ArrayList<>();
    public static List<String> starGazer = new ArrayList<>();

    public static @NotNull String getAuthors() {
        List<String> names = HolyFacker.MOD_META.getAuthors()
                .stream()
                .map(Person::getName)
                .toList();

        return String.join(", ", names);
    }

    public static String solveName(String notSolved) {
        AtomicReference<String> mb = new AtomicReference<>("FATAL ERROR");
        Objects.requireNonNull(mc.getNetworkHandler()).getListedPlayerListEntries().forEach(player -> {
            if (notSolved.contains(player.getProfile().getName())) {
                mb.set(player.getProfile().getName());
            }
        });

        return mb.get();
    }

    public static Identifier getCustomImg(String name) throws IOException {
        return mc.getTextureManager().registerDynamicTexture("th-" + name + "-" + (int) MathUtility.random(0, 1000), new NativeImageBackedTexture(NativeImage.read(new FileInputStream(IMAGES_FOLDER + "/" + name + ".png"))));
    }

    public static void syncVersion() {
        try {
            if (!new BufferedReader(new InputStreamReader(new URL("https://raw.githubusercontent.com/Pan4ur/THRecodeUtil/main/syncVersion121.txt").openStream())).readLine().equals(HolyFacker.VERSION))
                HolyFacker.isOutdated = true;
        } catch (Exception ignored) {
        }
    }

    public static void parseStarGazer() {
        // Disabled - removed external repository link
    }

    public static void syncContributors() {
        try {
            URL list = new URL("https://raw.githubusercontent.com/Pan4ur/THRecodeUtil/main/thTeam.txt");
            BufferedReader in = new BufferedReader(new InputStreamReader(list.openStream(), StandardCharsets.UTF_8));
            String inputLine;
            int i = 0;
            while ((inputLine = in.readLine()) != null) {
                HolyFacker.contributors[i] = inputLine.trim();
                i++;
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String readManifestField(String fieldName) {
        try {
            Enumeration<URL> en = Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME);
            while (en.hasMoreElements()) {
                try {
                    URL url = en.nextElement();
                    InputStream is = url.openStream();
                    if (is != null) {
                        String s = new Manifest(is).getMainAttributes().getValue(fieldName);
                        if (s != null)
                            return s;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return "0";
    }


    public static void parseCommits() {
        // Disabled - removed external repository link and changelog
    }
}
