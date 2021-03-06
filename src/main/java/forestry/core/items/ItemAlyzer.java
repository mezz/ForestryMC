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
package forestry.core.items;

import com.google.common.collect.ImmutableSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import forestry.api.core.IErrorSource;
import forestry.api.core.IErrorState;
import forestry.api.genetics.IBreedingTracker;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.ISpeciesRoot;
import forestry.core.config.ForestryItem;
import forestry.core.errors.EnumErrorCode;
import forestry.core.inventory.ItemInventory;

public abstract class ItemAlyzer extends ItemInventoried {

	public abstract static class AlyzerInventory extends ItemInventory implements IErrorSource {
		public static final int SLOT_SPECIMEN = 0;
		public static final int SLOT_ANALYZE_1 = 1;
		public static final int SLOT_ANALYZE_2 = 2;
		public static final int SLOT_ANALYZE_3 = 3;
		public static final int SLOT_ANALYZE_4 = 4;
		public static final int SLOT_ANALYZE_5 = 6;
		public static final int SLOT_ENERGY = 5;

		public AlyzerInventory(EntityPlayer player, int size, ItemStack itemstack) {
			super(player, size, itemstack);
		}

		protected static boolean isEnergy(ItemStack itemstack) {
			if (itemstack == null || itemstack.stackSize <= 0) {
				return false;
			}

			return ForestryItem.honeyDrop.isItemEqual(itemstack) || ForestryItem.honeydew.isItemEqual(itemstack);
		}

		private boolean hasSpecimen() {
			for (int i = SLOT_SPECIMEN; i <= SLOT_ANALYZE_5; i++) {
				if (i == SLOT_ENERGY) {
					continue;
				}

				ItemStack itemStack = getStackInSlot(i);
				if (itemStack != null) {
					return true;
				}
			}
			return false;
		}

		protected abstract ISpeciesRoot getSpeciesRoot();

		@Override
		public boolean canSlotAccept(int slotIndex, ItemStack itemStack) {
			if (slotIndex == SLOT_ENERGY) {
				return isEnergy(itemStack);
			}

			ISpeciesRoot speciesRoot = getSpeciesRoot();
			if (!speciesRoot.isMember(itemStack)) {
				return false;
			}

			// only allow one slot to be used at a time
			if (hasSpecimen() && getStackInSlot(slotIndex) == null) {
				return false;
			}

			if (slotIndex == SLOT_SPECIMEN) {
				return true;
			}

			IIndividual individual = speciesRoot.getMember(itemStack);
			return individual.isAnalyzed();
		}

		@Override
		public void onSlotClick(EntityPlayer player) {
			// Source slot to analyze empty
			ItemStack specimen = getStackInSlot(SLOT_SPECIMEN);
			if (specimen == null) {
				return;
			}

			IIndividual individual = getSpeciesRoot().getMember(specimen);
			// No individual, abort
			if (individual == null) {
				return;
			}

			// Analyze if necessary
			if (!individual.isAnalyzed()) {

				// Requires energy
				if (!isEnergy(getStackInSlot(SLOT_ENERGY))) {
					return;
				}

				individual.analyze();
				if (player != null) {
					IBreedingTracker breedingTracker = getSpeciesRoot().getBreedingTracker(player.worldObj, player.getGameProfile());
					breedingTracker.registerSpecies(individual.getGenome().getPrimary());
					breedingTracker.registerSpecies(individual.getGenome().getSecondary());
				}

				NBTTagCompound nbttagcompound = new NBTTagCompound();
				individual.writeToNBT(nbttagcompound);
				specimen.setTagCompound(nbttagcompound);

				// Decrease energy
				decrStackSize(SLOT_ENERGY, 1);
			}

			setInventorySlotContents(SLOT_ANALYZE_1, specimen);
			setInventorySlotContents(SLOT_SPECIMEN, null);
		}

		@Override
		public final ImmutableSet<IErrorState> getErrorStates() {
			ImmutableSet.Builder<IErrorState> errorStates = ImmutableSet.builder();

			if (!hasSpecimen()) {
				errorStates.add(EnumErrorCode.NOTHINGANALYZE);
			}

			if (!isEnergy(getStackInSlot(SLOT_ENERGY))) {
				errorStates.add(EnumErrorCode.NOHONEY);
			}

			return errorStates.build();
		}
	}

}
