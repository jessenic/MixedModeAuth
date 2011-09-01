/**
 * MixedModeAuth: MixedModeAuth.java
 */
package thulinma.mixedmodeauth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

import net.minecraft.server.EntityPlayer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

/**
 * @author Alex "Arcalyth" Riebs (original code)
 * @author Thulinma
 */
public class MixedModeAuth extends JavaPlugin {
  Logger log = Logger.getLogger("Minecraft");
  private final MixedModeAuthPlayerListener playerListener = new MixedModeAuthPlayerListener(this);
  private final MixedModeAuthBlockListener blockListener = new MixedModeAuthBlockListener(this);

  private HashMap<String, JSONObject> users = new HashMap<String, JSONObject>();
  private PermissionHandler permissions;

  @Override
  public void onDisable() {
    PluginDescriptionFile pdfFile = getDescription();
    log.info("[MixedModeAuth] "+pdfFile.getVersion()+" disabled.");
  }

  @Override
  public void onEnable() {
    PluginManager pm = this.getServer().getPluginManager();
    pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Normal, this);
    pm.registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, Event.Priority.High, this);
    pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Event.Priority.High, this);
    pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.High, this);

    loadUsers();
    setupPermissions();

    PluginDescriptionFile pdfFile = getDescription();
    log.info("[MixedModeAuth] "+pdfFile.getVersion()+" enabled.");
  }

  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
    if (cmd.getName().equalsIgnoreCase("auth")) {
      // [?] Provide help on /auth
      if (args.length <= 0) return false;
      if (!(sender instanceof Player)) {
        sender.sendMessage("This command cannot be used from the console");
        return true;
      }

      if (args[0].equals("del") || args[0].equals("delete")){
        if (hasPermission((Player)sender, "mixedmodeauth.delete")){
          if (args.length < 2){
            sender.sendMessage("Usage: /auth delete <username>");
            return true;
          }
          if (isUser(args[1])){
            delUser(args[1]);
            sender.sendMessage("User "+args[1]+" deleted from registered users.");
          }else{
            sender.sendMessage("There is no such registered user: "+args[1]);
          }
        }else{
          sender.sendMessage("You do not have permission to delete registered players.");
        }
        return true;
      }

      if (args[0].equals("pass") || args[0].equals("password") || args[0].equals("passwd")){
        if (hasPermission((Player)sender, "mixedmodeauth.passwd")){
          if (args.length < 2){
            sender.sendMessage("Usage: /auth password <new password>");
            return true;
          }
          String user = ((Player)sender).getName();
          if (isUser(user)){
            delUser(user);
            newUser(user, args[1]);
            sender.sendMessage("Your password has been changed.");
          }else{
            sender.sendMessage("You are not currently logged in!");
          }
        }else{
          sender.sendMessage("You do not have permission to change your password.");
        }
        return true;
      }

      if (args[0].equals("create") || args[0].equals("new") || args[0].equals("newaccount") || args[0].equals("createaccount")){
        if (hasPermission((Player)sender, "mixedmodeauth.create")){
          if (args.length < 3){
            sender.sendMessage("Usage: /auth create <new username> <new password>");
            return true;
          }
          if (!isUser(args[1])){
            newUser(args[1], args[2]);
            sender.sendMessage("New account created with name "+args[1]);
          }else{
            sender.sendMessage("This account already exists!");
          }
        }else{
          sender.sendMessage("You do not have permission to create accounts.");
        }
        return true;
      }

      return authLogin(sender, args);
    }
    return false;
  }

  public boolean authLogin(CommandSender sender, String[] args) {
    if (args.length < 1) return false;

    Player player = (Player)sender;
    String username = player.getName();

    // [?] Catch people that already have a name
    if (!username.substring(0, 6).equalsIgnoreCase("Player")) {
      // [?] Already have a name, and already in the db? Must be premium or already authenticated.
      if (isUser(username)) {
        player.sendMessage("You are already logged in!");
      } else {
        newUser(username, args[0]);
        player.sendMessage("Password saved! You can now play.");
      }
      return true;
    } else {
      // [?] I'm a guest, help!
      if (args.length < 2) {
        player.sendMessage("Please supply a username AND password.");
        return true;
      }

      String loginName = args[0];
      String password = args[1];

      if (loginName.substring(0, 6).equalsIgnoreCase("Player")) {
        player.sendMessage("Please do not start your username with \"player\". Try again.");
        return true;
      }

      // [?] Does the player exist on the server?
      if (!isUser(loginName)) {
        if (hasPermission((Player)sender, "mixedmodeauth.create")){
          newUser(loginName, password);
          player.sendMessage("You have registered the name " + loginName + ". Use /auth again to authenticate.");
          log.info("[MixedModeAuth] New user " + loginName + " created");
        }else{
          player.sendMessage("You are not allowed to create new accounts.");
          player.sendMessage("To create a new account: login to this server using your minecraft account, or ask an admin to create one for you.");
        }
      } else {
        // [?] Player exists, is he online?
        Player checkPlayer = getServer().getPlayer(loginName);
        if (checkPlayer != null && checkPlayer.isOnline())
          player.sendMessage("Someone is already playing as " + loginName + "!");
        else {
          // [?] Player isn't currently playing, so authenticate.
          if (authUser(loginName, password)) {
            EntityPlayer entity = ((CraftPlayer)player).getHandle();
            entity.name = loginName;
            entity.displayName = entity.name;

            player.loadData();
            player.teleport(player);

            player.sendMessage("You are now logged in. Welcome, " + entity.name + "!");
            log.info("[MixedModeAuth] " + entity.name + " identified via /auth");
          } else {
            player.sendMessage("Wrong username or password. Try again.");
          }
        }
      }
    }

    return true;
  }

  private void setupPermissions() {
    Plugin test = this.getServer().getPluginManager().getPlugin("Permissions");

    if (permissions == null) {
      if (test != null) {
        permissions = ((Permissions) test).getHandler();
      } else {
        log.info("[MixedModeAuth] Permission system not detected, falling back to defaults.");
      }
    }
  }

  private boolean hasPermission(Player player, String node) {
    if(permissions == null) {
      if (node.equals("mixedmodeauth.passwd")){
        return true; 
      }else{
        return player.isOp();
      }
    } else {
      return permissions.has(player, node);
    }
  }

  private void loadUsers() {
    JSONParser parser = new JSONParser();
    try {
      File file = new File(getDataFolder(), "users.json");
      BufferedReader reader = new BufferedReader(new FileReader(file));
      JSONArray data = (JSONArray) parser.parse(reader);
      for (Object obj : data) {
        JSONObject user = (JSONObject) obj;
        users.put((String) user.get("name"), user);
      }
      reader.close();
    } catch (FileNotFoundException ex) {
      // Assume empty markers file
    } catch (Exception ex) {
      log.severe("[MixedModeAuth] Error reading users from file: "+ex.getMessage());
    }
  }

  @SuppressWarnings("unchecked") //prevent eclipse whining about data.add()
  
  private void saveUsers() {
    File file = new File(getDataFolder(), "users.json");
    JSONArray data = new JSONArray();
    for (JSONObject user : users.values()) {
      data.add(user);
    }
    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(file));
      writer.write(data.toString());
      writer.close();
    } catch (IOException e) {
      log.severe("[MixedModeAuth] Error writing users to file!");
    }
  }
  
  @SuppressWarnings("unchecked") //Prevent warnings about user.put()
  private void newUser(String name, String pass){
    JSONObject user = new JSONObject();
    user.put("name", name);
    user.put("pass", pass);
    users.put(name, user);
    saveUsers();
  }

  private void delUser(String name){
    if (isUser(name)){
    users.remove(name);
    saveUsers();
    }
  }

  public Boolean isUser(String name){
    return users.containsKey(name);
  }
  
  private Boolean authUser(String name, String pass){
    if (!isUser(name)){return false;}
    String upass = (String) users.get(name).get("pass");
    return upass.equals(pass);
  }
  
}
