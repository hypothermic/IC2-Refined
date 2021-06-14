package ic2.common;

import ic2.api.*;
import ic2.energy.EnergyCluster;
import ic2.platform.Platform;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.TileEntity;
import net.minecraft.server.World;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EnergyNet {

  private static final Map<World, EnergyNet> worldToEnergyNetMap = new HashMap<>();

  public static EnergyNet getForWorld(World world) {
    if (world == null) {
      System.out.println("[IC2] EnergyNet.getForWorld: world = null, bad things may happen..");
      return null;
    } else {
      if (!worldToEnergyNetMap.containsKey(world)) {
        worldToEnergyNetMap.put(world, new EnergyNet(world));
      }
      return worldToEnergyNetMap.get(world);
    }
  }

  public static void onTick(World world) { //Shock entities
    Platform.profilerStartSection("Shocking");
    getForWorld(world).onTick(); // moved to instance function
    Platform.profilerEndSection();
  }

  private final World world;
  private final HashMap<EntityLiving, Integer> entityLivingToShockEnergyMap = new HashMap<>();
  private final Collection<EnergyCluster> clusters = new LinkedList<>();

  private EnergyNet(World world) {
    this.world = world;
  }

  public void addTileEntity(TileEntity tileEntity) {
    // Don't proceed if the tile entity isn't an energy tile or is already added to the "energy net"
    if (!(tileEntity instanceof IEnergyTile) || ((IEnergyTile) tileEntity).isAddedToEnergyNet()) {
      return;
    }

    // Don't proceed if the tile is already referenced in a cluster
    if (clusters.stream().anyMatch(cluster -> cluster.containsTile(tileEntity))) {
      return;
    }

    // Get all clusters which have TileEntities adjacent to the position of the new tile entity
    List<EnergyCluster> adjacentClusters = clusters
            .stream()
            .filter(cluster -> cluster.containsTilesAdjacentTo(tileEntity))
            .distinct()
            .collect(Collectors.toList());

    // Depending on how many clusters are adjacent, do something
    switch (adjacentClusters.size()) {
      case 0: // no adjacent clusters, create a new one
        clusters.add(new EnergyCluster(tileEntity));
        break;

      case 1: // only one cluster, add the tile entity to it.
        adjacentClusters.get(0).addTile(tileEntity);
        break;

      default: // multiple clusters, merge them into one
        EnergyCluster destination = adjacentClusters.get(0);
        List<EnergyCluster> sources = clusters.stream()
                .filter(cluster -> !cluster.equals(destination))
                .collect(Collectors.toList());

        // Steal all the tiles from all clusters and add them to the destination
        sources.stream()
                .flatMap(cluster -> cluster.stealTiles().stream())
                .forEach(destination::addTile);

        // Remove the merged clusters and let them be garbage collected
        clusters.removeAll(sources);

        // Add the new tile
        destination.addTile(tileEntity);

        break;
    }
  }

  public void removeTileEntity(TileEntity tileEntity) {
    // Don't proceed if the tile entity isn't an energy tile or is not already added to the "energy net"
    if (!(tileEntity instanceof IEnergyTile) || !((IEnergyTile) tileEntity).isAddedToEnergyNet()) {
      return;
    }

    // Find the cluster that contains this tile, and remove the tile from that cluster
    clusters.stream()
            .filter(cluster -> cluster.containsTile(tileEntity))
            .findFirst()
            .ifPresent(cluster -> cluster.removeTile(tileEntity));
  }

  public void onTick() {
    for (EntityLiving entityLiving : entityLivingToShockEnergyMap.keySet()) {
      int i = (entityLivingToShockEnergyMap.get(entityLiving) + 63) / 64;

      if (entityLiving.isAlive()) {
        entityLiving.damageEntity(IC2DamageSource.electricity, i);
      }
    }
    entityLivingToShockEnergyMap.clear();
  }

  public int emitEnergyFrom(IEnergySource ienergysource, int energyAmount) {
    if (ienergysource == null || !ienergysource.isAddedToEnergyNet()) {
      return energyAmount;
    }

    clusters.stream()
            .filter(cluster -> cluster.containsTile((TileEntity) ienergysource))
            .findFirst()
            .ifPresent(cluster -> cluster.emitEnergyFrom((TileEntity) ienergysource, energyAmount));

    return 0;
/*
    else {
      if (!energySourceToEnergyPathMap.containsKey(ienergysource)) {
        energySourceToEnergyPathMap
            .put(ienergysource, discover((TileEntity) ienergysource, false, ienergysource.getMaxEnergyOutput()));
      }
      if (energySourceToEnergyPathMap.get(ienergysource).size() < 1) { // Remove empty entries
        energySourceToEnergyPathMap.remove(ienergysource);
        return energyAmount;
      }
      else {
        energySourceToEnergyPathMap.get(ienergysource).removeIf(energyPath -> energyPath.conductors == null
            || energyPath.conductors.size() == 0);
      }
      int j = 0;
      Vector<EnergyPath> vector = new Vector<>();
      double d = 0.0D;
      for (EnergyPath energypath : energySourceToEnergyPathMap.get(ienergysource)) {
        if (!EnergyNet.class.desiredAssertionStatus() && !(energypath.target instanceof IEnergySink)) {
          throw new AssertionError();
        }
        IEnergySink ienergysink = (IEnergySink) energypath.target;
        if (ienergysink.demandsEnergy()) {
          d += 1.0D / energypath.loss;
          if (!vector.contains(energypath)) //Added to prevent duplicates.
          {
            vector.add(energypath);
          }
          if (vector.size() >= energyAmount) {
            break;
          }
        }
      }
      Iterator<EnergyPath> iterator1 = vector.iterator();
      while (true) {
        int conducted;
        EnergyPath energypath1;
        do {
          IEnergySink ienergysink1;
          int k;
          int l;
          do {
            if (!iterator1.hasNext()) {
              return energyAmount - j;
            }
            energypath1 = iterator1.next();
            ienergysink1 = (IEnergySink) energypath1.target;
            k = (int) Math.floor((double) Math.round((double) energyAmount / d / energypath1.loss * 100000.0D) / 100000.0D);
            l = (int) Math.floor(energypath1.loss);
          } while (k <= l);
          int i1 = ienergysink1.injectEnergy(energypath1.targetDirection, k - l);
          j += k - i1;
          conducted = k - l - i1;
          energypath1.totalEnergyConducted += conducted;*/
//          if (conducted > energypath1.minInsulationEnergyAbsorption) {
//            List<EntityLiving> list = world.a(EntityLiving.class, AxisAlignedBB
//                .a(energypath1.minX - 1, energypath1.minY - 1, energypath1.minZ - 1,
//                    energypath1.maxX + 2, energypath1.maxY + 2, energypath1.maxZ + 2));
//            for (EntityLiving entityLiving : list) {
//              int k1 = 0;
//
//              for (IEnergyConductor iEnergyConductor : energypath1.conductors) {
//                TileEntity tileentity = (TileEntity) iEnergyConductor;
//                if (entityLiving.boundingBox.a(AxisAlignedBB
//                    .a(tileentity.x - 1, tileentity.y - 1, tileentity.z - 1,
//                        tileentity.x + 2, tileentity.y + 2, tileentity.z + 2))) {
//                  int l1 = conducted - iEnergyConductor.getInsulationEnergyAbsorption();
//                  if (l1 > k1) {
//                    k1 = l1;
//                  }
//                  if (iEnergyConductor.getInsulationEnergyAbsorption() == energypath1.minInsulationEnergyAbsorption) {
//                    break;
//                  }
//                }
//              }
//              if (entityLivingToShockEnergyMap.containsKey(entityLiving)) {
//                entityLivingToShockEnergyMap.put(entityLiving, entityLivingToShockEnergyMap.get(entityLiving) + k1);
//              }
//              else {
//                entityLivingToShockEnergyMap.put(entityLiving, k1);
//              }
//            }
//            if (conducted >= energypath1.minInsulationBreakdownEnergy) {
//              for (IEnergyConductor iEnergyConductor : energypath1.conductors) {
//                if (conducted >= iEnergyConductor.getInsulationBreakdownEnergy()) {
//                  iEnergyConductor.removeInsulation();
//                  if (iEnergyConductor.getInsulationEnergyAbsorption() < energypath1.minInsulationEnergyAbsorption) {
//                    energypath1.minInsulationEnergyAbsorption = iEnergyConductor.getInsulationEnergyAbsorption();
//                  }
//                }
//              }
//            }
//          }
/*        } while (conducted < energypath1.minConductorBreakdownEnergy);
        for (IEnergyConductor iEnergyConductor : energypath1.conductors) {
          if (conducted >= iEnergyConductor.getConductorBreakdownEnergy()) {
            iEnergyConductor.removeConductor();
          }
        }
      }
    }*/
  }

  public long getTotalEnergyConducted(TileEntity tileentity) {
 /*   long l = 0L;
    if (tileentity instanceof IEnergyConductor || tileentity instanceof IEnergySink) {
      List list = discover(tileentity, true, Integer.MAX_VALUE);
      Iterator iterator1 = list.iterator();
  
      label56:
      while (true) {
        EnergyPath energypath1;
        IEnergySource ienergysource;
        do {
          do {
            if (!iterator1.hasNext()) {
              break label56;
            }
  
            energypath1 = (EnergyPath) iterator1.next();
            ienergysource = (IEnergySource) energypath1.target;
          } while (!energySourceToEnergyPathMap.containsKey(ienergysource));
        } while ((double) ienergysource.getMaxEnergyOutput() <= energypath1.loss);
  
        Iterator iterator2 = ((List) energySourceToEnergyPathMap.get(ienergysource)).iterator();
  
        while (true) {
          EnergyPath energypath2;
          do {
            if (!iterator2.hasNext()) {
              continue label56;
            }
      
            energypath2 = (EnergyPath) iterator2.next();
          } while ((!(tileentity instanceof IEnergySink) || energypath2.target != tileentity) &&
              (!(tileentity instanceof IEnergyConductor) || !energypath2.conductors.contains(tileentity)));
    
          l += energypath2.totalEnergyConducted;
        }
      }
    }
    
    EnergyPath energypath;
    if (tileentity instanceof IEnergySource && energySourceToEnergyPathMap.containsKey(tileentity)) {
      for (Iterator iterator = ((List) energySourceToEnergyPathMap.get(tileentity)).iterator(); iterator.hasNext();
            l += energypath.totalEnergyConducted) {
        energypath = (EnergyPath) iterator.next();
      }
    }
  
    return l;
    */
    return 69;
  }
  
  /*private List<EnergyPath> discover(TileEntity tileEntity, boolean flag, int i) {
    //newDiscover(tileEntity, flag, i); // Todo: Remove this
    HashMap<TileEntity, EnergyBlockLink> tileEntityEnergyBlockLinkHashMap = new HashMap<>();
    LinkedList<TileEntity> tileEntityLinkedList = new LinkedList<>();
    tileEntityLinkedList.add(tileEntity);
    
    discover_1:
    while (true) {
      TileEntity tileEntity1;
      do {
        if (tileEntityLinkedList.isEmpty()) {
          LinkedList<EnergyPath> energyPaths = new LinkedList<>();
          Iterator<Entry<TileEntity, EnergyBlockLink>> tileEntityEnergyBlockLinkIterator =
                tileEntityEnergyBlockLinkHashMap.entrySet().iterator();
          
          while (true) {
            discover_2:
            while (true) {
              Entry<TileEntity, EnergyBlockLink> tileEntityEnergyBlockLinkEntry;
              TileEntity energyPathTarget;
              do {
                if (!tileEntityEnergyBlockLinkIterator.hasNext()) {
                  return energyPaths;
                }
                
                tileEntityEnergyBlockLinkEntry = tileEntityEnergyBlockLinkIterator.next();
                energyPathTarget = tileEntityEnergyBlockLinkEntry.getKey();
              } while ((flag || !(energyPathTarget instanceof IEnergySink)) &&
                    (!flag || !(energyPathTarget instanceof IEnergySource)));
              
              EnergyBlockLink energyBlockLink = tileEntityEnergyBlockLinkEntry.getValue();
              EnergyPath energyPath = new EnergyPath();
              energyPath.loss = Math.max(energyBlockLink.loss, 0.1D);
              
              energyPath.target = energyPathTarget;
              energyPath.targetDirection = energyBlockLink.direction;
              
              if (!flag && tileEntity instanceof IEnergySource) {
                while (true) {
                  assert energyBlockLink != null;
                  energyPathTarget = energyBlockLink.direction.applyToTileEntity(energyPathTarget);
                  if (energyPathTarget == tileEntity) {
                    break;
                  }
                  
                  if (!(energyPathTarget instanceof IEnergyConductor)) {
                    if (energyPathTarget != null) {
                      System.out.println("EnergyNet: EnergyBlockLink corrupted (" + energyPath.target + " [" +
                            energyPath.target.x + " " + energyPath.target.y + " " + energyPath.target.z + "] -> " +
                            energyPathTarget + " [" + energyPathTarget.x + " " + energyPathTarget.y + " " +
                            energyPathTarget.z + "] -> " + tileEntity + " [" + tileEntity.x + " " + tileEntity.y +
                            " " + tileEntity.z + "])");
                    }
                    continue discover_2;
                  }
                  
                  IEnergyConductor iEnergyConductor = (IEnergyConductor) energyPathTarget;
                  
                  energyPath.minX = Math.min(energyPath.minX, energyPathTarget.x);
                  energyPath.minY = Math.min(energyPath.minY, energyPathTarget.y);
                  energyPath.minZ = Math.min(energyPath.minZ, energyPathTarget.z);
                  energyPath.maxX = Math.max(energyPath.maxX, energyPathTarget.x);
                  energyPath.maxY = Math.max(energyPath.maxY, energyPathTarget.y);
                  energyPath.maxZ = Math.max(energyPath.maxZ, energyPathTarget.z);


                  energyPath.conductors.add(iEnergyConductor);
                  
                  energyPath.minInsulationEnergyAbsorption = Math.min(iEnergyConductor.getInsulationEnergyAbsorption(),
                        energyPath.minInsulationEnergyAbsorption);
                  energyPath.minInsulationBreakdownEnergy = Math.min(iEnergyConductor.getInsulationBreakdownEnergy(),

        energyPath.minInsulationBreakdownEnergy);
                  energyPath.minConductorBreakdownEnergy = Math.min(iEnergyConductor.getConductorBreakdownEnergy(),
                        energyPath.minConductorBreakdownEnergy);


                  energyBlockLink = tileEntityEnergyBlockLinkHashMap.get(energyPathTarget);
                  if (energyBlockLink == null) {
                    Platform.displayError("An energy network pathfinding entry is corrupted.\nThis could happen " +
                          "due to incorrect Minecraft behavior or a bug.\n\n(Technical information: energyBlockLink, " +
                          "tile entities below)\nE: " + tileEntity + " (" + tileEntity.x + "," + tileEntity.y + "," +
                          tileEntity.z + ")\n" + "C: " + energyPathTarget + " (" + energyPathTarget.x + "," +
                          energyPathTarget.y + "," + energyPathTarget.z + ")\n" + "R: " + energyPath.target +
                          " (" + energyPath.target.x + "," + energyPath.target.y + "," + energyPath.target.z + ")");
                  }
                }
              }
              
              energyPaths.add(energyPath);
            }
          }
        }
        
        tileEntity1 = tileEntityLinkedList.remove();
      } while (tileEntity1.l());
      
      double energyBlockLinkLoss = 0.0D;
      if (tileEntity1 != tileEntity) {
        energyBlockLinkLoss = tileEntityEnergyBlockLinkHashMap.get(tileEntity1).loss;
      }
      
      List<EnergyNet.EnergyTarget> energyTargetList = getValidReceivers(tileEntity1, flag);
      Iterator<EnergyNet.EnergyTarget> energyTargetIterator = energyTargetList.iterator();
      
      while (true) {
        EnergyTarget energyTarget;
        double energyConductorConductionLoss;
        do { // ... while tileEntityEnergyBlockLinkHashMap contains energyTarget.tileEntity AND
          //            tileEntityEnergyBlockLinkHashMap.get(energyTarget.tileEntity).loss
          //                <= energyBlockLinkLoss + energyConductorConductionLoss
          
          do { // ... while energyBlockLinkLoss + energyConductorConductionLoss >= 1
            
            do { // ... while energyTarget.tileEntity == tileEntity (from method args)
              if (!energyTargetIterator.hasNext()) {
                continue discover_1;
              }
              energyTarget = energyTargetIterator.next();
            } while (energyTarget.tileEntity == tileEntity);
            
            energyConductorConductionLoss = 0.0D;
            if (!(energyTarget.tileEntity instanceof IEnergyConductor)) {
              break;
            }
            energyConductorConductionLoss = ((IEnergyConductor) energyTarget.tileEntity).getConductionLoss();
            energyConductorConductionLoss = Math.max(energyConductorConductionLoss, 1.0E-4D);
          } while (energyBlockLinkLoss + energyConductorConductionLoss >= i);
          
        } while (tileEntityEnergyBlockLinkHashMap.containsKey(energyTarget.tileEntity) &&
              tileEntityEnergyBlockLinkHashMap.get(energyTarget.tileEntity).loss
                    <= energyBlockLinkLoss + energyConductorConductionLoss);
        
        tileEntityEnergyBlockLinkHashMap.put(energyTarget.tileEntity,
              new EnergyBlockLink(energyTarget.direction, energyBlockLinkLoss + energyConductorConductionLoss));
        
        if (energyTarget.tileEntity instanceof IEnergyConductor) {
          tileEntityLinkedList.remove(energyTarget.tileEntity);
          tileEntityLinkedList.add(energyTarget.tileEntity);
        }
      }
    }
  }*/
  
  /*private LinkedList<EnergyTarget> getValidReceivers(TileEntity tileentity, boolean flag) {
    LinkedList<EnergyTarget> linkedlist = new LinkedList<>();
    Direction[] adirection = Direction.values();
    int i = adirection.length;
    
    for (int j = 0; j < i; ++j) {
      Direction direction = adirection[j];
      TileEntity tileentity1 = direction.applyToTileEntity(tileentity);
      if (tileentity1 instanceof IEnergyTile && ((IEnergyTile) tileentity1).isAddedToEnergyNet()) {
        Direction direction1 = direction.getInverse();
        if ((!flag && tileentity instanceof IEnergyEmitter &&
            ((IEnergyEmitter) tileentity).emitsEnergyTo(tileentity1, direction) ||
            flag && tileentity instanceof IEnergyAcceptor &&
                ((IEnergyAcceptor) tileentity).acceptsEnergyFrom(tileentity1, direction)) &&
            (!flag && tileentity1 instanceof IEnergyAcceptor &&
                ((IEnergyAcceptor) tileentity1).acceptsEnergyFrom(tileentity, direction1) ||
                flag && tileentity1 instanceof IEnergyEmitter &&
                    ((IEnergyEmitter) tileentity1).emitsEnergyTo(tileentity, direction1))) {
          linkedlist.add(new EnergyTarget(tileentity1, direction1));
        }
      }
    }

    return linkedlist;
  }*/

  
  /*static class EnergyBlockLink {
    Direction direction;
    double loss;
    Direction.
    EnergyBlockLink(Direction direction1, double d) {
      direction = direction1;
      loss = d;
    }
  }
  
  static class EnergyPath {
    TileEntity target = null;
    Direction targetDirection;
    Set<IEnergyConductor> conductors = new HashSet<>();
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    double loss = 0.0D;
    int minInsulationEnergyAbsorption = Integer.MAX_VALUE;
    int minInsulationBreakdownEnergy = Integer.MAX_VALUE;
    int minConductorBreakdownEnergy = Integer.MAX_VALUE;
    long totalEnergyConducted = 0L;
    
    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof EnergyPath)) {
        return false;
      }
      EnergyPath ep = (EnergyPath) o;
      return ep.target.equals(target) && ep.targetDirection.equals(targetDirection) && ep.minX == minX &&
          ep.minY == minY
          && ep.minZ == minZ && ep.maxX == maxX && ep.maxY == maxY && ep.maxZ == maxZ;
    }
  }
  
  static class EnergyTarget {
    TileEntity tileEntity;
    Direction direction;
    
    EnergyTarget(TileEntity tileentity, Direction direction1) {
      tileEntity = tileentity;
      direction = direction1;
    }
  }*/
}
