package thulinma.mixedmodeauth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("rawtypes")
public class Configuration extends YamlConfiguration {

  public Configuration(JavaPlugin plugin) {
    this(plugin, "config.yml");
  }
  public Configuration(JavaPlugin plugin, String fname) {
    this(plugin, new File(plugin.getDataFolder() + File.separator + fname), fname);
  }
  public Configuration(JavaPlugin plugin, File source, String fname) {
    this.source = source;
    this.plugin = (MixedModeAuth) plugin;
  }

  private File source;
  private MixedModeAuth plugin;

  @SuppressWarnings("unchecked")
  public <T> T parse(String path, T def) {
    T rval = (T) this.get(path, def);
    this.set(path, rval);
    return rval;
  }

  public boolean exists() {
    return this.source.exists();
  }
  public void init() {
    this.load();
    this.save();
  }

  public void load() {
    try {
      if (!source.exists()){
        try {
          InputStream in = plugin.getResource(this.source.getName());
          OutputStream out = new FileOutputStream(source);
          byte[] buf = new byte[1024];
          int len;
          while((len=in.read(buf))>0){
            out.write(buf,0,len);
          }
          out.close();
          in.close();
        } catch (Exception e) {
        }
      }
      this.load(this.source);
    } catch (Exception ex) {
      System.out.println("[MixedModeAuth] Error loading '" + this.source + "':");
    }
  }
  public void save() {
    try {
      this.save(this.source);
    } catch (Exception ex) {
      System.out.println("[MixedModeAuth] Error saving '" + this.source + "':");
    }
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getListOf(String path, List<T> def) {
    List list = this.getList(path, null);
    if (list == null) {
      return def;
    } else {
      List<T> rval = new ArrayList<T>();
      for (Object object : this.getList(path)) {
        try {
          rval.add((T) object);
        } catch (Throwable t) {}
      }
      return rval;
    }
  }

  public Set<String> getKeys(String path) {
    try {
      return this.getConfigurationSection(path).getKeys(false);
    } catch (Exception ex) {
      return new HashSet<String>();
    }
  }

}
