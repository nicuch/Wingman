/*

   CitizensBooks
   Copyright (c) 2018 @ Drăghiciu 'nicuch' Nicolae

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

package ro.nicuch.wingman;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class UpdateChecker implements Listener {
    private final WingmanPlugin plugin;
    private final int resourceId = 73975;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(WingmanPlugin plugin) {
        this.plugin = plugin;
        //Async check if the plugin has an update.
        //The timer will start after the server finish loading
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            //Sync log that the plugin is checking for an update
            Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.getLogger().info("Checking for updates..."));
            //Checking for updates
            if (this.checkForUpdate()) {
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    this.plugin.getLogger().info("An update for Wingman (v" + this.latestVersion + ") is available at:");
                    this.plugin.getLogger().info("https://www.spigotmc.org/resources/wingman." + resourceId + "/");
                });
            } else
                Bukkit.getScheduler().runTask(this.plugin, () ->
                        this.plugin.getLogger().info("No new version available!"));
        }, 0, 30 * 60 * 20);
    }

    private String getSpigotVersion() {
        try {
            HttpsURLConnection con = (HttpsURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId).openConnection();
            con.setRequestMethod("GET");
            return new BufferedReader(new InputStreamReader(con.getInputStream())).readLine();
        } catch (Exception ex) {
            this.plugin.getLogger().warning("Failed to check for update on spigot.");
        }
        return null;
    }

    private boolean checkForUpdate() {
        String version = getSpigotVersion();
        if (version != null) {
            if (this.plugin.getDescription().getVersion().compareTo(version) < 0) {
                this.latestVersion = version;
                this.updateAvailable = true;
                Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.getOnlinePlayers().stream().filter(player -> this.plugin.getAPI().hasPermission(player, "wingman.notify")).forEach(player -> player.sendMessage(this.plugin.getMessage("new_version_available", ConfigDefaults.new_version_available)
                        .replace("%latest_version%", this.latestVersion == null ? "" : this.latestVersion).replace("%current_version%", this.plugin.getDescription().getVersion()))));
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!this.plugin.getSettings().getBoolean("update_check", true))
            return;
        Player player = event.getPlayer();
        if (this.plugin.getAPI().hasPermission(player, "wingman.notify"))
            return;
        if (!this.updateAvailable)
            return;
        player.sendMessage(this.plugin.getMessage("new_version_available", ConfigDefaults.new_version_available)
                .replace("%latest_version%", this.latestVersion == null ? "" : this.latestVersion).replace("%current_version%", this.plugin.getDescription().getVersion()));
    }
}
