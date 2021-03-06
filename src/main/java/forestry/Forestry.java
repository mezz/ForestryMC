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
package forestry;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms.IMCEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.Type;

import forestry.api.fuels.FuelManager;
import forestry.core.ForestryCore;
import forestry.core.config.Constants;
import forestry.core.config.ForestryItem;
import forestry.core.config.Version;
import forestry.core.network.PacketHandler;
import forestry.core.utils.Log;
import forestry.core.utils.StringUtil;
import forestry.core.utils.datastructures.FluidMap;
import forestry.core.utils.datastructures.ItemStackMap;

/**
 * Forestry Minecraft Mod
 *
 * @author SirSengir
 */
@Mod(
		modid = Constants.MOD,
		name = "Forestry",
		version = Version.VERSION,
		guiFactory = "forestry.core.config.ForestryGuiConfigFactory",
		dependencies = "required-after:Forge@[10.13.0.1207,);"
				+ "after:Buildcraft|Core@[6.1.7,);"
				+ "after:ExtrabiomesXL;"
				+ "after:BiomesOPlenty;"
				+ "after:IC2@[2.0.140,);"
				+ "after:Natura@[2.2.0,);"
				+ "after:HardcoreEnderExpansion;")
public class Forestry {

	@Mod.Instance(Constants.MOD)
	public static Forestry instance;
	private File configFolder;

	private static final Map<String, ForestryItem> mappedItems = new HashMap<>();

	static {
		mappedItems.put("Forestry:builderBackpack", ForestryItem.builderBackpack);
		mappedItems.put("Forestry:builderBackpackT2", ForestryItem.builderBackpackT2);
		mappedItems.put("Forestry:adventurerBackpack", ForestryItem.adventurerBackpack);
		mappedItems.put("Forestry:adventurerBackpackT2", ForestryItem.adventurerBackpackT2);
		mappedItems.put("Forestry:shortMead", ForestryItem.beverage);
		mappedItems.put("Forestry:waterCan", ForestryItem.canWater);
		mappedItems.put("Forestry:biofuelCan", ForestryItem.canEthanol);
		mappedItems.put("Forestry:biomassCan", ForestryItem.canBiomass);
		mappedItems.put("Forestry:bucketBiofuel", ForestryItem.bucketEthanol);
		mappedItems.put("Forestry:refractoryBiofuel", ForestryItem.refractoryEthanol);
		mappedItems.put("Forestry:waxCapsuleBiofuel", ForestryItem.waxCapsuleEthanol);
	}

	public Forestry() {
		FuelManager.fermenterFuel = new ItemStackMap<>();
		FuelManager.moistenerResource = new ItemStackMap<>();
		FuelManager.rainSubstrate = new ItemStackMap<>();
		FuelManager.bronzeEngineFuel = new FluidMap<>();
		FuelManager.copperEngineFuel = new ItemStackMap<>();
		FuelManager.generatorFuel = new FluidMap<>();
	}

	@SidedProxy(clientSide = "forestry.core.ForestryClient", serverSide = "forestry.core.ForestryCore")
	public static ForestryCore core = new ForestryCore();
	public static PacketHandler packetHandler;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		packetHandler = new PacketHandler();
		
		configFolder = new File(event.getModConfigurationDirectory(), "forestry");

		core.preInit(event.getSourceFile(), this);
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		core.init(this);
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		core.postInit();
	}

	@EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		core.serverStarting(event.getServer());
	}

	public File getConfigFolder() {
		return configFolder;
	}
	/*@EventHandler
	 public void fingerprintWarning(FMLFingerprintViolationEvent event) {
	 Log.info("Fingerprint of the mod jar is invalid. The jar file was tampered with.");
	 FMLInterModComms.sendMessage("Railcraft", "securityViolation", "Fingerprint of jar file did not match.");
	 FMLInterModComms.sendMessage("Thaumcraft", "securityViolation", "Fingerprint of jar file did not match.");
	 FMLInterModComms.sendMessage("IC2", "securityViolation", "Fingerprint of jar file did not match.");
	 }*/

	@EventHandler
	public void processIMCMessages(IMCEvent event) {
		core.processIMCMessages(event.getMessages());
	}

	@EventHandler
	public void missingMapping(FMLMissingMappingsEvent event) {
		for (MissingMapping mapping : event.get()) {
			if (mapping.type == Type.BLOCK) {
				Block block = GameRegistry.findBlock(Constants.MOD, StringUtil.cleanTags(mapping.name));
				if (block != null) {
					mapping.remap(block);
					Log.warning("Remapping block " + mapping.name + " to " + StringUtil.cleanBlockName(block));
				}
			} else {
				Block block = GameRegistry.findBlock(Constants.MOD, StringUtil.cleanTags(mapping.name));
				if (block != null) {
					mapping.remap(Item.getItemFromBlock(block));
					Log.warning("Remapping item " + mapping.name + " to " + StringUtil.cleanBlockName(block));
				} else {
					ForestryItem mappedItem = mappedItems.get(mapping.name);
					if (mappedItem != null) {
						mapping.remap(mappedItem.item());
						Log.warning("Remapping item " + mapping.name + " to " + mappedItem.name());
					}
				}
			}
		}
	}
}
