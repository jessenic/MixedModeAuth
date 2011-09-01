/**
 * MixedModeAuth: MixedModeAuthPlayerListener.java
 */
package thulinma.mixedmodeauth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import net.minecraft.server.EntityPlayer;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * @author Alex "Arcalyth" Riebs (original code)
 * @author Thulinma
 */
public class MixedModeAuthPlayerListener extends PlayerListener {
  public static MixedModeAuth plugin;

  private void setPlayerGuest(Player player){
    player.sendMessage("You are currently a guest, and cannot play until you login to your account.");
    player.sendMessage("Use /auth <username> <password> to login.");
    // rename to player_entID to prevent people kicking each other off
    EntityPlayer entity = ((CraftPlayer)player).getHandle();
    entity.name = "Player_"+entity.id;
    entity.displayName = entity.name;
    //clear inventory
    player.getInventory().clear();
    //teleport to default spawn loc
    player.teleport(player.getWorld().getSpawnLocation());	  
    plugin.log.info("[MixedModeAuth] Nonpremium user has been asked to login.");
  }

  /**
   * @param authPlayer
   */
  public MixedModeAuthPlayerListener(MixedModeAuth instance) {
    plugin = instance;
  }

  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    String name = player.getName();

    if (name.substring(0, 6).equalsIgnoreCase("Player")) {
      setPlayerGuest(player);
    } else {
      // Check if player is real player, first...
      String inputLine = "";
      try{
        URL mcheck = new URL("http://www.minecraft.net/game/checkserver.jsp?premium="+name);
        URLConnection mcheckc = mcheck.openConnection();
        mcheckc.setReadTimeout(1500);
        BufferedReader in = new BufferedReader(new InputStreamReader(mcheckc.getInputStream()));
        inputLine = in.readLine();
        in.close();
      } catch(Exception e){
        plugin.log.info("[MixedModeAuth] Premium check error, assuming nonpremium: "+e.getMessage());
      }
      if (inputLine.equals("PREMIUM")){
        // [?] Tell real players to enter themselves into the AuthDB
        if (!plugin.isUser(name)) {
          player.sendMessage("Welcome, " + name + "! It appears this is your first time playing on this server.");
          player.sendMessage("Please create a password for your account by typing /auth <password>");
          player.sendMessage("This will allow you to play even if minecraft login servers are down.");
          player.sendMessage("The server admin can see what you pick as password so don't use the same password as your Minecraft account!");
        } else {
          plugin.log.info("[MixedModeAuth] Premium user " + name + " auto-identified.");
        }
      } else {
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