package ic2.common;

import ic2.api.IEnergyConductor;
import ic2.api.IEnergySink;
import ic2.api.IEnergySource;
import ic2.energy.EnergyCluster;
import ic2.platform.Platform;
import net.minecraft.server.*;
import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.util.Optional;

public class ItemToolMeter extends ItemIC2 {
  public ItemToolMeter(int i, int j) {
    super(i, j);
    this.maxStackSize = 1;
    this.setMaxDurability(0);
  }
  
  public boolean onItemUseFirst(ItemStack itemstack, EntityHuman entityhuman, World world, int i, int j, int k, int l) {
    TileEntity tileentity = world.getTileEntity(i, j, k);
    if (!(tileentity instanceof IEnergySource) && !(tileentity instanceof IEnergyConductor) &&
        !(tileentity instanceof IEnergySink)) {
      return false;
    }
    else {
      if (Platform.isSimulating()) {
        NBTTagCompound nbttagcompound = StackUtil.getOrCreateNbtData(itemstack);
        EnergyNet energyNet = EnergyNet.getForWorld(world);
        long l1 = energyNet.getTotalEnergyConducted(tileentity);
        long l2 = world.getTime();
        Optional<EnergyCluster> clusterRef = energyNet.getCluster(tileentity);

        clusterRef.ifPresent(cluster -> {
          Platform.messagePlayer(entityhuman, "§cCable cluster info:");
          Platform.messagePlayer(entityhuman, "- Hashcode: " + cluster.hashCode());
          Platform.messagePlayer(entityhuman, "- Size: " + cluster.tileAmount());
        });

        Platform.messagePlayer(entityhuman, "§cEU measurement info:");
        if (nbttagcompound.getInt("lastMeasuredTileEntityX") == i &&
            nbttagcompound.getInt("lastMeasuredTileEntityY") == j &&
            nbttagcompound.getInt("lastMeasuredTileEntityZ") == k) {
          long l3 = l2 - nbttagcompound.getLong("lastMeasureTime");
          if (l3 < 1L) {
            l3 = 1L;
          }
          
          double d = (double) (l1 - nbttagcompound.getLong("lastTotalEnergyConducted")) / (double) l3;
          DecimalFormat decimalformat = new DecimalFormat("0.##");

          Platform.messagePlayer(entityhuman,
              "- Measured power: " + decimalformat.format(d) + " EU/t (avg. over " + l3 + " ticks)");
        }
        else {
          nbttagcompound.setInt("lastMeasuredTileEntityX", i);
          nbttagcompound.setInt("lastMeasuredTileEntityY", j);
          nbttagcompound.setInt("lastMeasuredTileEntityZ", k);
          Platform.messagePlayer(entityhuman, "- Starting new measurement");
        }
        
        nbttagcompound.setLong("lastTotalEnergyConducted", l1);
        nbttagcompound.setLong("lastMeasureTime", l2);
      }
      
      return true;
    }
  }
}
