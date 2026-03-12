package com.koolda.sitOnHappyGhastToFly;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SitOnHappyGhastToFly extends JavaPlugin implements Listener {

    private int radius;
    private boolean slowDownOnDisable;
    private boolean slowDownOnCommandFly;
    private boolean flightsIntoNormal;
    private boolean flightsIntoNether;
    private boolean flightsIntoTheEnd;
    private boolean flightsIntoOther;

    private boolean sendOnExitMessage;
    private String onExitMessage;
    private boolean sendOnExitUpdateRadius;
    private String onExitUpdateRadius;
    private boolean sendOnCommandFlyMessage;
    private String onCommandFlyMessage;
    private boolean sendOnLeftTheRadiusMessage;
    private String onLeftTheRadiusMessage;
    private boolean sendOnChangeGamemode;
    private String onChangeGamemode;

    // Активный статус "строитель"
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        radius = getConfig().getInt("radius", 5);
        slowDownOnDisable = getConfig().getBoolean("slow-down-on-disable", true);
        slowDownOnCommandFly = getConfig().getBoolean("slow-down-on-command-fly", false);
        flightsIntoNormal = getConfig().getBoolean("flights-into-normal", true);
        flightsIntoNether = getConfig().getBoolean("flights-into-nether", true);
        flightsIntoTheEnd = getConfig().getBoolean("flights-into-the-end", true);
        flightsIntoOther = getConfig().getBoolean("flights-into-other", true);

        sendOnExitMessage = getConfig().getBoolean("message.send-on-exit", true);
        onExitMessage = getConfig().getString("message.on-exit");
        sendOnExitUpdateRadius = getConfig().getBoolean("message.send-on-exit-update-radius", true);
        onExitUpdateRadius = getConfig().getString("message.on-exit-update-radius");
        sendOnCommandFlyMessage = getConfig().getBoolean("message.send-on-command-fly", true);
        onCommandFlyMessage = getConfig().getString("message.on-command-fly");
        sendOnLeftTheRadiusMessage = getConfig().getBoolean("message.send-on-left-the-radius", true);
        onLeftTheRadiusMessage = getConfig().getString("message.on-left-the-radius");
        sendOnChangeGamemode = getConfig().getBoolean("message.send-on-change-gamemode", true);
        onChangeGamemode = getConfig().getString("message.on-change-gamemode");

        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(this, 0L, 20L * 10); // каждые 10 секунд

        getLogger().info("SitOnHappyGhastToFly включён");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!sessions.containsKey(player.getUniqueId())) return;

            disableFlight(player);
            if (slowDownOnDisable) {
                giveSlowFalling(player);
            }
        }
        sessions.clear();
    }

    /* ================= СОБЫТИЯ ================= */

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (isNotHappyGhast(event.getVehicle())) return;
        if (isForbiddenWorld(player.getWorld())) return;

        Entity ghast = event.getVehicle();
        UUID id = player.getUniqueId();

        boolean isUpdateRadius = sessions.containsKey(id);

        sessions.put(player.getUniqueId(), new PlayerSession(ghast.getUniqueId(), ghast.getLocation(), player.getWorld().getEnvironment()));

        enableFlight(player);

        if (isUpdateRadius) {
            if (sendOnExitUpdateRadius) {
                player.sendMessage(onExitUpdateRadius);
            }
        } else {
            if (sendOnExitMessage) {
                player.sendMessage(onExitMessage);
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().equalsIgnoreCase("/fly")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (!sessions.containsKey(id)) return;
        sessions.remove(id);

        disableFlight(player);
        if (slowDownOnCommandFly) {
            giveSlowFalling(player);
        }

        if (sendOnCommandFlyMessage) {
            player.sendMessage(onCommandFlyMessage);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (sessions.containsKey(player.getUniqueId())) {
            if (slowDownOnDisable) {
                giveSlowFalling(player);
            }
        }
    }

    @EventHandler
    public void onPlayer(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (sessions.containsKey(player.getUniqueId())) {
            enableFlight(player);
            if (slowDownOnDisable) {
                clearSlowFalling(player);
            }
        }
    }

    /* ================= ОСНОВНАЯ ЛОГИКА ================= */

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();

            if (!sessions.containsKey(id)) continue;

            if (isForbiddenWorld(player.getWorld())) {
                sessions.remove(id);

                disableFlight(player);

                if (sendOnLeftTheRadiusMessage) {
                    player.sendMessage(onLeftTheRadiusMessage);
                }

                continue;
            }

            PlayerSession session = sessions.get(id);

            if (isChangeWorld(player.getWorld(), session.startWorld)) {
                sessions.remove(id);

                disableFlight(player);

                if (sendOnLeftTheRadiusMessage) {
                    player.sendMessage(onLeftTheRadiusMessage);
                }

                continue;
            }

            if (player.getLocation().distance(session.ghastLocation()) > radius) {
                sessions.remove(id);

                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    if (sendOnChangeGamemode) {
                        player.sendMessage(onChangeGamemode);
                    }

                    continue;
                }

                disableFlight(player);
                if (slowDownOnDisable) {
                    giveSlowFalling(player);
                }

                if (sendOnLeftTheRadiusMessage) {
                    player.sendMessage(onLeftTheRadiusMessage);
                }

                continue;
            }

            enableFlight(player);
        }
    }

    /* ================= ВСПОМОГАТЕЛЬНЫЕ ================= */

    private void enableFlight(Player player) {
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
    }

    private void disableFlight(Player player) {
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void giveSlowFalling(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, // 20 секунд
                0, false, false, true));
    }

    private void clearSlowFalling(Player player) {
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
    }

    private boolean isNotHappyGhast(Entity entity) {
        if (entity == null) return true;
        return !(entity.getType() == EntityType.HAPPY_GHAST);
    }

    private boolean isForbiddenWorld(World world) { // Это запрещённый мир?
        World.Environment environment = world.getEnvironment();

        return switch (environment) {
            case NORMAL -> !flightsIntoNormal;
            case NETHER -> !flightsIntoNether;
            case THE_END -> !flightsIntoTheEnd;
            default -> !flightsIntoOther;
        };
    }

    private boolean isChangeWorld(World currentWorld, World.Environment startWorld) { // Это переход между мирами?
        return !(startWorld == currentWorld.getEnvironment());
    }

    /* ================= RECORD ================= */

    private record PlayerSession(UUID playerId, Location ghastLocation, World.Environment startWorld) {}
}