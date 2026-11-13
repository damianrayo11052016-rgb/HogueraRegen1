package com.example.hogueraregen;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class HogueraRegen extends JavaPlugin {

    private BukkitTask task;

    private int radius;
    private int checkInterval;
    private int effectDuration;
    private boolean requireLit;
    private boolean showParticles;

    private boolean normalEnabled;
    private int normalAmplifier;
    private boolean soulEnabled;
    private int soulAmplifier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        startTask();
        getLogger().info("HogueraRegen activado.");
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
    }

    private void loadSettings() {
        reloadConfig();
        radius = Math.max(1, Math.min(10, getConfig().getInt("radius", 5)));
        checkInterval = Math.max(1, getConfig().getInt("check-interval-ticks", 40));
        effectDuration = Math.max(checkInterval + 20, getConfig().getInt("effect-duration-ticks", 100));
        requireLit = getConfig().getBoolean("require-lit", true);
        showParticles = getConfig().getBoolean("show-particles", true);

        int baseAmp = getConfig().getInt("amplifier", 0);
        normalEnabled = getConfig().getBoolean("normal-campfire.enabled", true);
        normalAmplifier = getConfig().getInt("normal-campfire.amplifier", baseAmp);
        soulEnabled = getConfig().getBoolean("soul-campfire.enabled", true);
        soulAmplifier = getConfig().getInt("soul-campfire.amplifier", baseAmp + 1);
    }

    private void startTask() {
        if (task != null) {
            task.cancel();
        }
        task = getServer().getScheduler().runTaskTimer(this, this::tick, checkInterval, checkInterval);
    }

    private void tick() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.hasPermission("hogueraregen.use") || player.isDead()) {
                continue;
            }
            int amplifier = findBestAmplifier(player);
            if (amplifier >= 0) {
                applyRegen(player, amplifier);
            }
        }
    }

    /** Devuelve el mejor nivel de regeneración disponible cerca del jugador, o -1 si no hay hoguera. */
    private int findBestAmplifier(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int px = loc.getBlockX();
        int py = loc.getBlockY();
        int pz = loc.getBlockZ();
        int minY = Math.max(world.getMinHeight(), py - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, py + radius);
        long radiusSq = (long) radius * radius;

        int best = -1;
        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    long dx = x - px, dy = y - py, dz = z - pz;
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    int amp;
                    if (type == Material.CAMPFIRE && normalEnabled) {
                        amp = normalAmplifier;
                    } else if (type == Material.SOUL_CAMPFIRE && soulEnabled) {
                        amp = soulAmplifier;
                    } else {
                        continue;
                    }
                    if (requireLit && !((Campfire) block.getBlockData()).isLit()) {
                        continue;
                    }
                    if (amp > best) {
                        best = amp;
                    }
                }
            }
        }
        return best;
    }

    private void applyRegen(Player player, int amplifier) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.REGENERATION);
        // No sobrescribir un efecto mejor (poción, etc.)
        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                effectDuration,
                amplifier,
                true,            // ambient
                showParticles,   // partículas
                true));          // icono
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("hogueraregen")) {
            return false;
        }
        if (!sender.hasPermission("hogueraregen.admin")) {
            sender.sendMessage(color(getConfig().getString("messages.no-permission", "&cSin permiso.")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            startTask();
            sender.sendMessage(color(getConfig().getString("messages.reloaded", "&aRecargado.")));
            return true;
        }
        return false;
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
