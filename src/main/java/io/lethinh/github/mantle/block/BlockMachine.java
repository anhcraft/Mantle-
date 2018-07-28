package io.lethinh.github.mantle.block;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.NumberConversions;

import io.lethinh.github.mantle.Mantle;
import io.lethinh.github.mantle.block.impl.BlockBlockBreaker;
import io.lethinh.github.mantle.block.impl.BlockBlockPlacer;
import io.lethinh.github.mantle.block.impl.BlockMobMagnet;
import io.lethinh.github.mantle.block.impl.BlockTreeCutter;
import io.lethinh.github.mantle.nbt.Constants;
import io.lethinh.github.mantle.nbt.NBTHelper;
import io.lethinh.github.mantle.nbt.NBTTagCompound;
import io.lethinh.github.mantle.nbt.NBTTagList;
import io.lethinh.github.mantle.utils.Utils;

/**
 * Created by Le Thinh
 */
public abstract class BlockMachine {

	/* Static Members */
	public static final CopyOnWriteArrayList<BlockMachine> MACHINES = new CopyOnWriteArrayList<>();
	public static final Properties PROPERTIES = new Properties();
	protected static final long DEFAULT_DELAY = 20L, DEFAULT_PERIOD = 10L;
	private static boolean legacyConfig = false;

	/* Instance Members */
	public final Block block;
	public Inventory inventory;
	public final BukkitRunnable runnable;
	public List<String> accessiblePlayers;

	/* Default constructor */
	public BlockMachine(Block block, int invSlots, String invName, String... players) {
		this(block, Bukkit.createInventory(null, invSlots, invName), players);
	}

	public BlockMachine(Block block, Inventory inventory, String... players) {
		this.block = block;

		this.accessiblePlayers = new ArrayList<>();

		for (String player : players) {
			accessiblePlayers.add(player);
		}

		this.inventory = inventory;
		this.runnable = new BukkitRunnable() {
			@Override
			public void run() {
				if (!canWork()) {
					return;
				}

				work();
			}
		};
	}

	/* Tick */
	private boolean stoppedTick = false;

	public void setStoppedTick(boolean stoppedTick) {
		this.stoppedTick = stoppedTick;

		if (runnable != null) {
			if (stoppedTick) {
				runnable.cancel();
			} else {
				handleUpdate(Mantle.instance);
			}
		}
	}

	public boolean isStoppedTick() {
		return stoppedTick;
	}

	public void dropItems(ItemStack stack) {
		World world = block.getWorld();
		Location location = block.getLocation();
		world.dropItemNaturally(location, stack);

		if (Utils.isNotEmpty(inventory) && getRealSlots() > 0) {
			for (int i = 0; i < getRealSlots(); ++i) {
				ItemStack invDrop = inventory.getItem(i);

				if (invDrop == null || invDrop.getAmount() == 0) {
					continue;
				}

				world.dropItemNaturally(location, invDrop);
			}
		}
	}

	public int getRealSlots() {
		return 27;
	}

	/**
	 * Run {@code runnable} synchronously or asynchronously
	 *
	 * @param plugin
	 */
	public abstract void handleUpdate(Mantle plugin);

	public abstract void work();

	public boolean canWork() {
		return !stoppedTick;
	}

	public boolean canPlace(Player player) {
		// return player.getName().equals("Nesfan") ||
		// player.hasPermission(Mantle.PLUGIN_ID + ".place." + inventory.getName());
		return true;
	}

	public boolean canOpen(Player player) {
		if (inventory == null || inventory.getSize() == 0) {
			return false;
		}

		if (accessiblePlayers.isEmpty()) {
			return accessiblePlayers.add(player.getName());
		}

		return accessiblePlayers.stream().anyMatch(p -> p.equals(player.getName()));
	}

	public String getName() {
		return inventory.getTitle().replace(' ', '_');
	}

	/* Object */
	@Override
	public boolean equals(Object obj) {
		return obj instanceof BlockMachine && block.getLocation().equals(((BlockMachine) obj).block.getLocation());
	}

	@Override
	public int hashCode() {
		return block.getLocation().hashCode();
	}

	/* NBT */
	public NBTTagCompound writeToNBT() {
		NBTTagCompound nbt = new NBTTagCompound();

		nbt.setTag("Inventory", Utils.serializeInventory(inventory));
		nbt.setBoolean("StoppedTick", isStoppedTick());

		nbt.setInteger("AllowedSize", accessiblePlayers.size());
		NBTTagList playerTags = new NBTTagList();

		for (int i = 0; i < accessiblePlayers.size(); ++i) {
			NBTTagCompound playerTag = new NBTTagCompound();
			playerTag.setInteger("Index", i);
			playerTag.setString("Name", accessiblePlayers.get(i));
			playerTags.appendTag(playerTag);
		}

		nbt.setTag("AllowedPlayers", playerTags);

		return nbt;
	}

	public void readFromNBT(NBTTagCompound nbt) {
		if (legacyConfig) {
			if (nbt.hasKey("Inventory")) {
				Inventory to = Utils.deserializeInventory(nbt.getCompoundTag("Inventory"));

				if (to != null) {
					inventory = to;
				}
			}

			if (nbt.hasKey("StoppedTick")) {
				setStoppedTick(nbt.getBoolean("StoppedTick"));
			}

			if (nbt.hasKey("AllowedSize") && nbt.hasKey("AllowedPlayers")) {
				int allowedSize = nbt.getInteger("AllowedSize");
				accessiblePlayers = new ArrayList<>(allowedSize);
				NBTTagList playerTags = nbt.getTagList("AllowedPlayers", Constants.NBT.TAG_COMPOUND);

				for (int i = 0; i < playerTags.tagCount(); ++i) {
					NBTTagCompound playerTag = playerTags.getCompoundTagAt(i);
					int index = playerTag.getInteger("Index");
					String name = playerTag.getString("Name");

					if (index >= 0 && index < allowedSize) {
						accessiblePlayers.add(index, name);
					}
				}
			}
		} else {
			Inventory to = Utils.deserializeInventory(nbt.getCompoundTag("Inventory"));

			if (to != null) {
				inventory = to;
			}

			setStoppedTick(nbt.getBoolean("StoppedTick"));

			int allowedSize = nbt.getInteger("AllowedSize");
			accessiblePlayers = new ArrayList<>(allowedSize);
			NBTTagList playerTags = nbt.getTagList("AllowedPlayers", Constants.NBT.TAG_COMPOUND);

			for (int i = 0; i < playerTags.tagCount(); ++i) {
				NBTTagCompound playerTag = playerTags.getCompoundTagAt(i);
				int index = playerTag.getInteger("Index");
				String name = playerTag.getString("Name");

				if (index >= 0 && index < allowedSize) {
					accessiblePlayers.add(index, name);
				}
			}
		}
	}

	/* I/O */
	public static void saveMachinesInventoriesData() throws IOException {
		if (MACHINES.isEmpty()) {
			return;
		}

		File dir = new File(Mantle.instance.getDataFolder(), "MachinesInventories");

		if (!dir.exists()) {
			dir.mkdirs();
		}

		for (File file : dir.listFiles()) {
			file.delete();
		}

		for (BlockMachine save : MACHINES) {
			Location location = save.block.getLocation();
			File out = new File(dir, Utils.serializeLocation(location));
			NBTHelper.safeWrite(save.writeToNBT(), out);
		}
	}

	public static void loadMachinesInventoriesData() throws IOException {
		File dir = new File(Mantle.instance.getDataFolder(), "MachinesInventories");

		if (!dir.exists()) {
			dir.mkdirs();
			return;
		}

		File[] files = dir.listFiles();

		if (files.length == 0) {
			return;
		}

		for (File file : files) {
			Location location = Utils.deserializeLocation(file.getName());

			for (BlockMachine machine : MACHINES) {
				if (!machine.block.getLocation().equals(location)) {
					continue;
				}

				machine.readFromNBT(NBTHelper.read(file));
			}
		}
	}

	public static void saveMachinesData() throws IOException {
		File dir = Mantle.instance.getDataFolder();

		if (!dir.exists()) {
			dir.mkdirs();
		}

		PROPERTIES.clear();
		PROPERTIES.setProperty("ConfigVersion", Mantle.VERSION);

		for (BlockMachine machine : MACHINES) {
			Location location = machine.block.getLocation();
			PROPERTIES.setProperty(Utils.serializeLocation(location), machine.getName());
		}

		OutputStream out = new FileOutputStream(new File(dir, "machines_data.txt"));
		PROPERTIES.store(out, null);
	}

	public static void loadMachinesData() throws IOException {
		File file = new File(Mantle.instance.getDataFolder(), "machines_data.txt");

		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}

		if (!file.exists()) {
			file.createNewFile();
			return;
		}

		InputStream in = new FileInputStream(file);

		PROPERTIES.load(in);

		if (PROPERTIES.isEmpty()) {
			return;
		}

		Logger logger = Mantle.instance.getLogger();

		if (StringUtils.isBlank(PROPERTIES.getProperty("ConfigVersion"))
				|| !PROPERTIES.getProperty("ConfigVersion").equals(Mantle.VERSION)) {
			logger.warning(
					"Your machines data are detected to be in previous version, there may be some changes in new version!");
			legacyConfig = true;
		}

		for (Entry<Object, Object> entry : PROPERTIES.entrySet()) {
			String loc = (String) entry.getKey();
			String type = (String) entry.getValue();

			if (StringUtils.isBlank(loc) || StringUtils.isBlank(type)) {
				continue;
			}

			Location location = Utils.deserializeLocation(loc);

			if (legacyConfig && location == null) {
				try {
					String[] split = loc.split("_");
					location = new Location(Bukkit.getWorld(split[0]), NumberConversions.toInt(split[1]),
							NumberConversions.toInt(split[2]), NumberConversions.toInt(split[3]));
				} catch (Throwable t) {
					logger.warning(loc + " was failed to deserialize!");
				}
			}

			if (location == null) {
				continue;
			}

			Block block = location.getBlock();

			if (block == null || block.isEmpty()) {
				continue;
			}

			BlockMachine machine = null;

			switch (type.toLowerCase()) {
			case "tree_cutter":
				BlockMachine.MACHINES.add(machine = new BlockTreeCutter(block));
				break;
//			case "planter":
//				BlockMachine.MACHINES.add(machine = new BlockPlanter(block));
//				break;
			case "block_breaker":
				BlockMachine.MACHINES.add(machine = new BlockBlockBreaker(block));
				break;
			case "block_placer":
				BlockMachine.MACHINES.add(machine = new BlockBlockPlacer(block));
				break;
			case "mob_magnet":
				BlockMachine.MACHINES.add(machine = new BlockMobMagnet(block));
				break;
			}

			if (machine != null && machine instanceof Listener) {
				Bukkit.getServer().getPluginManager().registerEvents((Listener) machine, Mantle.instance);
			}
		}
	}

}
