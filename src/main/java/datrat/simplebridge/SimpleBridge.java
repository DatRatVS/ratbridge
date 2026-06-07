package datrat.simplebridge;

import com.mojang.logging.LogUtils;
import datrat.simplebridge.platform.forge.ForgeServerEvents;
import datrat.simplebridge.platform.forge.SimpleBridgeForgeConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(SimpleBridge.MOD_ID)
public final class SimpleBridge {
    public static final String MOD_ID = "simplebridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SimpleBridge() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SimpleBridgeForgeConfig.SPEC, "simplebridge.toml");
        MinecraftForge.EVENT_BUS.register(new ForgeServerEvents());
    }
}
