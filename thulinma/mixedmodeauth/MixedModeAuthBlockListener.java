/**
 * MixedModeAuth: MixedModeAuthBlockListener.java
 */
package thulinma.mixedmodeauth;

import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;

/**
 * @author Alex "Arcalyth" Riebs (original code)
 * @author Thulinma
 */
public class MixedModeAuthBlockListener extends BlockListener {
  public static MixedModeAuth plugin;

  public MixedModeAuthBlockListener(MixedModeAuth instance) {
    plugin = instance;
  }

  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (!plugin.isUser(player.getName())) {
      event.setCancelled(true);
      player.sendMessage("You cannot play until you have an active account.");
    }
  }
}
