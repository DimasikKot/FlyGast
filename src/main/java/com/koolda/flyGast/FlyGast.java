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

    /** Активный статус "садился на счастливого гаста" */
    private final Map<UUID, HappyGhastSession> sessions = new HashMap<>();

    /** Игроки, которые вручную отключили /fly */
    private final Set<UUID> disabledByCommand = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        radius = getConfig().getInt("radius", 5);

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

        player.sendMessage("§aВы сели на Happy Ghast");
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

        player.sendMessage("§eСтатус «садился на счастливого гаста» активирован");
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!e.getMessage().equalsIgnoreCase("/fly")) return;

        e.setCancelled(true);
        Player p = e.getPlayer();

        sessions.remove(p.getUniqueId());
        disabledByCommand.add(p.getUniqueId());

        p.setAllowFlight(false);
        p.setFlying(false);

        p.sendMessage("§cСтатус Happy Ghast отключён");
    }

    /* ================= ОСНОВНАЯ ЛОГИКА ================= */

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();

            if (!sessions.containsKey(id)) continue;
            if (disabledByCommand.contains(id)) continue;

            if (isForbiddenWorld(p.getWorld())) {
                sessions.remove(id);
                disableFlight(p);
                continue;
            }

            HappyGhastSession session = sessions.get(id);

            if (p.getLocation().distance(session.ghastLocation()) > radius) {
                sessions.remove(id);
                disableFlight(p);

                // 👇 ВАЖНО: эффект ТОЛЬКО при отходе
                giveSlowFalling(p);

                p.sendMessage("§cВы улетели далеко от Happy Ghast");
                continue;
            }

            enableFlight(p);
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

    private boolean isForbiddenWorld(World world) {
        return world.getEnvironment() == World.Environment.NETHER
                || world.getEnvironment() == World.Environment.THE_END;
    }

    /* ================= RECORD ================= */

    private record HappyGhastSession(UUID ghastId, Location ghastLocation) {}
}