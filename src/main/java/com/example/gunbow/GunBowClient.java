package com.example.gunbow;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

/**
 * Registers one optional built-in resource pack per gun (each replaces the bow's item model/texture)
 * and adds keybinds to cycle through them. Selection is stored in vanilla options.txt, so it persists.
 *
 * Fabric/Minecraft API names move between versions; if a line below fails to compile on 26.3,
 * these calls were checked against the Fabric 26.x docs.
 */
public class GunBowClient implements ClientModInitializer {
    public static final String MOD_ID = "gunbow";
    private static final String[][] GUNS = loadGuns();

    private static KeyMapping next, prev, reset;

    @Override
    public void onInitializeClient() {
        ModContainer self = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();

        // API CHECK 1: built-in pack registration
        for (String[] g : GUNS) {
            ResourceLoader.registerBuiltinPack(
                Identifier.fromNamespaceAndPath(MOD_ID, g[0]), self,
                Component.literal("Bow: " + g[1]), PackActivationType.NORMAL);
        }

        // API CHECK 2: keybinding category + constructor
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        next  = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gunbow.next",  71, cat));
        prev  = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gunbow.prev",  72, cat));
        reset = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gunbow.reset", 74, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (next.consumeClick())  cycle(mc, +1);
            while (prev.consumeClick())  cycle(mc, -1);
            while (reset.consumeClick()) cycle(mc, 0);
        });
    }

    /** dir: +1 next gun, -1 previous gun, 0 back to vanilla bow. */
    private static void cycle(Minecraft mc, int dir) {
        PackRepository repo = mc.getResourcePackRepository();   // API CHECK 3: PackRepository accessor name
        repo.reload();

        List<String> ours = new ArrayList<>();
        for (String id : repo.getAvailableIds()) if (id.contains(MOD_ID)) ours.add(id);
        Collections.sort(ours);
        if (ours.isEmpty()) return;

        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        int cur = -1;
        for (int i = 0; i < ours.size(); i++) if (selected.contains(ours.get(i))) cur = i;
        selected.removeAll(ours);

        Component msg;
        if (dir == 0) {
            msg = Component.literal("Bow skin: vanilla");
        } else {
            int n = cur < 0 ? (dir > 0 ? 0 : ours.size() - 1) : Math.floorMod(cur + dir, ours.size());
            selected.add(ours.get(n));                       // last = highest priority
            msg = Component.literal("Bow skin: " + nameFor(ours.get(n)) + " (" + (n + 1) + "/" + ours.size() + ")");
        }
        repo.setSelected(selected);
        mc.options.updateResourcePacks(repo);                // saves options + reloads resources
        if (mc.player != null) mc.player.sendSystemMessage(msg);
    }

    private static String nameFor(String packId) {
        for (String[] g : GUNS) if (packId.endsWith(g[0])) return g[1];
        return packId;
    }

    private static String[][] loadGuns() {
        try (Reader r = new InputStreamReader(GunBowClient.class.getResourceAsStream("/guns.json"))) {
            var arr = com.google.gson.JsonParser.parseReader(r).getAsJsonArray();
            String[][] out = new String[arr.size()][];
            for (int i = 0; i < out.length; i++) {
                var p = arr.get(i).getAsJsonArray();
                out[i] = new String[]{p.get(0).getAsString(), p.get(1).getAsString()};
            }
            return out;
        } catch (Exception e) { throw new RuntimeException("guns.json missing", e); }
    }
}
