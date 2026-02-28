package com.koolda.flyGast;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class FlyGast extends JavaPlugin implements Listener {

    private int radius;
    private boolean slowDownOnLeftTheRadius;
    private boolean flightsIntoNormal;
    private boolean flightsIntoNether;
    private boolean flightsIntoTheEnd;
    private boolean flightsIntoOther;

    private boolean sendOnEnterMessage;
    private String onEnterMessage;
    private boolean sendOnExitMessage;
    private String onExitMessage;
    private boolean sendOnCommandFlyMessage;
    private String onCommandFlyMessage;
    private boolean sendOnLeftTheRadiusMessage;
    private String onLeftTheRadiusMessage;

    /** Активный статус "садился на счастливого гаста" */
    private final Map<UUID, HappyGhastSession> sessions = new HashMap<>();

    /** Игроки, которые вручную отключили /fly */
    private final Set<UUID> disabledByCommand = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        radius = getConfig().getInt("radius", 5);
        slowDownOnLeftTheRadius = getConfig().getBoolean("slow-down-on-left-the-radius", false);
        flightsIntoNormal = getConfig().getBoolean("flights-into-normal", false);
        flightsIntoNether = getConfig().getBoolean("flights-into-nether", false);
        flightsIntoTheEnd = getConfig().getBoolean("flights-into-the-end", false);
        flightsIntoOther = getConfig().getBoolean("flights-into-other", false);

        sendOnEnterMessage = getConfig().getBoolean("message.send-on-enter", false);
        onEnterMessage = getConfig().getString("message.on-enter");
        sendOnExitMessage = getConfig().getBoolean("message.send-on-exit", false);
        onExitMessage = getConfig().getString("message.on-exit");
        sendOnCommandFlyMessage = getConfig().getBoolean("message.send-on-command-fly", false);
        onCommandFlyMessage = getConfig().getString("message.on-command-fly");
        sendOnLeftTheRadiusMessage = getConfig().getBoolean("message.send-on-left-the-radius", false);
        onLeftTheRadiusMessage = getConfig().getString("message.on-left-the-radius");

        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(this, 0L, 20L * 10); // каждые 10 секунд

        getLogger().info("FlyGast включён");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
        sessions.clear();
        disabledByCommand.clear();
    }

    /* ================= СОБЫТИЯ ================= */

    @EventHandler
    public void onEnter(VehicleEnterEvent e) {
        if (!(e.getEntered() instanceof Player player)) return;
        if (isNotHappyGhast(e.getVehicle())) return;

        if (sendOnEnterMessage) {
            player.sendMessage(onEnterMessage);
        }
    }

    @EventHandler
    public void onExit(VehicleExitEvent e) {
        if (!(e.getExited() instanceof Player player)) return;
        if (isNotHappyGhast(e.getVehicle())) return;
        if (isForbiddenWorld(player.getWorld())) return;

        Entity ghast = e.getVehicle();

        sessions.put(player.getUniqueId(),
                new HappyGhastSession(ghast.getUniqueId(), ghast.getLocation()));

        disabledByCommand.remove(player.getUniqueId());
        enableFlight(player);

        if (sendOnExitMessage) {
            player.sendMessage(onExitMessage);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!e.getMessage().equalsIgnoreCase("/fly")) return;

        e.setCancelled(true);
        Player player = e.getPlayer();

        sessions.remove(player.getUniqueId());
        disabledByCommand.add(player.getUniqueId());

        player.setAllowFlight(false);
        player.setFlying(false);

        if (sendOnCommandFlyMessage) {
            player.sendMessage(onCommandFlyMessage);
        }
    }

    /* ================= ОСНОВНАЯ ЛОГИКА ================= */

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();

            if (!sessions.containsKey(id)) continue;
            if (disabledByCommand.contains(id)) continue;

            if (isForbiddenWorld(player.getWorld())) {
                sessions.remove(id);
                disableFlight(player);
                continue;
            }

            HappyGhastSession session = sessions.get(id);

            if (player.getLocation().distance(session.ghastLocation()) > radius) {
                sessions.remove(id);
                disableFlight(player);

                // 👇 ВАЖНО: эффект ТОЛЬКО при отходе
                if (slowDownOnLeftTheRadius) {
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

    private void enableFlight(Player p) {
        if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
            p.setFlying(true);
        }
    }

    private void disableFlight(Player p) {
        p.setAllowFlight(false);
        p.setFlying(false);
    }

    private void giveSlowFalling(Player p) {
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING,
                20 * 20, // 20 секунд
                0,
                false,
                false,
                true
        ));
    }

    private boolean isNotHappyGhast(Entity entity) {
        if (entity == null) return true;

        if (entity.customName() != null) {
            String name = Objects.requireNonNull(entity.customName()).toString()
                    .replaceAll("§[0-9a-fk-or]", "");
            return !name.contains("Happy Ghast");
        }

        return !entity.getType().toString().contains("GHAST");
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

    /* ================= RECORD ================= */

    private record HappyGhastSession(UUID ghastId, Location ghastLocation) {}
}