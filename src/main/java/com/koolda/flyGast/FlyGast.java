package com.koolda.flyGast;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlyGast extends JavaPlugin implements Listener {

    private final Map<UUID, Boolean> flyEnabled = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FlyGast включен!");

        // Запускаем проверку высоты каждые 20 секунд
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayerHeight();
            }
        }.runTaskTimer(this, 0L, 20L * 20); // Каждые 20 секунд
    }

    @Override
    public void onDisable() {
        // Отключаем полет всем игрокам при выключении плагина
        for (Player player : getServer().getOnlinePlayers()) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        getLogger().info("FlyGast выключен!");
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;

        Entity vehicle = event.getVehicle();

        // Проверяем, что это Happy Ghast
        if (isHappyGhast(vehicle)) {
            // Проверяем, не в аду ли и не в энде ли игрок
            if (isInForbiddenWorld(player)) {
                player.sendMessage("§cПолет отключен в этом мире!");
                return;
            }

            // Включаем полет
            player.setAllowFlight(true);
            player.setFlying(true);
            flyEnabled.put(player.getUniqueId(), true);

            player.sendMessage("§aВы сели на Happy Ghast! Полет активирован!");
            getLogger().info("Игрок " + player.getName() + " сел на Happy Ghast и получил полет в мире " + player.getWorld().getName());
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;

        Entity vehicle = event.getVehicle();

        // Проверяем, что слез именно с Happy Ghast
        if (isHappyGhast(vehicle)) {
            // Отмечаем, что игрок слез с гаста
            flyEnabled.put(player.getUniqueId(), false);
            player.sendMessage("§eВы слезли с Happy Ghast. Полет отключится ниже 50 блоков!");
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Если игрок переместился в запрещенный мир, отключаем полет
        if (isInForbiddenWorld(player) && flyEnabled.getOrDefault(player.getUniqueId(), false)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            flyEnabled.put(player.getUniqueId(), false);
            player.sendMessage("§cПолет отключен в этом мире!");
        }

        // Если игрок переместился из запрещенного мира в разрешенный и сидит на мобе
        if (!isInForbiddenWorld(player) && player.isInsideVehicle() && isHappyGhast(player.getVehicle())) {
            World fromWorld = event.getFrom();
            if (isForbiddenWorld(fromWorld)) {
                player.setAllowFlight(true);
                player.setFlying(true);
                flyEnabled.put(player.getUniqueId(), true);
                player.sendMessage("§aПолет снова активирован!");
            }
        }
    }

    private void checkPlayerHeight() {
        for (Player player : getServer().getOnlinePlayers()) {
            // Проверяем, есть ли у игрока активный полет от нашего плагина
            if (!flyEnabled.getOrDefault(player.getUniqueId(), false)) {
                continue;
            }

            // Проверяем, не в запрещенном ли мире игрок
            if (isInForbiddenWorld(player)) {
                player.setAllowFlight(false);
                player.setFlying(false);
                flyEnabled.put(player.getUniqueId(), false);
                player.sendMessage("§cПолет отключен в этом мире!");
                continue;
            }

            // Проверяем высоту
            int FLY_DISABLE_HEIGHT = 50;
            if (player.getLocation().getY() < FLY_DISABLE_HEIGHT) {
                // Отключаем полет
                player.setAllowFlight(false);
                player.setFlying(false);
                flyEnabled.put(player.getUniqueId(), false);

                player.sendMessage("§cВы ниже 50 блоков! Полет отключен!");
                getLogger().info("Полет отключен для " + player.getName() + " (ниже 50 блоков)");
            }

            // Если игрок не на Happy Ghast, но выше 50 блоков - полет остается
            if (!isPlayerOnHappyGhast(player)) {
                player.getLocation();
            }// Игрок слез с гаста, но еще высоко - полет остается
// Ничего не делаем
        }
    }

    private boolean isHappyGhast(Entity entity) {
        if (entity == null) return false;

        // Проверяем название моба
        if (entity.customName() != null) {
            String name = String.valueOf(entity.customName());
            // Убираем цветовые коды для проверки
            String cleanName = name.replaceAll("§[0-9a-fk-or]", "");
            return cleanName.contains("Happy Ghast");
        }

        // Также проверяем тип сущности, если Happy Ghast - это кастомный моб
        String entityType = entity.getType().toString();
        return entityType.contains("HAPPY_GHAST") || entityType.contains("HAPPYGHAST");
    }

    private boolean isPlayerOnHappyGhast(Player player) {
        if (!player.isInsideVehicle()) return false;

        Entity vehicle = player.getVehicle();
        return isHappyGhast(vehicle);
    }

    private boolean isInForbiddenWorld(Player player) {
        World world = player.getWorld();
        String worldName = world.getName().toLowerCase();
        World.Environment environment = world.getEnvironment();

        // Проверяем по окружению
        if (environment == World.Environment.NETHER) {
            return true; // Запрещаем в аду
        }

        if (environment == World.Environment.THE_END) {
            return true; // Запрещаем в энде
        }

        // Также проверяем по названиям на всякий случай
        return worldName.contains("nether") || worldName.contains("hell") || worldName.contains("end") || worldName.contains("the_end");
    }

    private boolean isForbiddenWorld(World world) {
        return world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END;
    }
}