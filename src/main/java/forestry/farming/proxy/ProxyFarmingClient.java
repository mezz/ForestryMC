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
package forestry.farming.proxy;


import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.client.registry.RenderingRegistry;

import forestry.core.config.ForestryBlock;
import forestry.farming.render.RenderFarmBlock;
import forestry.farming.render.RenderFarmItem;
import forestry.plugins.PluginFarming;

public class ProxyFarmingClient extends ProxyFarming {

	@Override
	public void initializeRendering() {
		PluginFarming.modelIdFarmBlock = RenderingRegistry.getNextAvailableRenderId();

		RenderingRegistry.registerBlockHandler(new RenderFarmBlock());
		MinecraftForgeClient.registerItemRenderer(ForestryBlock.farm.item(), new RenderFarmItem());
	}
}
