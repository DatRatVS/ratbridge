package datrat.ratbridge.neoforge;

import datrat.ratbridge.platform.neoforge.NeoForgeServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RatBridgeNeoForge.MOD_ID)
public final class RatBridgeNeoForge {
    public static final String MOD_ID = "ratbridge";
    public static final Logger LOGGER = LoggerFactory.getLogger("RatBridge");

    public RatBridgeNeoForge(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(new NeoForgeServerEvents());
    }
}
