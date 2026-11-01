package dev.arcana.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Initial player-facing casting media.
 *
 * These items are intentionally inert in this milestone. Spell storage,
 * menus, and casting interactions will be added as separate server-validated
 * systems rather than hidden inside item callbacks.
 */
public final class ArcanaItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems("arcana");

    public static final DeferredItem<Item> GRIMOIRE =
            ITEMS.registerItem("grimoire", Item::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> WAND =
            ITEMS.registerItem("wand", Item::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> STAFF =
            ITEMS.registerItem("staff", Item::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> SCROLL =
            ITEMS.registerItem("scroll", Item::new,
                    new Item.Properties().stacksTo(16));

    private ArcanaItems() { }
}
