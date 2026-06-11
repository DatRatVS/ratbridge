package datrat.ratbridge;

import com.mojang.logging.LogUtils;
import datrat.ratbridge.platform.forge.ForgeServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(RatBridge.MOD_ID)
public final class RatBridge {
    public static final String MOD_ID = "ratbridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RatBridge() {
        MinecraftForge.EVENT_BUS.register(new ForgeServerEvents());
    }
}
