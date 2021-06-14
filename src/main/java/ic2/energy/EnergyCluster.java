package ic2.energy;

import net.minecraft.server.TileEntity;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A network of connected IEnergyTiles
 */
public class EnergyCluster {

	private final List<WeakReference<TileEntity>> tiles;

	public EnergyCluster() {
		this.tiles = new LinkedList<>();
		Logger.getAnonymousLogger().info("New energy cluster");
	}

	public EnergyCluster(TileEntity tile) {
		this();
		addTile(tile);
	}

	public void addTile(TileEntity tile) {
		tiles.add(new WeakReference<>(tile));
	}

	public void removeTile(TileEntity tile) {

		// If tile wasn't removed, return
		if (!tiles.removeIf(ref -> ref.get() == null || Objects.equals(ref.get(), tile))) {
			return;
		}

		// Check if the cluster has been split by checking the amount of connections to the removed tile
		Stream<WeakReference<TileEntity>> stream = tiles.stream().filter(ref -> areTilesAdjacent(tile, ref.get()));

		if (stream.count() > 1) { // more than 1 adjacent connection
			Logger.getAnonymousLogger().info("TODO split cluster into one or more smaller ones");
		}
	}

	public List<TileEntity> stealTiles() {
		List<TileEntity> results = tiles.stream()
				.filter(ref -> ref.get() != null)
				.map(Reference::get)
				.collect(Collectors.toList());

		tiles.clear();

		return results;
	}

	public void emitEnergyFrom(TileEntity tile, int energyAmount) {
		Logger.getAnonymousLogger().info("TODO emit energy");
	}

	public boolean containsTile(TileEntity tile) {
		return tiles.stream().anyMatch(ref -> {
			TileEntity tile2 = ref.get();

			// If ref no longer exists
			if (tile2 == null) {
				tiles.remove(ref);
				return false;
			}

			return tile == tile2;
		});
	}

	public boolean containsTilesAdjacentTo(TileEntity tile1) {
		return tiles.stream().anyMatch(ref -> {
			TileEntity tile2 = ref.get();

			// If ref no longer exists
			if (tile2 == null) {
				tiles.remove(ref);
				return false;
			}

			return areTilesAdjacent(tile1, tile2);
		});
	}

	public long tileAmount() {
		return tiles.size();
	}

	public static boolean areTilesAdjacent(TileEntity tile1, TileEntity tile2) {
		// Add the XYZ differences between tile1 and tile2 to get the total distance
		// If distance <= 1, the two are adjacent to eachother
		return Math.abs(tile1.x - tile2.x)
				+ Math.abs(tile1.y - tile2.y)
				+ Math.abs(tile1.z - tile2.z)
				<= 1;
	}
}
