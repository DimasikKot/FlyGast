package com.koolda.flyGast;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class FlyGast extends JavaPlugin implements Listener {

    private final Map<UUID, Boolean> slowFallingActive = new HashMap<>();
    private final Map<UUID, Boolean> flyDisabledByCommand = new HashMap<>();
    private final int HAPPY_GHAST_RADIUS = 200;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FlyGast включен!");

        // Запускаем проверку каждые 2 секунды для эффекта плавного падения
        new BukkitRunnable() {
            @Override
            public void run() {
                checkSlowFallingEffect();
            }
        }.runTaskTimer(this, 0L, 20L * 10); // Каждые 10 секунд
    }

    @Override
    public void onDisable() {
        // Отключаем полет всем игрокам при выключении плагина
        for (Player player : getServer().getOnlinePlayers()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
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
                player.sendMessage("§cHappy Ghast не работает в этом мире!");
                return;
            }

            player.sendMessage("§aВы сели на Happy Ghast! В радиусе 200 блоков вы получите эффект плавного падения!");
            getLogger().info("Игрок " + player.getName() + " сел на Happy Ghast");
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;

        Entity vehicle = event.getVehicle();

        // Проверяем, что слез именно с Happy Ghast
        if (isHappyGhast(vehicle)) {
            // Убираем статус активного эффекта
            slowFallingActive.remove(player.getUniqueId());
            player.sendMessage("§eВы слезли с Happy Ghast. Эффект плавного падения больше не активен!");
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Если игрок переместился в запрещенный мир
        if (isInForbiddenWorld(player)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
            slowFallingActive.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        if (command.equals("/fly") || command.equals("fly")) {
            event.setCancelled(true); // Отменяем стандартную команду если она есть

            UUID playerId = player.getUniqueId();
            boolean currentStatus = flyDisabledByCommand.getOrDefault(playerId, false);

            if (currentStatus) {
                // Включаем полет обратно
                flyDisabledByCommand.put(playerId, false);
                player.sendMessage("§aВы снова можете летать при наличии эффекта плавного падения!");

                // Проверяем, должен ли игрок сейчас летать
                if (hasSlowFalling(player) && !isInForbiddenWorld(player)) {
                    player.setAllowFlight(true);
                    if (!player.isFlying()) {
                        player.setFlying(true);
                    }
                }
            } else {
                // Отключаем полет
                flyDisabledByCommand.put(playerId, true);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage("§cВы отключили полет. Чтобы снова летать, введите /fly еще раз!");
            }
        }
    }

    private void checkSlowFallingEffect() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Проверяем, не в запрещенном ли мире игрок
            if (isInForbiddenWorld(player)) {
                if (player.getAllowFlight()) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
                player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                slowFallingActive.remove(player.getUniqueId());
                continue;
            }

            // Проверяем, отключил ли игрок полет через команду
            if (flyDisabledByCommand.getOrDefault(player.getUniqueId(), false)) {
                player.setAllowFlight(false);
                player.setFlying(false);
                continue;
            }

            // Проверяем, сидит ли игрок на Happy Ghast
            if (player.isInsideVehicle() && isHappyGhast(player.getVehicle())) {
                Entity vehicle = player.getVehicle();

                // Проверяем расстояние до Happy Ghast
                if (player.getLocation().distance(vehicle.getLocation()) <= HAPPY_GHAST_RADIUS) {
                    // Даем эффект плавного падения, если его нет
                    if (!player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOW_FALLING,
                                20 * 20, // 10 секунд (обновляется каждые 2 секунды)
                                0,   // Уровень 1
                                false,
                                false,
                                true
                        ));
                        slowFallingActive.put(player.getUniqueId(), true);
                    }
                }
            }

            // Управление полетом на основе эффекта плавного падения
            boolean hasSlowFalling = hasSlowFalling(player);
            boolean hadSlowFalling = slowFallingActive.getOrDefault(player.getUniqueId(), false);

            if (hasSlowFalling) {
                // Эффект появился - включаем полет
                if (!flyDisabledByCommand.getOrDefault(player.getUniqueId(), false)) {
                    player.setAllowFlight(true);
                    player.sendMessage("§aВы получили способность летать благодаря эффекту плавного падения!");
                }
                slowFallingActive.put(player.getUniqueId(), true);
            } else {
                // Эффект пропал - отключаем полет
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage("§cЭффект плавного падения закончился - полет отключен!");
                slowFallingActive.put(player.getUniqueId(), false);
            }
        }
    }

    private boolean hasSlowFalling(Player player) {
        return player.hasPotionEffect(PotionEffectType.SLOW_FALLING) &&
                Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SLOW_FALLING)).getAmplifier() == 0;
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
}