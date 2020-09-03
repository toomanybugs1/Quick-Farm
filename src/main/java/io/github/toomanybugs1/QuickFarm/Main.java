package io.github.toomanybugs1.QuickFarm;

import java.util.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.player.UserManager;

import org.jetbrains.annotations.NotNull;

public final class Main extends JavaPlugin implements Listener {

    List<String> enabledPlayers;
    mcMMO mcmmo;

    @Override
    public void onEnable() {

        Bukkit.getPluginManager().registerEvents(this, this);

        this.saveDefaultConfig();

        this.enabledPlayers = this.getConfig().getStringList("players-enabled");

        this.mcmmo = (mcMMO) Bukkit.getServer().getPluginManager().getPlugin("mcMMO");

        if (this.mcmmo == null)
            getLogger().info("This server does not have mcMMO. Disabling mcMMO features.");
        else
            getLogger().info("mcMMO has been detected!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd, @NotNull String label, String[] args) {

        if (!cmd.getName().equalsIgnoreCase("togglequickfarm") || args.length != 0)
            return false;

        if (sender instanceof Player) {

            this.enabledPlayers = this.getConfig().getStringList("players-enabled");

            String playerName = sender.getName();

            if (this.enabledPlayers.contains(playerName)) {
                this.enabledPlayers.remove(playerName);

                sender.sendMessage(ChatColor.GOLD + "[QuickFarm] "
                    + ChatColor.DARK_RED + "Quick farming disabled.");
            } else {
                this.enabledPlayers.add(playerName);

                sender.sendMessage(ChatColor.GOLD + "[QuickFarm] "
                    + ChatColor.DARK_GREEN + "Quick farming enabled.");
            }

            this.getConfig().set("players-enabled", this.enabledPlayers);
            this.saveConfig();
        } else {
            sender.sendMessage("Only players can use this command.");
        }


        return true;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        //some events were passing null blocks (specifically when using super breaker in mcmmo)
        if (event == null)
            return;

        Player player = event.getPlayer();

        if (this.enabledPlayers.contains(player.getName()))  // Ensure the player has permission to use this plugin.
            this.tryAutoReplant(event, player);
    }

    /**
     * Attempts to automatically replant a crop from seeds in the player's inventory.
     *
     * Fails under any of the following conditions:
     * <ul>
     *     <li>the block broken wasn't a crop</li>
     *     <li>the player doesn't have the corresponding seeds in his/her inventory.</li>
     * </ul>
     *
     * @param event The event containing information about the block that was broken
     * @param player The player whose inventory will be searched for an auto-plantable seed.
     */
    private void tryAutoReplant(BlockBreakEvent event, Player player) {
        Block block = event.getBlock();
        ItemStack seed = this.getPlantableSeed(block);
        BlockData blockData = block.getBlockData();

        if (seed == null || !(blockData instanceof Ageable))
            return;

        Ageable blockAge = (Ageable) block.getBlockData();
        PlayerInventory playerInventory = player.getInventory();

        if (playerInventory.containsAtLeast(seed, 1)) {
            event.setCancelled(true);

            // Drop all items that would normally be dropped.
            Collection<ItemStack> drops = block.getDrops(new ItemStack(Material.IRON_HOE), player);

            World playerWorld = player.getWorld();
            Location blockLocation = block.getLocation();

            for (ItemStack drop : drops)
                playerWorld.dropItemNaturally(blockLocation, drop);

            // Auto-replant the crop
            final int previousAge = blockAge.getAge();
            blockAge.setAge(0);
            block.setBlockData(blockAge);

            // Update the player's inventory to reflect the use of the seed during auto-replanting.
            final int stackSlot = playerInventory.first(seed.getType());
            ItemStack seedStack = playerInventory.getItem(stackSlot);


            final int newSeedAmount = seedStack.getAmount() - 1;
            seedStack.setAmount(newSeedAmount);

            playerInventory.setItem(stackSlot, newSeedAmount > 0 ?
                seedStack : null
            );

            // mcmmo should only reward xp if the crop is fully grown
            if (this.mcmmo != null && previousAge == blockAge.getMaximumAge()) {
                McMMOPlayer mcPlayer = UserManager.getPlayer(player);
                ExperienceAPI.addXpFromBlockBySkill(block.getState(), mcPlayer, PrimarySkillType.HERBALISM);
            }
        }

    }

    /**
     * Returns the plantable version of the given block, if one exists.
     *
     * @param block The block of which to get the plantable version.
     * @return The plantable version of the given block if it exists, otherwise null.
     */
    private ItemStack getPlantableSeed(Block block) {
        // Get the seed corresponding to the block just broken.
        switch(block.getType()) {
            case BEETROOTS:
                return new ItemStack(Material.BEETROOT_SEEDS);
            case CARROTS:
                return new ItemStack(Material.CARROT);
            case POTATOES:
                return new ItemStack(Material.POTATO);
            case WHEAT:
                return new ItemStack(Material.WHEAT_SEEDS);
            case SUGAR_CANE:
                return new ItemStack(Material.SUGAR_CANE);
            case PUMPKIN_STEM:
                return new ItemStack(Material.PUMPKIN_SEEDS);
            case MELON_STEM:
                return new ItemStack(Material.MELON_SEEDS);
            // case NEW_FARM_PLANT:
                // return new ItemStack(Material.NEW_FARM_SEED);
            default:  // Indicate no corresponding seed if "block" wasn't a valid crop.
                return null;
        }
    }

}
