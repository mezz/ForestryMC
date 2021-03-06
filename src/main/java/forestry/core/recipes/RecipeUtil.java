/*******************************************************************************
 * Copyright (c) 2011-2014 SirSengir.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Various Contributors including, but not limited to:
 * SirSengir (original work), CovertJaguar, Player, Binnie, MysteriousAges
 ******************************************************************************/
package forestry.core.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import cpw.mods.fml.common.registry.GameRegistry;

import forestry.api.recipes.IDescriptiveRecipe;
import forestry.api.recipes.RecipeManagers;
import forestry.core.config.ForestryBlock;
import forestry.core.config.ForestryItem;
import forestry.core.fluids.Fluids;
import forestry.core.gui.ContainerDummy;
import forestry.core.utils.ItemStackUtil;
import forestry.core.utils.Log;

public abstract class RecipeUtil {

	private static final Container DUMMY_CONTAINER = new ContainerDummy();

	public static void addFermenterRecipes(ItemStack resource, int fermentationValue, Fluids output) {
		if (RecipeManagers.fermenterManager == null) {
			return;
		}

		RecipeManagers.fermenterManager.addRecipe(resource, fermentationValue, 1.0f, output.getFluid(1), Fluids.WATER.getFluid(1));

		if (FluidRegistry.isFluidRegistered(Fluids.JUICE.getFluid())) {
			RecipeManagers.fermenterManager.addRecipe(resource, fermentationValue, 1.5f, output.getFluid(1), Fluids.JUICE.getFluid(1));
		}

		if (FluidRegistry.isFluidRegistered(Fluids.HONEY.getFluid())) {
			RecipeManagers.fermenterManager.addRecipe(resource, fermentationValue, 1.5f, output.getFluid(1), Fluids.HONEY.getFluid(1));
		}
	}

	/**
	 * Returns a list of the ore dictionary names if they exist.
	 * Returns a list containing itemStack if there are no ore dictionary names.
	 * Used for creating recipes that should accept equivalent itemStacks, based on the ore dictionary.
	 */
	public static List getOreDictRecipeEquivalents(ItemStack itemStack) {
		int[] oreDictIds = OreDictionary.getOreIDs(itemStack);
		List<String> oreDictNames = new ArrayList<>(oreDictIds.length);
		for (int oreId : oreDictIds) {
			String oreDictName = OreDictionary.getOreName(oreId);
			oreDictNames.add(oreDictName);
		}
		if (oreDictNames.isEmpty()) {
			return Collections.singletonList(itemStack);
		}
		return oreDictNames;
	}

	public static NBTTagCompound getCraftingNbt(IInventory inventoryCrafting) {
		NBTTagCompound craftingNbt = null;
		for (int i = 0; i < inventoryCrafting.getSizeInventory(); i++) {
			ItemStack stackInSlot = inventoryCrafting.getStackInSlot(i);
			if (stackInSlot == null || !stackInSlot.hasTagCompound()) {
				continue;
			}

			NBTTagCompound tagCompound = stackInSlot.getTagCompound();
			// if there are multiple NBT they must all match
			if (craftingNbt != null && !craftingNbt.equals(tagCompound)) {
				return null;
			} else {
				craftingNbt = tagCompound;
			}
		}

		return craftingNbt;
	}

	public static Object[] getCraftingRecipeAsArray(Object rec) {

		try {

			if (rec instanceof IDescriptiveRecipe) {

				IDescriptiveRecipe recipe = (IDescriptiveRecipe) rec;
				return getShapedRecipeAsArray(recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(), recipe.getRecipeOutput());

			}

		} catch (Exception ex) {
			Log.warning("Exception while trying to parse an ItemStack[10] from an IRecipe:");
			Log.warning(ex.getMessage());
		}

		return null;
	}

	private static Object[] getShapedRecipeAsArray(int width, int height, Object[] ingredients, ItemStack output) {
		Object[] result = new Object[10];

		for (int y = 0; y < height; y++) {
			System.arraycopy(ingredients, y * width, result, y * 3, width);
		}

		result[9] = output;
		return result;
	}

	public static boolean canCraftRecipe(World world, ItemStack[] recipeItems, ItemStack recipeOutput, ItemStack[] availableItems) {
		// Need at least one matched set
		if (ItemStackUtil.containsSets(recipeItems, availableItems, true, true) == 0) {
			return false;
		}

		// Check that it doesn't make a different recipe.
		// For example:
		// Wood Logs are all ore dictionary equivalent with each other,
		// but an Oak Log shouldn't be able to make Ebony Wood Planks
		// because it makes Oak Wood Planks using the same recipe.
		// Strategy:
		// Create a fake crafting inventory using items we have in availableItems
		// in place of the ones in the saved crafting inventory.
		// Check that the recipe it makes is the same as the currentRecipe.
		InventoryCrafting crafting = new InventoryCrafting(DUMMY_CONTAINER, 3, 3);
		ItemStack[] stockCopy = ItemStackUtil.condenseStacks(availableItems);

		for (int slot = 0; slot < recipeItems.length; slot++) {
			ItemStack recipeStack = recipeItems[slot];
			if (recipeStack == null) {
				continue;
			}

			// Use crafting equivalent (not oredict) items first
			for (ItemStack stockStack : stockCopy) {
				if (stockStack.stackSize > 0 && ItemStackUtil.isCraftingEquivalent(recipeStack, stockStack, false, false)) {
					ItemStack stack = ItemStackUtil.createSplitStack(stockStack, 1);
					stockStack.stackSize--;
					crafting.setInventorySlotContents(slot, stack);
					break;
				}
			}

			// Use oredict items if crafting equivalent items aren't available
			if (crafting.getStackInSlot(slot) == null) {
				for (ItemStack stockStack : stockCopy) {
					if (stockStack.stackSize > 0 && ItemStackUtil.isCraftingEquivalent(recipeStack, stockStack, true, true)) {
						ItemStack stack = ItemStackUtil.createSplitStack(stockStack, 1);
						stockStack.stackSize--;
						crafting.setInventorySlotContents(slot, stack);
						break;
					}
				}
			}
		}
		ItemStack output = CraftingManager.getInstance().findMatchingRecipe(crafting, world);

		return ItemStack.areItemStacksEqual(output, recipeOutput);
	}

	public static List<ItemStack> findMatchingRecipes(InventoryCrafting inventory, World world) {
		ItemStack repairRecipe = findRepairRecipe(inventory);
		if (repairRecipe != null) {
			return Collections.singletonList(repairRecipe);
		}

		List<ItemStack> matchingRecipes = new ArrayList<>();

		for (Object recipe : CraftingManager.getInstance().getRecipeList()) {
			IRecipe irecipe = (IRecipe) recipe;

			if (irecipe.matches(inventory, world)) {
				ItemStack result = irecipe.getCraftingResult(inventory);
				matchingRecipes.add(result);
			}
		}

		return matchingRecipes;
	}

	private static ItemStack findRepairRecipe(InventoryCrafting inventory) {
		int craftIngredientCount = 0;
		ItemStack itemstack0 = null;
		ItemStack itemstack1 = null;

		for (int j = 0; j < inventory.getSizeInventory(); j++) {
			ItemStack itemstack = inventory.getStackInSlot(j);

			if (itemstack != null) {
				if (craftIngredientCount == 0) {
					itemstack0 = itemstack;
				}

				if (craftIngredientCount == 1) {
					itemstack1 = itemstack;
				}

				++craftIngredientCount;
			}
		}

		if (craftIngredientCount == 2 && itemstack0.getItem() == itemstack1.getItem() && itemstack0.stackSize == 1 && itemstack1.stackSize == 1 && itemstack0.getItem().isRepairable()) {
			Item item = itemstack0.getItem();
			int damage0 = item.getMaxDamage() - itemstack0.getItemDamageForDisplay();
			int damage1 = item.getMaxDamage() - itemstack1.getItemDamageForDisplay();
			int repairAmount = damage0 + damage1 + item.getMaxDamage() * 5 / 100;
			int repairedDamage = item.getMaxDamage() - repairAmount;

			if (repairedDamage < 0) {
				repairedDamage = 0;
			}

			return new ItemStack(itemstack0.getItem(), 1, repairedDamage);
		}

		return null;
	}

	private static void cleanRecipe(Object... obj) {
		for (int i = 0; i < obj.length; i++) {
			if (obj[i] instanceof ForestryItem) {
				obj[i] = ((ForestryItem) obj[i]).item();
			} else if (obj[i] instanceof ForestryBlock) {
				obj[i] = ((ForestryBlock) obj[i]).block();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static void addRecipe(ItemStack itemstack, Object... obj) {
		cleanRecipe(obj);
		CraftingManager.getInstance().getRecipeList().add(new ShapedOreRecipe(itemstack, obj));
	}

	@SuppressWarnings("unchecked")
	public static void addShapelessRecipe(ItemStack itemstack, Object... obj) {
		cleanRecipe(obj);
		CraftingManager.getInstance().getRecipeList().add(new ShapelessOreRecipe(itemstack, obj));
	}

	public static void addSmelting(ItemStack res, ItemStack prod, float xp) {
		if (res == null || res.getItem() == null) {
			throw new IllegalArgumentException("Tried to register smelting recipe with null input");
		}
		if (prod == null || prod.getItem() == null) {
			throw new IllegalArgumentException("Tried to register smelting recipe with null output");
		}
		GameRegistry.addSmelting(res, prod, xp);
	}
}
