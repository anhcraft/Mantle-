package io.lethinh.github.mantle.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.NumberConversions;

import io.lethinh.github.mantle.Mantle;
import io.lethinh.github.mantle.nbt.Constants;
import io.lethinh.github.mantle.nbt.NBTTagCompound;
import io.lethinh.github.mantle.nbt.NBTTagList;

/**
 * Created by Le Thinh
 */
public final class Utils {

	private Utils() {

	}

	/* Internal */
	public static NamespacedKey prefixNamespaced(String key) {
		return new NamespacedKey(Mantle.instance, key);
	}

	/* Inventory */
	public static boolean isFull(Inventory inventory) {
		return IntStream.range(0, inventory.getSize())
				.allMatch(i -> inventory.getItem(i) != null && inventory.getItem(i).getAmount() == 64);
	}

	public static boolean isNotEmpty(Inventory inventory) {
		return IntStream.range(0, inventory.getSize()).anyMatch(i -> inventory.getItem(i) != null);
	}

	@SuppressWarnings("deprecation")
	public static NBTTagCompound writeNBTToStack(ItemStack stack, NBTTagCompound nbt) {
		nbt.setInteger("id", stack.getTypeId());
		nbt.setByte("Amount", (byte) stack.getAmount());
		nbt.setShort("Damage", stack.getDurability());

		NBTTagCompound stackTags = new NBTTagCompound();
		ItemMeta itemMeta = stack.getItemMeta();

		if (itemMeta.hasLocalizedName()) {
			stackTags.setString("localized", itemMeta.getLocalizedName());
		}

		if (itemMeta.hasDisplayName()) {
			stackTags.setString("display", itemMeta.getDisplayName());
		}

		if (itemMeta.hasLore()) {
			stackTags.setString("lore", itemMeta.getLore().stream().collect(Collectors.joining(",")));
		}

		if (itemMeta.hasEnchants()) {
			NBTTagList enchantments = new NBTTagList();

			for (Entry<Enchantment, Integer> ench : itemMeta.getEnchants().entrySet()) {
				NBTTagCompound enchTag = new NBTTagCompound();
				enchTag.setInteger("id", ench.getKey().getId());
				enchTag.setInteger("level", ench.getValue());
				enchantments.appendTag(enchTag);
			}

			stackTags.setTag("enchants", enchantments);
		}

		nbt.setTag("tag", stackTags);
		return nbt;
	}

	public static ItemStack readStackFromNBT(NBTTagCompound nbt) {
		@SuppressWarnings("deprecation")
		Material material = Material.getMaterial(nbt.getInteger("id"));
		int amount = nbt.getInteger("Amount");
		short damage = nbt.getShort("Damage");
		ItemStack ret = new ItemStack(material, amount, damage);

		if (nbt.hasKey("tag", Constants.NBT.TAG_COMPOUND)) {
			NBTTagCompound stackTags = nbt.getCompoundTag("tag");

			if (stackTags.hasNoTags()) {
				return ret;
			}

			ItemMeta meta = ret.getItemMeta();

			if (stackTags.hasKey("localized", Constants.NBT.TAG_STRING)) {
				meta.setLocalizedName(stackTags.getString("localized"));
			}

			if (stackTags.hasKey("display", Constants.NBT.TAG_STRING)) {
				meta.setDisplayName(stackTags.getString("display"));
			}

			if (stackTags.hasKey("lore", Constants.NBT.TAG_STRING)) {
				List<String> lore = Arrays.asList(stackTags.getString("lore").split(","));
				meta.setLore(lore);
			}

			ret.setItemMeta(meta);
		}

		return ret;
	}

	/* Serialize & Deserialize */
	public static NBTTagCompound serializeInventory(Inventory inventory) {
		NBTTagList nbtTagList = new NBTTagList();

		for (int i = 0; i < inventory.getSize(); ++i) {
			ItemStack stack = inventory.getItem(i);

			if (stack != null) {
				NBTTagCompound itemTag = new NBTTagCompound();
				itemTag.setInteger("Slot", i);
				writeNBTToStack(stack, itemTag);
				nbtTagList.appendTag(itemTag);
			}
		}

		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setTag("Items", nbtTagList);
		nbt.setInteger("Size", inventory.getSize());
		nbt.setString("Title", inventory.getTitle());
		return nbt;
	}

	public static Inventory deserializeInventory(NBTTagCompound nbt) {
		try {
			Inventory inventory = Bukkit.createInventory(null, nbt.getInteger("Size"), nbt.getString("Title"));
			NBTTagList tagList = nbt.getTagList("Items", Constants.NBT.TAG_COMPOUND);

			for (int i = 0; i < tagList.tagCount(); ++i) {
				NBTTagCompound itemTags = tagList.getCompoundTagAt(i);
				int slot = itemTags.getInteger("Slot");

				if (slot >= 0 && slot < inventory.getSize()) {
					inventory.setItem(slot, readStackFromNBT(itemTags));
				}
			}

			return inventory;
		} catch (Throwable t) {
			return null;
		}
	}

	public static String serializeLocation(Location location) {
		return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + ","
				+ location.getBlockZ();
	}

	public static Location deserializeLocation(String serialization) {
		try {
			String[] split = serialization.split(",");

			if (split.length == 0) {
				return null;
			}

			return new Location(Bukkit.getWorld(split[0]), NumberConversions.toInt(split[1]),
					NumberConversions.toInt(split[2]), NumberConversions.toInt(split[3]));
		} catch (Throwable t) {
			return null;
		}
	}

	/* World */
	public static BlockFace getBlockFaceFromPlayer(Location loc, Player player) {
		Location playerLoc = player.getLocation();

		// Check for vertical faces
		if (Math.abs(playerLoc.getX() - (loc.getX() + 0.5D)) < 2D
				&& Math.abs(playerLoc.getZ() - (loc.getZ() + 0.5D)) < 2D) {
			double eyesPos = playerLoc.getY() + player.getEyeHeight();

			if (eyesPos - loc.getY() > 2D) {
				return BlockFace.UP;
			}

			if (loc.getY() - eyesPos > 0D) {
				return BlockFace.DOWN;
			}
		}

		// Or horizontal faces
		BlockFace[] horizontals = new BlockFace[] { BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST };
		int index = NumberConversions.floor(playerLoc.getYaw() * 4F / 360F + 0.5D) & 3;
		return horizontals[Math.abs(index % horizontals.length)].getOppositeFace();
	}

	@SuppressWarnings("deprecation")
	public static boolean isBlockEqualStack(Block block, ItemStack stack) {
		return block != null && !block.isEmpty() && stack != null && stack.getAmount() > 0
				&& block.getType() == stack.getType() && block.getData() == stack.getData().getData();
	}

	public static boolean isGrowable(Material material) {
		return material == Material.SOIL || material == Material.CROPS || material == Material.SEEDS
				|| material == Material.CARROT || material == Material.BEETROOT_BLOCK || material == Material.MELON_STEM
				|| material == Material.BEETROOT_BLOCK || material == Material.MELON_STEM
				|| material == Material.PUMPKIN_STEM;
	}

	public static boolean areStacksEqualIgnoreDurabilityAndAmount(ItemStack stackA, ItemStack stackB) {
		return stackA != null && stackB != null && stackA.getType() == stackB.getType() && stackA.hasItemMeta()
				&& stackB.hasItemMeta() && Bukkit.getItemFactory().equals(stackA.getItemMeta(), stackB.getItemMeta());
	}

	public static String getColoredString(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}

	public static BlockFace[] getMainFaces() {
		return new BlockFace[] { BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST,
				BlockFace.EAST };
	}

	public static Location offsetLocation(Location location, BlockFace face) {
		return location.add(face.getModX(), face.getModY(), face.getModZ());
	}

	public static BlockFace getEntityDirection(Location location) {
		BlockFace dir = BlockFace.NORTH;
		float f = Float.MIN_VALUE;

		for (BlockFace face : getMainFaces()) {
			float f1 = (float) (location.getX() * face.getModX() + location.getY() * face.getModY()
					+ location.getZ() * face.getModZ());

			if (f1 > f) {
				f = f1;
				dir = face;
			}
		}

		return dir;
	}

}
