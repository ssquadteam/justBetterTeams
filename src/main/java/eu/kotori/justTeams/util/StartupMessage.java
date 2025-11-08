package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class StartupMessage {
    public static void send() {
        String engine;
        JustTeams plugin = JustTeams.getInstance();
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        MiniMessage mm = MiniMessage.miniMessage();
        String check = "<green>\u2714</green>";
        String cross = "<red>\u2716</red>";
        boolean redisEnabled = false;
        try {
            redisEnabled = plugin.getConfigManager() != null && plugin.getConfigManager().isRedisEnabled();
        } catch (Exception exception) {
            // empty catch block
        }
        boolean vaultEnabled = Bukkit.getPluginManager().isPluginEnabled("Vault");
        boolean papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        boolean pvpManagerEnabled = Bukkit.getPluginManager().isPluginEnabled("PvPManager");
        String redisStatus = redisEnabled ? check : "<gray>-</gray>";
        String vaultStatus = vaultEnabled ? check : cross;
        String papiStatus = papiEnabled ? check : cross;
        String pvpManagerStatus = pvpManagerEnabled ? check : cross;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            engine = "Folia";
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                engine = "Paper";
            } catch (ClassNotFoundException e2) {
                engine = "Spigot/Bukkit";
            }
        }
        TagResolver placeholders = TagResolver.builder().resolver((TagResolver)Placeholder.unparsed((String)"version", (String)plugin.getDescription().getVersion())).resolver((TagResolver)Placeholder.unparsed((String)"author", (String)String.join((CharSequence)", ", plugin.getDescription().getAuthors()))).build();
        String mainColor = "#4C9DDE";
        String accentColor = "#7FCAE3";
        String lineSeparator = "<dark_gray><strikethrough>                                                                                ";
        console.sendMessage(mm.deserialize(lineSeparator));
        console.sendMessage((Component)Component.empty());
        console.sendMessage(mm.deserialize("  <color:" + mainColor + ">\u2588\u2557  \u2588\u2588\u2557   <white>JustTeams <gray>v<version>", placeholders));
        console.sendMessage(mm.deserialize("  <color:" + mainColor + ">\u2588\u2588\u2551 \u2588\u2588\u2554\u255d   <gray>\u0299\u028f <white><author>", placeholders));
        console.sendMessage(mm.deserialize("  <color:" + mainColor + ">\u2588\u2588\u2588\u2588\u2588\u2554\u255d    <white>s\u1d1b\u1d00\u1d1b\u1d1cs: <color:#2ecc71>Active"));
        console.sendMessage(mm.deserialize("  <color:" + accentColor + ">\u2588\u2554\u2550\u2588\u2588\u2557"));
        console.sendMessage(mm.deserialize("  <color:" + accentColor + ">\u2588\u2551  \u2588\u2588\u2557   <white>\u0280\u1d07\u1d05\u026as \u1d04\u1d00\u1d04\u029c\u1d07: " + redisStatus + " <gray>(optional)"));
        console.sendMessage(mm.deserialize("  <color:" + accentColor + ">\u2588\u2551  \u255a\u2550\u255d   <white>\u1d20\u1d00\u1d1c\u029f\u1d1b: " + vaultStatus + " <gray>(economy)"));
        console.sendMessage((Component)Component.empty());
        console.sendMessage(mm.deserialize("  <white>\u1d18\u1d00\u1d18\u026a: " + papiStatus + " <gray>| <white>\u1d18\u1d20\u1d18\u1d0d\u1d00\u0274\u1d00\u0262\u1d07\u0280: " + pvpManagerStatus + " <gray>| <white>\u1d07\u0274\u0262\u026a\u0274\u1d07: <gray>" + engine));
        console.sendMessage((Component)Component.empty());
        console.sendMessage(mm.deserialize(lineSeparator));
    }

    public static void sendUpdateNotification(JustTeams plugin) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        MiniMessage mm = MiniMessage.miniMessage();
        TagResolver placeholders = TagResolver.builder().resolver((TagResolver)Placeholder.unparsed((String)"current_version", (String)plugin.getDescription().getVersion())).resolver((TagResolver)Placeholder.unparsed((String)"latest_version", (String)plugin.latestVersion)).build();
        String mainColor = "#f39c12";
        String accentColor = "#e67e22";
        String lineSeparator = "<dark_gray><strikethrough>                                                                                ";
        List<String> updateBlock = List.of(
            "  <color:" + mainColor + ">\u2588\u2557  \u2588\u2588\u2557   <white>JustTeams <gray>Update",
            "  <color:" + mainColor + ">\u2588\u2588\u2551 \u2588\u2588\u2554\u255d   <gray>A new version is available!",
            "  <color:" + mainColor + ">\u2588\u2588\u2588\u2588\u2588\u2554\u255d",
            "  <color:" + accentColor + ">\u2588\u2554\u2550\u2588\u2588\u2557    <white>\u1d04\u1d1c\u0280\u0280\u1d07\u0274\u1d1b: <gray><current_version>",
            "  <color:" + accentColor + ">\u2588\u2551  \u2588\u2588\u2557   <white>\u029f\u1d00\u1d1b\u1d07s\u1d1b: <green><latest_version>",
            "  <color:" + accentColor + ">\u2588\u2551  \u255a\u2550\u255d   <aqua><click:open_url:'https://builtbybit.com/resources/justteams.71401/'>Click here to download</click>",
            ""
        );
        console.sendMessage(mm.deserialize(lineSeparator));
        console.sendMessage((Component)Component.empty());
        for (String line : updateBlock) {
            console.sendMessage(mm.deserialize(line, placeholders));
        }
        console.sendMessage(mm.deserialize(lineSeparator));
    }

    public static void sendUpdateNotification(Player player, JustTeams plugin) {
        MiniMessage mm = MiniMessage.miniMessage();
        String link = "https://builtbybit.com/resources/justteams.71401/";
        player.sendMessage(mm.deserialize("<gradient:#4C9DDE:#7FCAE3>--------------------------------------------------</gradient>"));
        player.sendMessage((Component)Component.empty());
        player.sendMessage(mm.deserialize("  <gradient:#4C9DDE:#7FCAE3>JustTeams</gradient> <gray>Update Available!</gray>"));
        player.sendMessage(mm.deserialize("  <gray>A new version is available: <green>" + plugin.latestVersion + "</green>"));
        player.sendMessage(mm.deserialize("  <click:open_url:'" + link + "'><hover:show_text:'<green>Click to visit download page!'><#7FCAE3><u>Click here to download the update.</u></hover></click>"));
        player.sendMessage((Component)Component.empty());
        player.sendMessage(mm.deserialize("<gradient:#7FCAE3:#4C9DDE>--------------------------------------------------</gradient>"));
    }
}

