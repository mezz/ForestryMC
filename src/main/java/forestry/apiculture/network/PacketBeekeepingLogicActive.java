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
package forestry.apiculture.network;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeekeepingLogic;
import forestry.apiculture.BeekeepingLogic;
import forestry.core.network.DataInputStreamForestry;
import forestry.core.network.DataOutputStreamForestry;
import forestry.core.network.IForestryPacketClient;
import forestry.core.network.PacketCoordinates;
import forestry.core.network.PacketIdClient;
import forestry.core.proxy.Proxies;

public class PacketBeekeepingLogicActive extends PacketCoordinates implements IForestryPacketClient {
	private BeekeepingLogic beekeepingLogic;

	public PacketBeekeepingLogicActive() {
	}

	public PacketBeekeepingLogicActive(IBeeHousing tile) {
		super(PacketIdClient.BEE_LOGIC_ACTIVE, tile.getCoordinates());
		this.beekeepingLogic = (BeekeepingLogic) tile.getBeekeepingLogic();
	}

	@Override
	protected void writeData(DataOutputStreamForestry data) throws IOException {
		super.writeData(data);
		beekeepingLogic.writeData(data);
	}

	@Override
	public void onPacketData(DataInputStreamForestry data, EntityPlayer player) throws IOException {
		TileEntity tile = getTarget(Proxies.common.getRenderWorld());
		if (tile instanceof IBeeHousing) {
			IBeeHousing beeHousing = (IBeeHousing) tile;
			IBeekeepingLogic beekeepingLogic = beeHousing.getBeekeepingLogic();
			if (beekeepingLogic instanceof BeekeepingLogic) {
				((BeekeepingLogic) beekeepingLogic).readData(data);
			}
		}
	}
}
