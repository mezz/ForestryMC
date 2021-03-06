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
package forestry.factory.tiles;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import cpw.mods.fml.common.Optional;

import forestry.api.core.ForestryAPI;
import forestry.core.config.Config;
import forestry.core.config.Constants;
import forestry.core.errors.EnumErrorCode;
import forestry.core.fluids.FluidHelper;
import forestry.core.fluids.TankManager;
import forestry.core.fluids.tanks.StandardTank;
import forestry.core.inventory.IInventoryAdapter;
import forestry.core.inventory.TileInventoryAdapter;
import forestry.core.network.DataInputStreamForestry;
import forestry.core.network.DataOutputStreamForestry;
import forestry.core.network.GuiId;
import forestry.core.render.TankRenderInfo;
import forestry.core.tiles.ILiquidTankTile;
import forestry.core.tiles.TilePowered;
import forestry.core.utils.InventoryUtil;
import forestry.factory.triggers.FactoryTriggers;

import buildcraft.api.statements.ITriggerExternal;

public class TileBottler extends TilePowered implements ISidedInventory, ILiquidTankTile {

	/* CONSTANTS */
	public static final short SLOT_INPUT_EMPTY_CAN = 0;
	public static final short SLOT_OUTPUT = 1;
	public static final short SLOT_INPUT_FULL_CAN = 2;

	private static final short CYCLES_FILLING_DEFAULT = 5;

	public static class BottlerRecipe {

		public final int cyclesPerUnit;
		public final FluidStack input;
		public final ItemStack can;
		public final ItemStack bottled;

		public BottlerRecipe(int cyclesPerUnit, FluidStack input, ItemStack can, ItemStack bottled) {
			this.cyclesPerUnit = cyclesPerUnit;
			this.input = input;
			this.can = can;
			this.bottled = bottled;
		}

		public boolean matches(FluidStack res, ItemStack empty) {
			return res != null && res.containsFluid(input) && can.isItemEqual(empty);
		}
	}

	public static class RecipeManager {

		private static final Set<BottlerRecipe> recipes = new HashSet<>();

		/**
		 * @return Recipe matching both res and empty, null if none
		 */
		public static BottlerRecipe findMatchingRecipe(FluidStack res, ItemStack empty) {
			// We need both ingredients
			if (res == null || empty == null || !FluidHelper.isEmptyContainer(empty)) {
				return null;
			}

			for (BottlerRecipe recipe : recipes) {
				if (recipe.matches(res, empty)) {
					return recipe;
				}
			}
			
			// No recipe matched. See if the liquid dictionary has anything.
			ItemStack filled = FluidHelper.getFilledContainer(res, empty);
			if (filled != null) {
				BottlerRecipe recipe = new BottlerRecipe(CYCLES_FILLING_DEFAULT, res, empty, filled);
				recipes.add(recipe);
				return recipe;
			}

			return null;
		}

		/**
		 * @return true if any recipe has a matching input
		 */
		public static boolean isInput(FluidStack res) {
			if (res == null) {
				return false;
			}
			return FluidRegistry.isFluidRegistered(res.getFluid());
		}

		public static Set<BottlerRecipe> recipes() {
			return Collections.unmodifiableSet(recipes);
		}

		public static Map<Object[], Object[]> getRecipes() {
			HashMap<Object[], Object[]> recipeList = new HashMap<>();

			for (BottlerRecipe recipe : recipes) {
				recipeList.put(new Object[]{recipe.input, recipe.can}, new Object[]{recipe.bottled});
			}

			return recipeList;
		}
	}

	private final StandardTank resourceTank;
	private final TankManager tankManager;

	private BottlerRecipe currentRecipe;
	private int fillingTime;
	private int fillingTotalTime;

	public TileBottler() {
		super(1100, 4000, 200);

		setInternalInventory(new BottlerInventoryAdapter(this));

		setHints(Config.hints.get("bottler"));
		resourceTank = new StandardTank(Constants.PROCESSOR_TANK_CAPACITY);
		tankManager = new TankManager(resourceTank);
	}

	@Override
	public void openGui(EntityPlayer player) {
		player.openGui(ForestryAPI.instance, GuiId.BottlerGUI.ordinal(), player.worldObj, xCoord, yCoord, zCoord);
	}

	/* SAVING & LOADING */
	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);

		nbttagcompound.setInteger("FillingTime", fillingTime);
		nbttagcompound.setInteger("FillingTotalTime", fillingTotalTime);

		tankManager.writeTanksToNBT(nbttagcompound);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);

		fillingTime = nbttagcompound.getInteger("FillingTime");
		fillingTotalTime = nbttagcompound.getInteger("FillingTotalTime");

		tankManager.readTanksFromNBT(nbttagcompound);

		checkRecipe();
	}

	@Override
	public void writeData(DataOutputStreamForestry data) throws IOException {
		super.writeData(data);
		tankManager.writePacketData(data);
	}

	@Override
	public void readData(DataInputStreamForestry data) throws IOException {
		super.readData(data);
		tankManager.readPacketData(data);
	}

	@Override
	public void updateServerSide() {
		super.updateServerSide();

		if (!updateOnInterval(20)) {
			return;
		}

		if (getStackInSlot(SLOT_INPUT_FULL_CAN) != null) {
			FluidHelper.drainContainers(tankManager, this, SLOT_INPUT_FULL_CAN);
		}

		checkRecipe();
	}

	@Override
	public boolean workCycle() {
		checkRecipe();

		// Continue work if nothing needs to be added
		if (fillingTime <= 0) {
			return false;
		}

		if (currentRecipe == null) {
			return false;
		}

		boolean added = InventoryUtil.tryAddStack(this, currentRecipe.bottled, SLOT_OUTPUT, 1, true, false);

		if (getErrorLogic().setCondition(!added, EnumErrorCode.NOSPACE)) {
			return false;
		}

		fillingTime--;
		if (fillingTime > 0) {
			return true;
		}

		FluidHelper.fillContainers(tankManager, this, SLOT_INPUT_EMPTY_CAN, SLOT_OUTPUT, currentRecipe.input.getFluid());

		checkRecipe();
		resetRecipe();

		return true;
	}

	private void checkRecipe() {
		ItemStack emptyCan = getStackInSlot(SLOT_INPUT_EMPTY_CAN);
		BottlerRecipe recipe = RecipeManager.findMatchingRecipe(resourceTank.getFluid(), emptyCan);

		if (currentRecipe != recipe) {
			currentRecipe = recipe;
			resetRecipe();
		}

		getErrorLogic().setCondition(currentRecipe == null, EnumErrorCode.NORECIPE);
	}

	private void resetRecipe() {
		if (currentRecipe == null) {
			fillingTime = 0;
			fillingTotalTime = 0;
			return;
		}

		fillingTime = currentRecipe.cyclesPerUnit;
		fillingTotalTime = currentRecipe.cyclesPerUnit;
	}

	// / STATE INFORMATION

	@Override
	public boolean hasResourcesMin(float percentage) {
		IInventoryAdapter inventory = getInternalInventory();
		if (inventory.getStackInSlot(SLOT_INPUT_EMPTY_CAN) == null) {
			return false;
		}

		return ((float) inventory.getStackInSlot(SLOT_INPUT_EMPTY_CAN).stackSize / (float) inventory.getStackInSlot(SLOT_INPUT_EMPTY_CAN).getMaxStackSize()) > percentage;
	}

	@Override
	public boolean hasWork() {
		return currentRecipe != null;
	}

	public int getFillProgressScaled(int i) {
		if (fillingTotalTime == 0) {
			return 0;
		}

		return ((fillingTotalTime - fillingTime) * i) / fillingTotalTime;

	}

	public int getResourceScaled(int i) {
		return (resourceTank.getFluidAmount() * i) / Constants.PROCESSOR_TANK_CAPACITY;
	}

	@Override
	public TankRenderInfo getResourceTankInfo() {
		return new TankRenderInfo(resourceTank, getResourceScaled(100));
	}

	/* SMP GUI */
	@Override
	public void getGUINetworkData(int i, int j) {
		i -= tankManager.maxMessageId() + 1;
		switch (i) {
			case 0:
				fillingTime = j;
				break;
			case 1:
				fillingTotalTime = j;
				break;
		}
	}

	@Override
	public void sendGUINetworkData(Container container, ICrafting iCrafting) {
		int i = tankManager.maxMessageId() + 1;
		iCrafting.sendProgressBarUpdate(container, i, fillingTime);
		iCrafting.sendProgressBarUpdate(container, i + 1, fillingTotalTime);
	}

	/* ILIQUIDCONTAINER */
	@Override
	public TankManager getTankManager() {
		return tankManager;
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
		return tankManager.fill(from, resource, doFill);
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
		return tankManager.drain(from, resource, doDrain);
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
		return tankManager.drain(from, maxDrain, doDrain);
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid) {
		return tankManager.canFill(from, fluid);
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid) {
		return tankManager.canDrain(from, fluid);
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from) {
		return tankManager.getTankInfo(from);
	}

	/* ITRIGGERPROVIDER */
	@Optional.Method(modid = "BuildCraftAPI|statements")
	@Override
	public Collection<ITriggerExternal> getExternalTriggers(ForgeDirection side, TileEntity tile) {
		LinkedList<ITriggerExternal> res = new LinkedList<>();
		res.add(FactoryTriggers.lowResource25);
		res.add(FactoryTriggers.lowResource10);
		return res;
	}

	private static class BottlerInventoryAdapter extends TileInventoryAdapter<TileBottler> {
		public BottlerInventoryAdapter(TileBottler tileBottler) {
			super(tileBottler, 3, "Items");
		}

		@Override
		public boolean canSlotAccept(int slotIndex, ItemStack itemStack) {
			if (slotIndex == SLOT_INPUT_EMPTY_CAN) {
				return FluidContainerRegistry.isEmptyContainer(itemStack);
			} else if (slotIndex == SLOT_INPUT_FULL_CAN) {
				FluidStack fluidStack = FluidHelper.getFluidStackInContainer(itemStack);
				return RecipeManager.isInput(fluidStack);
			}
			return false;
		}

		@Override
		public boolean canExtractItem(int slotIndex, ItemStack itemstack, int side) {
			return slotIndex == SLOT_OUTPUT;
		}
	}
}
