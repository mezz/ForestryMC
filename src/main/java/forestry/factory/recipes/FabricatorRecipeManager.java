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
package forestry.factory.recipes;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import forestry.api.recipes.IFabricatorManager;
import forestry.api.recipes.IFabricatorRecipe;
import forestry.core.recipes.ShapedRecipeCustom;
import forestry.core.utils.ItemStackUtil;

public class FabricatorRecipeManager implements IFabricatorManager {

	private static final Set<IFabricatorRecipe> recipes = new HashSet<>();
	public static final Set<FabricatorSmeltingRecipe> smeltings = new HashSet<>();

	@Override
	public void addRecipe(ItemStack plan, FluidStack molten, ItemStack result, Object[] pattern) {
		IFabricatorRecipe recipe = new FabricatorRecipe(plan, molten, ShapedRecipeCustom.createShapedRecipe(result, pattern));
		addRecipe(recipe);
	}

	@Override
	public void addSmelting(ItemStack resource, FluidStack molten, int meltingPoint) {
		if (resource == null || molten == null) {
			return;
		}
		smeltings.add(new FabricatorSmeltingRecipe(resource, molten, meltingPoint));
	}

	public static IFabricatorRecipe findMatchingRecipe(ItemStack plan, FluidStack liquid, ItemStack[] resources) {
		ItemStack[][] gridResources = new ItemStack[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				gridResources[j][i] = resources[i * 3 + j];
			}
		}

		for (IFabricatorRecipe recipe : recipes) {
			if (recipe.matches(plan, gridResources)) {
				if (liquid == null || liquid.containsFluid(recipe.getLiquid())) {
					return recipe;
				}
			}
		}

		return null;
	}

	public static boolean isPlan(ItemStack plan) {
		for (IFabricatorRecipe recipe : recipes) {
			if (ItemStackUtil.isIdenticalItem(recipe.getPlan(), plan)) {
				return true;
			}
		}

		return false;
	}

	public static FabricatorSmeltingRecipe findMatchingSmelting(ItemStack resource) {
		if (resource == null) {
			return null;
		}

		for (FabricatorSmeltingRecipe smelting : smeltings) {
			if (ItemStackUtil.isCraftingEquivalent(smelting.getResource(), resource)) {
				return smelting;
			}
		}

		return null;
	}

	public static FabricatorSmeltingRecipe findMatchingSmelting(FluidStack product) {
		if (product == null) {
			return null;
		}

		for (FabricatorSmeltingRecipe smelting : smeltings) {
			if (smelting.matches(product)) {
				return smelting;
			}
		}

		return null;
	}

	@Override
	public boolean addRecipe(IFabricatorRecipe recipe) {
		return recipes.add(recipe);
	}

	@Override
	public boolean removeRecipe(IFabricatorRecipe recipe) {
		return recipes.remove(recipe);
	}

	@Override
	public Set<IFabricatorRecipe> recipes() {
		return Collections.unmodifiableSet(recipes);
	}

	@Override
	public Map<Object[], Object[]> getRecipes() {
		HashMap<Object[], Object[]> recipeList = new HashMap<>();

		for (IFabricatorRecipe recipe : recipes) {
			recipeList.put(recipe.getIngredients(), new Object[]{recipe.getRecipeOutput()});
		}

		return recipeList;
	}
}
