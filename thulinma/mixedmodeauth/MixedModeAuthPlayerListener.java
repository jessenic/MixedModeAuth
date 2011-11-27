/**
 * MixedModeAuth: MixedModeAuthPlayerListener.java
 */
package thulinma.mixedmodeauth;

import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerLoginEvent;
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
    plugin.sendMess(player, "guestwelcome");
    if (plugin.configuration.getBoolean("renameguests", true)){
      // rename to player_entID to prevent people kicking each other off
      plugin.renameUser(player, "Player_"+player.getEntityId());
    }else{
      plugin.renameUser(player, "Player");
    }
    //clear inventory
    player.getInventory().clear();
    //teleport to default spawn loc
    Location spawnat = player.getWorld().getSpawnLocation();
    while (!spawnat.getBlock().isEmpty()){spawnat.add(0, 1, 0);}
    spawnat.add(0, 1, 0);
    player.teleport(spawnat);
    plugin.log.info("[MixedModeAuth] Guest user has been asked to login.");
    
    if (plugin.configuration.getLong("kicktimeout") > 0){
      Task kicktask = new Task(plugin, player) {
        public void run() {
          if (!plugin.isUser(((Player)getArg(0)).getName())){
            ((Player)getArg(0)).kickPlayer(plugin.configuration.getString("messages.kicktimeout"));
          }
        }
      };
      kicktask.startDelayed(plugin.configuration.getLong("kicktimeout")*20, false);
    }

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
      /*
      for (Player p : plugin.getServer().getOnlinePlayers()){
        if (p.getAddress().getAddress() == event.getAddress()){
          if (p.getName().toLowerCase().startsWith("player")){
            plugin.renameUser(p, event.getName());
            p.sendMessage("[MixedModeAuth] Welcome "+event.getName()+", you have been verified!");
          }
        }
      }
       */
    }
  }

  public void onPlayerJoin(PlayerJoinEvent event) {
	if(event instanceof FakePlayerJoinEvent){
	  return;
	}
    Player player = event.getPlayer();
    String name = player.getName();
    Boolean isGood = true;
    if (name.toLowerCase().startsWith("player")){
      setPlayerGuest(player);
    } else {
      //if secure mode is enabled...
      if (plugin.configuration.getBoolean("securemode", true)){
        // Check if player is real authenticated player
        if (badNames.containsKey(name)){
          if (badNames.get(name) > ((int)(System.currentTimeMillis() / 1000L) - 30)){
            isGood = false;
          }          
        }else{
          if (plugin.configuration.getBoolean("legacymode", false)){
            isGood = plugin.getURL("http://session.minecraft.net/game/checkserver.jsp?premium="+name).equals("PREMIUM");
          }
        }
        if (isGood){
          // Tell real players to enter themselves into the AuthDB
          if (!plugin.isUser(name)) {
            plugin.sendMess(player, "premiumwelcome");
            plugin.log.info("[MixedModeAuth] Premium user " + name + " asked to create account.");
          } else {
            plugin.sendMess(player, "premiumautolog");
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
  
  public void onPlayerLogin(PlayerLoginEvent event){
    if (plugin.getServer().getPlayerExact(event.getEventName()) != null){
      event.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.configuration.getString("messages.kickusedname"));
    }
  }
  
  public void onPlayerKick(PlayerKickEvent event) {
    if (event.getReason().equals("Logged in from another location.")) {
      if (plugin.isUser(event.getPlayer().getName())){event.setCancelled(true);}
    }
  }

  public void onPlayerInteract(PlayerInteractEvent event){
    Player player = event.getPlayer();
    if (!plugin.isUser(player.getName())) {
      event.setCancelled(true);
      plugin.sendMess(player, "interactionblocked");
    }
  }

  public void onPlayerPickupItem(PlayerPickupItemEvent event){
    Player player = event.getPlayer();
    if (!plugin.isUser(player.getName())) {
      event.setCancelled(true);
      plugin.sendMess(player, "interactionblocked");
    }
  }

}