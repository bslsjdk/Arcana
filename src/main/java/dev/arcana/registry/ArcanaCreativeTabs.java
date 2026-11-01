package dev.arcana.registry;

import dev.arcana.Arcana;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class ArcanaCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Arcana.MOD_ID);

    public static final Supplier<CreativeModeTab> ARCANA = TABS.register("arcana", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.arcana"))
                    .icon(() -> new ItemStack(ArcanaItems.GRIMOIRE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ArcanaItems.GRIMOIRE.get());
                        output.accept(ArcanaItems.WAND.get());
                        output.accept(ArcanaItems.STAFF.get());
                        output.accept(ArcanaItems.SCROLL.get());
                    })
                    .build());

    private ArcanaCreativeTabs() { }
}
