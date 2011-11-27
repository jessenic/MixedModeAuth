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
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.logging.Logger;

import net.minecraft.server.Packet20NamedEntitySpawn;
import net.minecraft.server.Packet29DestroyEntity;
import net.minecraft.server.Packet70Bed;
import net.minecraft.server.Packet9Respawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * @author Alex "Arcalyth" Riebs (original code)
 * @author Thulinma
 */
public class MixedModeAuth extends JavaPlugin {
  Logger log = Logger.getLogger("Minecraft");
  private final MixedModeAuthPlayerListener playerListener = new MixedModeAuthPlayerListener(this);
  private final MixedModeAuthBlockListener blockListener = new MixedModeAuthBlockListener(this);

  private HashMap<String, JSONObject> users = new HashMap<String, JSONObject>();
  public Configuration configuration;

  @Override
  public void onDisable() {
    PluginDescriptionFile pdfFile = getDescription();
    log.info("[MixedModeAuth] "+pdfFile.getVersion()+" disabled.");
  }

  @Override
  public void onEnable() {
    PluginDescriptionFile pdfFile = getDescription();
    PluginManager pm = this.getServer().getPluginManager();

    getDataFolder().mkdirs();
    loadUsers();
    configuration = new Configuration(this);
    configuration.load();
    if (!getServer().getOnlineMode()){
      log.warning("[MixedModeAuth] The server is setup in offline mode - cannot use secure mode! Please set server to online mode to enable secure mode!");
      configuration.set("securemode", false);
    }
    if (configuration.getBoolean("securemode", true)){
      if (!configuration.getBoolean("legacymode", false)){
        if (!this.getServer().getVersion().contains("PreLogMod")){
          log.warning("[MixedModeAuth] You do not have the server mod installed! Switching to legacy mode...");
          configuration.set("legacymode", true);
        }
      }
      if (configuration.getBoolean("legacymode", false)){
        if (!getURL("http://session.minecraft.net/game/checkserver.jsp?mixver=1").equals("MIXV1")){
          log.warning("[MixedModeAuth] You do not appear to have properly set up the latest checkserver script and host forward. Legacy mode disabled.");
          configuration.set("legacymode", false);
          if (!this.getServer().getVersion().contains("PreLogMod")){
            log.warning("[MixedModeAuth] No valid checkserver script and no server mod installed - disabling secure mode.");
            configuration.set("securemode", false);
          }
        }
      }
    }
    if (!configuration.getBoolean("securemode", true)){
      log.warning("[MixedModeAuth] Secure mode is turned OFF! Autologin disabled, everyone has to login with their username and password.");
      log.info("[MixedModeAuth] "+pdfFile.getVersion()+" enabled WITHOUT secure mode.");
    }else{
      if (!configuration.getBoolean("legacymode", false)){
        log.info("[MixedModeAuth] "+pdfFile.getVersion()+" enabled in modded secure mode.");
      }else{
        log.info("[MixedModeAuth] "+pdfFile.getVersion()+" enabled in legacy secure mode.");
      }
    }
    if (!configuration.getBoolean("renameguests", true)){
      log.info("[MixedModeAuth] Guest renaming disabled - all guests will kick each other off!");
    }
    pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Monitor, this);
    pm.registerEvent(Event.Type.PLAYER_PRELOGIN, playerListener, Event.Priority.Normal, this);
    if (configuration.getBoolean("blockinteractions", true)){
      pm.registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, Event.Priority.High, this);
      pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Event.Priority.High, this);
      pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.High, this);
    }else{
      log.info("[MixedModeAuth] Guest interactions will not be blocked by this plugin.");
    }
    if (configuration.getBoolean("kickusednames", true)){
      pm.registerEvent(Event.Type.PLAYER_LOGIN, playerListener, Event.Priority.Normal, this);
      pm.registerEvent(Event.Type.PLAYER_KICK, playerListener, Event.Priority.Normal, this);
    }
    if (configuration.getBoolean("disallowcommands", false)){
      pm.registerEvent(Event.Type.PLAYER_COMMAND_PREPROCESS, playerListener, Event.Priority.Normal, this);
    }
  }

  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
    if (cmd.getName().equalsIgnoreCase("auth")) {
      // [?] Provide help on /auth
      if (args.length <= 0) return false;
      
      if (args[0].equals("create") || args[0].equals("new") || args[0].equals("newaccount") || args[0].equals("createaccount")){
        if (sender.hasPermission("mixedmodeauth.create")){
          if (args.length < 3){
            sender.sendMessage("Usage: /auth create <new username> <new password>");
            return true;
          }
          if (!isUser(args[1])){
            newUser(args[1], args[2]);
            saveUsers();
            sender.sendMessage("New account created with name "+args[1]);
          }else{
            sender.sendMessage("This account already exists!");
          }
        }else{
          sender.sendMessage("You do not have permission to create accounts.");
        }
        return true;
      }
      
      if (args[0].equals("del") || args[0].equals("delete")){
        if (sender.hasPermission("mixedmodeauth.delete")){
          if (args.length < 2){
            sender.sendMessage("Usage: /auth delete <username>");
            return true;
          }
          if (isUser(args[1])){
            delUser(args[1]);
            saveUsers();
            sender.sendMessage("User "+args[1]+" deleted from registered users.");
          }else{
            sender.sendMessage("There is no such registered user: "+args[1]);
          }
        }else{
          sender.sendMessage("You do not have permission to delete registered players.");
        }
        return true;
      }
      
      if (!(sender instanceof Player)) {
        sender.sendMessage("You can only delete (/auth del) or create (/auth create) users from the console");
        return true;
      }

      if (args[0].equals("pass") || args[0].equals("password") || args[0].equals("passwd")){
        if (sender.hasPermission("mixedmodeauth.passwd")){
          if (args.length < 2){
            sendMess(sender, "passwdusage");
            return true;
          }
          String user = ((Player)sender).getName();
          if (isUser(user)){
            delUser(user);
            newUser(user, args[1]);
            saveUsers();
            sendMess(sender, "passchanged");
          }else{
            sendMess(sender, "notloggedin");
          }
        }else{
          sendMess(sender, "nopassperm");
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
    if (!username.toLowerCase().startsWith("player")) {
      // [?] Already have a name, and already in the db? Must be premium or already authenticated.
      if (isUser(username)) {
        sendMess(sender, "alreadylogged");
      } else {
        newUser(username, args[0]);
        saveUsers();
        sendMess(sender, "newuser");
      }
      return true;
    } else {
      // [?] I'm a guest, help!
      if (args.length < 2) {
        sendMess(sender, "authsyntax");
        return true;
      }

      String loginName = args[0];
      String password = args[1];

      if (loginName.toLowerCase().startsWith("player")) {
        sendMess(sender, "playerprefix");
        return true;
      }

      // [?] Does the player exist on the server?
      if (!isUser(loginName)) {
        if (sender.hasPermission("mixedmodeauth.create")){
          newUser(loginName, password);
          saveUsers();
          sendMess(sender, "newaccount");
          log.info("[MixedModeAuth] New user " + loginName + " created");
        }else{
          sendMess(sender, "createnotallowed");
        }
      } else {
        // [?] Player exists, is he online?
        Player checkPlayer = getServer().getPlayer(loginName);
        if (checkPlayer != null && checkPlayer.isOnline())
          sendMess(sender, "inuse");
        else {
          // [?] Player isn't currently playing, so authenticate.
          if (authUser(loginName, password)) {
            renameUser(player, loginName);
            player.loadData();
            player.teleport(player);
            sendMess(sender, "login");
            log.info("[MixedModeAuth] " + loginName + " identified via /auth");
          } else {
            sendMess(sender, "wronglogin");
          }
        }
      }
    }

    return true;
  }

  @SuppressWarnings("unchecked")
  private void loadUsers() {
    JSONParser parser = new JSONParser();
    try {
      File file = new File(getDataFolder(), "users.json");
      BufferedReader reader = new BufferedReader(new FileReader(file));
      JSONArray data = (JSONArray) parser.parse(reader);
      for (Object obj : data) {
        JSONObject user = (JSONObject) obj;
        if (!user.containsKey("hashed")){
          if (user.containsKey("pass")){
            user.put("hashed", BCrypt.hashpw((String)user.get("pass"), BCrypt.gensalt()));
            user.remove("pass");
          }
        }
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
      getDataFolder().mkdirs();
      BufferedWriter writer = new BufferedWriter(new FileWriter(file));
      writer.write(data.toString());
      writer.close();
    } catch (IOException e) {
      log.severe("[MixedModeAuth] Error writing users to file: "+e.getMessage());
    }
  }

  @SuppressWarnings("unchecked") //Prevent warnings about user.put()
  private void newUser(String name, String pass){
    JSONObject user = new JSONObject();
    user.put("name", name);
    user.put("hashed", BCrypt.hashpw(pass, BCrypt.gensalt()));
    users.put(name, user);
  }

  private void delUser(String name){
    if (isUser(name)){
      users.remove(name);
    }
  }

  public Boolean isUser(String name){
    return users.containsKey(name);
  }

  public String getURL(String url){
    String inputLine = "";
    try{
      URL mcheck = new URL(url);
      URLConnection mcheckc = mcheck.openConnection();
      mcheckc.setReadTimeout(1500);
      BufferedReader in = new BufferedReader(new InputStreamReader(mcheckc.getInputStream()));
      inputLine = in.readLine();
      in.close();
      return inputLine;
    } catch(Exception e){
      log.warning("[MixedModeAuth] Error retrieving "+url+": "+e.getMessage());
    }
    return "ERROR";
  }

  private Boolean authUser(String name, String pass){
    if (!isUser(name)){return false;}
    return BCrypt.checkpw(pass, (String) users.get(name).get("hashed"));
  }

  public void renameUser(Player p, String reName){
    this.getServer().getPluginManager().callEvent(new FakePlayerQuitEvent(p, null));
    ((CraftPlayer)p).getHandle().name = reName;
    p.setDisplayName(reName);
    p.setPlayerListName(reName);
    p.recalculatePermissions();
    Packet9Respawn p9 = new Packet9Respawn();
    p9.a = p.getWorld().getSeed();
    p9.b = p.getWorld().getEnvironment().getId();
    p9.c = p.getWorld().getDifficulty().getValue();
    p9.d = p.getWorld().getMaxHeight();
    p9.e = p.getGameMode().getValue();
    ((CraftPlayer) p).getHandle().netServerHandler.sendPacket(p9);
    ((CraftPlayer) p).getHandle().netServerHandler.sendPacket(new Packet70Bed(3, p.getGameMode().getValue()));
    Location loc = p.getLocation();
    Packet20NamedEntitySpawn p20 = new Packet20NamedEntitySpawn();
    p20.a = p.getEntityId();
    p20.b = reName; //Set the name of the player to the name they want.
    p20.c = (int) Math.floor(loc.getX() * 32.0D);
    p20.d = (int) Math.floor(loc.getY() * 32.0D);
    p20.e = (int) Math.floor(loc.getZ() * 32.0D);
    p20.f = (byte) ((int) loc.getYaw() * 256.0F / 360.0F);
    p20.g = (byte) ((int) (loc.getPitch() * 256.0F / 360.0F));
    p20.h = p.getItemInHand().getTypeId();
    Packet29DestroyEntity p29 = new Packet29DestroyEntity(p.getEntityId());
    
    for (Player p1 : Bukkit.getServer().getOnlinePlayers()) {
      if (p1 == p){continue;}
      ((CraftPlayer) p1).getHandle().netServerHandler.sendPacket(p29);
      ((CraftPlayer) p1).getHandle().netServerHandler.sendPacket(p20);
    }
	  this.getServer().getPluginManager().callEvent(new FakePlayerJoinEvent(p, reName+" logged on through /auth"));
  }
  
  public void sendMess(CommandSender to, String mess) {
    String message = configuration.getString("messages."+mess);
    if (message != null && !message.isEmpty()){
      for (String line: message.split("\n")) {
        to.sendMessage(line);
      }
    }
  }
  
}
