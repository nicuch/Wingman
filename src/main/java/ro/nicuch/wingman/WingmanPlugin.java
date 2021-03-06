package ro.nicuch.wingman;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import ro.nicuch.wingman.bstats.Metrics;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WingmanPlugin extends JavaPlugin {
    private Permission vaultPerms;
    private WingmanAPI api;
    private YamlConfiguration settings;
    private boolean usePlaceholderAPI, useVault;
    public final int configVersion = 1;

    public Permission getVaultPerms() {
        return this.vaultPerms;
    }

    public WingmanAPI getAPI() {
        return this.api;
    }

    public boolean usePlaceholderAPI() {
        return this.usePlaceholderAPI;
    }

    public boolean useVault() {
        return this.useVault;
    }

    @Override
    public void onEnable() {
        try {
            this.getLogger().info("============== BEGIN LOAD ==============");
            this.reloadSettings();
            //bStats Metrics, by default enabled
            new Metrics(this);
            PluginManager manager = this.getServer().getPluginManager();

            if (!manager.isPluginEnabled("Vault"))
                this.getLogger().info("Vault not found!");
            else {
                this.getLogger().info("Vault found, try hooking!");
                this.useVault = true;
                this.vaultPerms = this.getServer().getServicesManager().getRegistration(Permission.class).getProvider();
            }
            if (!manager.isPluginEnabled("PlaceholderAPI"))
                this.getLogger().info("PlaceholderAPI not found!");
            else {
                this.getLogger().info("PlaceholderAPI found, try hooking!");
                this.usePlaceholderAPI = true;
            }
            TabExecutor tabExecutor = new WingmanCommand(this);
            this.getCommand("wingman").setExecutor(tabExecutor);
            this.getCommand("wingman").setTabCompleter(tabExecutor);
            tabExecutor = new WingmanFlyCommand(this);
            this.getCommand("fly").setExecutor(tabExecutor);
            this.getCommand("fly").setTabCompleter(tabExecutor);
            manager.registerEvents(new WingmanListeners(this), this);
            //Update checker, by default enabled
            if (this.settings.getBoolean("update_check", true))
                manager.registerEvents(new UpdateChecker(this), this);
            this.timer();
            this.autoSaver();
            this.getLogger().info("============== END LOAD ==============");
        } catch (
                Exception ex) {
            this.printError(ex); //StackOverflows are not catched directly, maybe this will help?
            this.getLogger().info("============== END LOAD ==============");
            this.setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (this.getSettings().getBoolean("settings.online-mode", true))
            this.api.getAllDataForOnline().forEach(WingmanPlayerData::save);
        else
            this.api.getAllDataForOffline().forEach(WingmanPlayerData::save);
        try {
            this.api.getConnection().close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public YamlConfiguration getSettings() {
        return this.settings;
    }

    public void reloadSettings() {
        try {
            if (this.api != null)
                this.api.stopDatabase(); //Stopping the database first
            File config = new File(this.getDataFolder() + File.separator + "config.yml");
            if (!config.exists()) {
                this.saveResource("config.yml", false);
                this.getLogger().info("A new config.yml was created!");
            }
            this.settings = YamlConfiguration.loadConfiguration(config);
            //Load config.yml first
            if (this.settings.isInt("version") && this.settings.getInt("version") != this.configVersion) {
                boolean renamed = config.renameTo(new File(
                        this.getDataFolder() + File.separator + "config_" + System.currentTimeMillis() + ".yml"));
                if (renamed) {
                    this.getLogger().info("A new config.yml was generated!");
                    this.saveResource("config.yml", true);
                    //Load again the config
                    this.settings = YamlConfiguration.loadConfiguration(config);
                } else
                    this.getLogger().info("Failed to generate a new config!");
            }
            this.api = new WingmanAPI(this);
        } catch (Exception ex) {
            this.printError(ex); //Saving files can cause IOException
        }
    }

    private void timer() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (this.getSettings().getBoolean("settings.online-mode", true)) {
                this.api.getAllDataForOnline().forEach(wingmanPlayerData -> {
                    Player p = Bukkit.getPlayer(wingmanPlayerData.getPlayerUUID());
                    if (p.isFlying() && (p.getGameMode() != GameMode.CREATIVE || p.getGameMode() != GameMode.SPECTATOR) && !this.api.hasPermission(p, "wingman.fly.permanent")) {
                        wingmanPlayerData.tickFlyTime();
                        this.api.sendActionBar(p, this.api.secondsToString(wingmanPlayerData.getFlyTime(), this.getMessageNoHeader("lang.flytime_remaining_format", ConfigDefaults.flytime_remaining_format)));
                        if (wingmanPlayerData.getFlyTime() <= 0) {
                            if (p.getAllowFlight())
                                p.sendMessage(this.getMessage("lang.fly_deactivated", ConfigDefaults.fly_deactivated));
                            p.setFlying(false);
                            p.setAllowFlight(false);
                            wingmanPlayerData.setFallProtection();
                            Bukkit.getScheduler().runTaskLater(this, wingmanPlayerData::removeFallProtection, 20 * 10L);
                        }
                    }
                });
            } else {
                this.api.getAllDataForOffline().forEach(wingmanPlayerData -> {
                    Player p = Bukkit.getPlayer(wingmanPlayerData.getPlayerName());
                    if (p.isFlying() && (p.getGameMode() != GameMode.CREATIVE || p.getGameMode() != GameMode.SPECTATOR) && !this.api.hasPermission(p, "wingman.fly.permanent")) {
                        wingmanPlayerData.tickFlyTime();
                        this.api.sendActionBar(p, this.api.secondsToString(wingmanPlayerData.getFlyTime(), this.getMessageNoHeader("lang.flytime_remaining_format", ConfigDefaults.flytime_remaining_format)));
                        if (wingmanPlayerData.getFlyTime() <= 0) {
                            if (p.getAllowFlight())
                                p.sendMessage(this.getMessage("lang.fly_deactivated", ConfigDefaults.fly_deactivated));
                            p.setFlying(false);
                            p.setAllowFlight(false);
                            wingmanPlayerData.setFallProtection();
                            Bukkit.getScheduler().runTaskLater(this, wingmanPlayerData::removeFallProtection, 20 * 10L);
                        }
                    }
                });
            }
        }, 20, 20);
    }

    private void autoSaver() {
        int auto_save = this.getSettings().getInt("settings.auto-save", 10);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (this.getSettings().getBoolean("settings.online-mode", true)) {
                this.api.getAllDataForOnline().forEach(WingmanPlayerData::save);
            } else {
                this.api.getAllDataForOffline().forEach(WingmanPlayerData::save);
            }
        }, 20L * auto_save, 20L * auto_save);
    }

    public String getMessage(String path, String def) {
        return ChatColor.translateAlternateColorCodes('&',
                this.settings.getString("lang.header", ConfigDefaults.header))
                + ChatColor.translateAlternateColorCodes('&', this.settings.getString(path, def));
    }

    public List<String> getMessage(String path, List<String> def) {
        List<String> to_return = new ArrayList<>();
        for (String str : (this.settings.isList(path) ? this.settings.getStringList(path) : def))
            to_return.add(ChatColor.translateAlternateColorCodes('&',
                    this.settings.getString("lang.header", ConfigDefaults.header))
                    + ChatColor.translateAlternateColorCodes('&', str));
        return to_return;
    }

    public String getMessageNoHeader(String path, String def) {
        return ChatColor.translateAlternateColorCodes('&', this.settings.getString(path, def));
    }

    public List<String> getMessageNoHeader(String path, List<String> def) {
        List<String> to_return = new ArrayList<>();
        for (String str : (this.settings.isList(path) ? this.settings.getStringList(path) : def))
            to_return.add(ChatColor.translateAlternateColorCodes('&', str));
        return to_return;
    }

    /*
     * author GamerKing195 (from AutoupdaterAPI)
     */
    public void printError(Exception ex) {
        this.getLogger().severe("A severe error has occurred with Wingman.");
        this.getLogger().severe("If you cannot figure out this error on your own (e.g. a config error) please copy and paste everything from here to END ERROR and post it at https://github.com/nicuch/Wingman/issues.");
        this.getLogger().severe("");
        this.getLogger().severe("============== BEGIN ERROR ==============");
        this.getLogger().severe("PLUGIN VERSION: Wingman " + getDescription().getVersion());
        this.getLogger().severe("");
        this.getLogger().severe("MESSAGE: " + ex.getMessage());
        this.getLogger().severe("");
        this.getLogger().severe("STACKTRACE: ");
        ex.printStackTrace();
        this.getLogger().severe("");
        this.getLogger().severe("============== END ERROR ==============");
    }
}
