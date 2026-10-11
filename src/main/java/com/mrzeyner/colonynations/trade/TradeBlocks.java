package com.mrzeyner.colonynations.trade;

import com.mrzeyner.colonynations.ColonyNations;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TradeBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ColonyNations.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ColonyNations.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ColonyNations.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ColonyNations.MOD_ID);

    public static final DeferredHolder<Block, TradeTerminalBlock> TRADE_TERMINAL = BLOCKS.register("trade_terminal",
            () -> new TradeTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredHolder<Item, BlockItem> TRADE_TERMINAL_ITEM = ITEMS.register("trade_terminal",
            () -> new BlockItem(TRADE_TERMINAL.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradeTerminalBlockEntity>> TRADE_TERMINAL_BE =
            BLOCK_ENTITIES.register("trade_terminal", () -> BlockEntityType.Builder
                    .of(TradeTerminalBlockEntity::new, TRADE_TERMINAL.get())
                    .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<TradeTerminalMenu>> TRADE_TERMINAL_MENU =
            MENUS.register("trade_terminal", () -> IMenuTypeExtension.create(TradeTerminalMenu::new));

    private TradeBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(TradeBlocks::registerCapabilities);
        bus.addListener(TradeBlocks::addCreative);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TRADE_TERMINAL_BE.get(),
                TradeTerminalAutomation::forSide);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(TRADE_TERMINAL_ITEM.get());
        }
    }
}
