/**
 * MixedModeAuth: MixedModeAuthPlayerListener.java
 */
package thulinma.mixedmodeauth;

import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;

/**
 * @author Alex "Arcalyth" Riebs (original code)
 * @author Thulinma
 */
public class MixedModeAuthPlayerListener extends PlayerListener {
  public static MixedModeAuth plugin;
  private static HashMap<String, Integer> badNames = new HashMap<String, Integer>();

  private void setPlayerGuest(Player player){
    player.sendMessage("You are currently a guest, and cannot play until you login to your account.");
    player.sendMessage("Use /auth <username> <password> to login.");
    // rename to player_entID to prevent people kicking each other off
    plugin.renameUser(player, "Player_"+player.getEntityId());
    //clear inventory
    player.getInventory().clear();
    //teleport to default spawn loc
    Location spawnat = player.getWorld().getSpawnLocation();
    while (!spawnat.getBlock().isEmpty()){spawnat.add(0, 1, 0);}
    spawnat.add(0, 1, 0);
    player.teleport(spawnat);
    plugin.log.info("[MixedModeAuth] Guest user has been asked to login.");
  }

  /**
   * @param authPlayer
   */
  public MixedModeAuthPlayerListener(MixedModeAuth instance) {
    plugin = instance;
  }
  
  public void onPlayerPreLogin(PlayerPreLoginEvent event){
    //if this person would have been allowed, but is not because of failing the verify, let them in
    if (event.getResult() != PlayerPreLoginEvent.Result.ALLOWED){
      if (event.getKickMessage().contains("Failed to verify")){
        plugin.log.info("[MixedModeAuth] Nonpremium user "+event.getName()+", overriding online mode protection!");
        badNames.put(event.getName(), (int) (System.currentTimeMillis() / 1000L));
        event.allow();
      }
    }else{
      plugin.log.info("[MixedModeAuth] User "+event.getName()+" detected as premium user.");
    }
  }

  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    String name = player.getName();
    Boolean isGood = true;
    if (name.substring(0, 6).equalsIgnoreCase("Player")) {
      setPlayerGuest(player);
    } else {
      //if secure mode is enabled...
      if (plugin.configuration.getBoolean("securemode", true)){
        // Check if player is real authenticated player
        if (badNames.containsKey(name)){
          if (badNames.get(name) > ((int)(System.currentTimeMillis() / 1000L) - 30)){
            isGood = false;
          }          
        }
        if (isGood){
          // Tell real players to enter themselves into the AuthDB
          if (!plugin.isUser(name)) {
            player.sendMessage("Welcome, " + name + "! It appears this is your first time playing on this server.");
            player.sendMessage("Please create a password for your account by typing /auth <password>");
            player.sendMessage("This will allow you to play even if minecraft login servers are down.");
            player.sendMessage("The server admin can see what you pick as password so don't use the same password as your Minecraft account!");
            plugin.log.info("[MixedModeAuth] Premium user " + name + " asked to create account.");
          } else {
            plugin.log.info("[MixedModeAuth] Premium user " + name + " auto-identified.");
          }
        } else {
          badNames.remove(name);
          setPlayerGuest(player);
        }
      } else {
        badNames.remove(name);
        setPlayerGuest(player);
      }
    }
  }

  public void onPlayerInteract(PlayerInteractEvent event){
    Player player = event.getPlayer();
    if (!plugin.isUser(player.getName())) {
      event.setCancelled(true);
      player.sendMessage("You cannot play until you have an active account.");
    }
  }

  public void onPlayerPickupItem(PlayerPickupItemEvent event){
    Player player = event.getPlayer();
    if (!plugin.isUser(player.getName())) {
      event.setCancelled(true);
      player.sendMessage("You cannot play until you have an active account.");
    }
  }

}