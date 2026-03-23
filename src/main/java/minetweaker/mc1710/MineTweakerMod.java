/*
 * To change this license header, choose License Headers in Project Properties. To change this template file, choose
 * Tools | Templates and open the template in the editor.
 */

package minetweaker.mc1710;

import java.io.File;
import java.util.List;

import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import minetweaker.MineTweakerAPI;
import minetweaker.MineTweakerImplementationAPI;
import minetweaker.api.logger.FileLogger;
import minetweaker.mc1710.brackets.ItemBracketHandler;
import minetweaker.mc1710.brackets.LiquidBracketHandler;
import minetweaker.mc1710.client.MCClient;
import minetweaker.mc1710.formatting.MCFormatter;
import minetweaker.mc1710.furnace.FuelTweaker;
import minetweaker.mc1710.furnace.MCFurnaceManager;
import minetweaker.mc1710.game.MCGame;
import minetweaker.mc1710.mods.MCLoadedMods;
import minetweaker.mc1710.network.*;
import minetweaker.mc1710.oredict.MCOreDict;
import minetweaker.mc1710.recipes.MCRecipeManager;
import minetweaker.mc1710.server.MCServer;
import minetweaker.mc1710.util.MineTweakerHacks;
import minetweaker.mc1710.util.MineTweakerPlatformUtils;
import minetweaker.mc1710.vanilla.MCVanilla;
import minetweaker.runtime.*;
import minetweaker.runtime.providers.ScriptProviderCascade;
import minetweaker.runtime.providers.ScriptProviderCustom;
import minetweaker.runtime.providers.ScriptProviderDirectory;

/**
 * Main mod class. Performs some general logic, initialization of the API and FML event handling.
 *
 * @author Stan Hebben
 */
@Mod(modid = MineTweakerMod.MODID, version = Tags.MOD_VERSION)
public class MineTweakerMod {

    public static final String MODID = "MineTweaker3";
    public static final String MCVERSION = "1.7.10";

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    private static final String[] REGISTRIES = { "minetweaker.mods.ic2.ClassRegistry",
            "minetweaker.mods.nei.ClassRegistry", "minetweaker.mods.mfr.ClassRegistry",
            "minetweaker.mods.gregtech.ClassRegistry", "minetweaker.mods.buildcraft.ClassRegistry" };
    private static final String[] REGISTRY_DESCRIPTIONS = { "IC2 mod support", "NEI mod support", "MFR mod support",
            "GregTech mod support", "Buildcraft mod support" };

    static {
        NETWORK.registerMessage(
                MineTweakerLoadScriptsHandler.class,
                MineTweakerLoadScriptsPacket.class,
                0,
                Side.CLIENT);
        NETWORK.registerMessage(
                MineTweakerOpenBrowserHandler.class,
                MineTweakerOpenBrowserPacket.class,
                1,
                Side.CLIENT);
        NETWORK.registerMessage(
                MineTweakerCopyClipboardHandler.class,
                MineTweakerCopyClipboardPacket.class,
                2,
                Side.CLIENT);
    }

    @Mod.Instance(MODID)
    public static MineTweakerMod INSTANCE;

    private final IScriptProvider scriptsGlobal;
    private final ScriptProviderCustom scriptsIMC;
    private volatile boolean loaded = false;

    public MineTweakerMod() {
        MCRecipeManager.recipes = (List<IRecipe>) CraftingManager.getInstance().getRecipeList();
        MineTweakerImplementationAPI.init(
                new MCOreDict(),
                new MCRecipeManager(),
                new MCFurnaceManager(),
                MCGame.INSTANCE,
                new MCLoadedMods(),
                new MCFormatter(),
                new MCVanilla());

        MineTweakerImplementationAPI.logger
                .addLogger(new FileLogger(new File("logs" + File.pathSeparator + "minetweaker.log")));
        MineTweakerImplementationAPI.platform = MCPlatformFunctions.INSTANCE;

        File globalDir = new File("scripts");
        if (!globalDir.exists()) globalDir.mkdirs();

        scriptsIMC = new ScriptProviderCustom("intermod");
        scriptsGlobal = new ScriptProviderDirectory(globalDir);
        MineTweakerImplementationAPI.setScriptProvider(scriptsGlobal);
    }

    // ##########################
    // ### FML Event Handlers ###
    // ##########################

    @EventHandler
    public void onIMCEvent(FMLInterModComms.IMCEvent event) {
        for (final FMLInterModComms.IMCMessage imcMessage : event.getMessages()) {
            if (imcMessage.key.equalsIgnoreCase("addMineTweakerScript")) {
                if (imcMessage.isStringMessage()) {
                    scriptsIMC.add(imcMessage.getSender() + "::imc", imcMessage.getStringValue());
                } else if (imcMessage.isNBTMessage()) {
                    NBTTagCompound message = imcMessage.getNBTValue();
                    scriptsIMC.add(
                            imcMessage.getSender() + "::" + message.getString("name"),
                            message.getString("content"));
                }
            }
        }
    }

    @EventHandler
    public void onLoad(FMLPreInitializationEvent ev) {
        MineTweakerConfig.file = new File(ev.getModConfigurationDirectory(), MODID + ".cfg");
        MineTweakerConfig.load();
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());
        FMLCommonHandler.instance().bus().register(new FMLEventHandler());
    }

    @EventHandler
    public void onPostInit(FMLPostInitializationEvent ev) {
        MineTweakerAPI.registerClassRegistry(MineTweakerRegistry.class);

        for (int i = 0; i < REGISTRIES.length; i++) {
            MineTweakerAPI.registerClassRegistry(REGISTRIES[i], REGISTRY_DESCRIPTIONS[i]);
        }

        FuelTweaker.INSTANCE.register();
    }

    @EventHandler
    public void onComplete(FMLLoadCompleteEvent ev) {
        MineTweakerAPI.logInfo("MineTweaker: Building registry");
        ItemBracketHandler.rebuildItemRegistry();
        LiquidBracketHandler.rebuildLiquidRegistry();
        MineTweakerAPI.logInfo("MineTweaker: Sucessfully built item registry");
        if (MineTweakerPlatformUtils.isClient()) {
            MineTweakerAPI.client = new MCClient();
        }
    }

    @EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent ev) {
        File scriptsDir = new File(MineTweakerHacks.getWorldDirectory(ev.getServer()), "scripts");
        if (!scriptsDir.exists()) {
            scriptsDir.mkdir();
        }

        IScriptProvider scriptsLocal = new ScriptProviderDirectory(scriptsDir);
        IScriptProvider cascaded = new ScriptProviderCascade(scriptsIMC, scriptsGlobal, scriptsLocal);

        MineTweakerImplementationAPI.setScriptProvider(cascaded);
        MCServer srv = new MCServer(ev.getServer());
        if (loaded && MineTweakerConfig.antiStuck) MineTweakerAPI.server = srv;
        else {
            MineTweakerImplementationAPI.onServerStart(srv);
            loaded = true;
        }
    }

    public void onClientAboutToConnect() {
        if (!MineTweakerConfig.loadScriptsBeforeConnection) return;
        IScriptProvider cascaded = new ScriptProviderCascade(scriptsIMC, scriptsGlobal);
        MineTweakerImplementationAPI.setScriptProvider(cascaded);
        if (loaded && MineTweakerConfig.antiStuck) return;
        MineTweakerImplementationAPI.reload();
        loaded = true;
    }

    @EventHandler
    public void onServerStopped(FMLServerStoppedEvent ev) {
        MineTweakerImplementationAPI.onServerStop();
        MineTweakerImplementationAPI.setScriptProvider(scriptsGlobal);
        MineTweakerImplementationAPI.logger.clearPlayers();
    }
}
