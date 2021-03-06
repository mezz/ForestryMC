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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.biome.BiomeGenBase;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import forestry.api.core.BiomeHelper;
import forestry.api.core.ForestryAPI;
import forestry.api.core.IErrorLogic;
import forestry.core.config.Config;
import forestry.core.config.Constants;
import forestry.core.errors.EnumErrorCode;
import forestry.core.fluids.FluidHelper;
import forestry.core.fluids.Fluids;
import forestry.core.fluids.TankManager;
import forestry.core.fluids.tanks.FilteredTank;
import forestry.core.inventory.TileInventoryAdapter;
import forestry.core.network.DataInputStreamForestry;
import forestry.core.network.DataOutputStreamForestry;
import forestry.core.network.GuiId;
import forestry.core.tiles.ILiquidTankTile;
import forestry.core.tiles.TileBase;
import forestry.core.utils.ItemStackUtil;

public class TileRaintank extends TileBase implements ISidedInventory, ILiquidTankTile {

	/* CONSTANTS */
	public static final short SLOT_RESOURCE = 0;
	public static final short SLOT_PRODUCT = 1;
	private static final FluidStack STACK_WATER = Fluids.WATER.getFluid(Constants.RAINTANK_AMOUNT_PER_UPDATE);

	/* MEMBER */
	private final FilteredTank resourceTank;
	private final TankManager tankManager;
	private boolean isValidBiome = true;
	private int fillingTime;
	private ItemStack usedEmpty;

	public TileRaintank() {
		setInternalInventory(new RaintankInventoryAdapter(this));
		setHints(Config.hints.get("raintank"));

		resourceTank = new FilteredTank(Constants.RAINTANK_TANK_CAPACITY, FluidRegistry.WATER);
		tankManager = new TankManager(resourceTank);
	}

	@Override
	public void validate() {
		// Raintanks in desert biomes are useless
		if (worldObj != null) {
			BiomeGenBase biome = worldObj.getBiomeGenForCoordsBody(xCoord, zCoord);
			isValidBiome = BiomeHelper.canRainOrSnow(biome);
			getErrorLogic().setCondition(!isValidBiome, EnumErrorCode.INVALIDBIOME);
		}

		super.validate();
	}

	@Override
	public void openGui(EntityPlayer player) {
		player.openGui(ForestryAPI.instance, GuiId.RaintankGUI.ordinal(), player.worldObj, xCoord, yCoord, zCoord);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);

		nbttagcompound.setBoolean("IsValidBiome", isValidBiome);

		tankManager.writeTanksToNBT(nbttagcompound);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);

		isValidBiome = nbttagcompound.getBoolean("IsValidBiome");

		tankManager.readTanksFromNBT(nbttagcompound);
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

		if (!updateOnInterval(20)) {
			return;
		}

		IErrorLogic errorLogic = getErrorLogic();

		errorLogic.setCondition(!isValidBiome, EnumErrorCode.INVALIDBIOME);

		boolean hasSky = worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord);
		errorLogic.setCondition(!hasSky, EnumErrorCode.NOSKY);

		errorLogic.setCondition(!worldObj.isRaining(), EnumErrorCode.NOTRAINING);

		if (!errorLogic.hasErrors()) {
			resourceTank.fill(STACK_WATER, true);
		}
		
		if (!ItemStackUtil.isIdenticalItem(usedEmpty, getStackInSlot(SLOT_RESOURCE))) {
			fillingTime = 0;
			usedEmpty = null;
		}

		if (usedEmpty == null) {
			usedEmpty = getStackInSlot(SLOT_RESOURCE);
		}

		if (!isFilling()) {
			tryToStartFillling();
		} else {
			fillingTime--;
			if (fillingTime <= 0 && FluidHelper.fillContainers(tankManager, this, SLOT_RESOURCE, SLOT_PRODUCT, Fluids.WATER.getFluid())) {
				fillingTime = 0;
			}
		}
	}

	public boolean isFilling() {
		return fillingTime > 0;
	}

	private void tryToStartFillling() {
		// Nothing to do if no empty cans are available
		if (!FluidHelper.fillContainers(tankManager, getInternalInventory(), SLOT_RESOURCE, SLOT_PRODUCT, Fluids.WATER.getFluid(), false)) {
			return;
		}

		fillingTime = Constants.RAINTANK_FILLING_TIME;
	}

	public int getFillProgressScaled(int i) {
		return (fillingTime * i) / Constants.RAINTANK_FILLING_TIME;
	}

	/* SMP GUI */
	@Override
	public void getGUINetworkData(int i, int j) {
		i -= tankManager.maxMessageId() + 1;
		switch (i) {
			case 0:
				fillingTime = j;
				break;
		}
	}

	@Override
	public void sendGUINetworkData(Container container, ICrafting iCrafting) {
		int i = tankManager.maxMessageId() + 1;
		iCrafting.sendProgressBarUpdate(container, i, fillingTime);
	}

	// / ILIQUIDCONTAINER IMPLEMENTATION
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
	public TankManager getTankManager() {
		return tankManager;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from) {
		return tankManager.getTankInfo(from);
	}

	private static class RaintankInventoryAdapter extends TileInventoryAdapter<TileRaintank> {
		public RaintankInventoryAdapter(TileRaintank raintank) {
			super(raintank, 3, "Items");
		}

		@Override
		public boolean canSlotAccept(int slotIndex, ItemStack itemStack) {
			if (slotIndex == SLOT_RESOURCE) {
				return FluidHelper.getFilledContainer(Fluids.WATER.getFluid(1000), itemStack) != null;
			}
			return false;
		}

		@Override
		public boolean canExtractItem(int slotIndex, ItemStack itemstack, int side) {
			return slotIndex == SLOT_PRODUCT;
		}
	}
}
