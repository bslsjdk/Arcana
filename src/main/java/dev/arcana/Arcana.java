package dev.arcana;

import dev.arcana.registry.ArcanaItems;
import dev.arcana.registry.ArcanaCreativeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point. The language core is independent from Minecraft APIs. */
@Mod(Arcana.MOD_ID)
public final class Arcana {
    public static final String MOD_ID = "arcana";

    public Arcana(IEventBus modBus) {
        ArcanaItems.ITEMS.register(modBus);
        ArcanaCreativeTabs.TABS.register(modBus);
    }
}
