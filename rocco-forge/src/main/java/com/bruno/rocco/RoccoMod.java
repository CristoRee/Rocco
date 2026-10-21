package com.bruno.rocco;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RoccoMod.MODID)
public class RoccoMod
{
    public static final String MODID = "rocco";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RoccoMod(FMLJavaModLoadingContext context)
    {
        LOGGER.info("Rocco mod cargando...");
    }
}
