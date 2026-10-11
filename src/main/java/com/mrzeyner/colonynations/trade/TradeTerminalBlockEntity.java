package com.mrzeyner.colonynations.trade;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mrzeyner.colonynations.compat.minecolonies.ChunkNation;
import com.mrzeyner.colonynations.compat.numismatics.NumismaticsCoins;
import com.mrzeyner.colonynations.data.NationSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Three inventories and the nation this terminal belongs to.
 * The good for sale stays empty until T25, so automation accepts coins only.
 */
public final class TradeTerminalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STOCK_SLOTS = 27;
    public static final int COIN_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 9;

    private final ItemStackHandler stock = new Notifying(STOCK_SLOTS, true);
    private final ItemStackHandler coins = new Notifying(COIN_SLOTS, false);
    private final ItemStackHandler output = new Notifying(OUTPUT_SLOTS, true);
    private final TradeTerminalAutomation.Insert insert = new TradeTerminalAutomation.Insert(this);
    private final TradeTerminalAutomation.Output extract = new TradeTerminalAutomation.Output(this);
    private ItemStack goods = ItemStack.EMPTY;
    private UUID nationId;

    public TradeTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(TradeBlocks.TRADE_TERMINAL_BE.get(), pos, state);
    }

    public ItemStackHandler stock() {
        return stock;
    }

    public ItemStackHandler coins() {
        return coins;
    }

    public ItemStackHandler output() {
        return output;
    }

    public TradeTerminalAutomation.Insert insertHandler() {
        return insert;
    }

    public TradeTerminalAutomation.Output outputHandler() {
        return extract;
    }

    public UUID nationId() {
        return nationId;
    }

    public ItemStack goods() {
        return goods;
    }

    public boolean acceptsGoods(ItemStack stack) {
        return !goods.isEmpty() && stack != null && !stack.isEmpty() && ItemStack.isSameItemSameComponents(goods, stack);
    }

    public void adoptLand(@Nullable UUID landNation) {
        UUID next = TerminalNation.follow(nationId, landNation);
        if (next == null ? nationId != null : !next.equals(nationId)) {
            nationId = next;
            setChanged();
        }
    }

    public boolean canManage(ServerPlayer player) {
        if (nationId == null || player == null) {
            return false;
        }
        NationSavedData data = NationSavedData.get(player.server);
        return data.registry().byId(nationId).map(nation -> player.getUUID().equals(nation.owner())).orElse(false);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 20L != 0L) {
            return;
        }
        adoptLand(ChunkNation.of(server, worldPosition));
    }

    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, stock);
        drop(level, pos, coins);
        drop(level, pos, output);
    }

    private static void drop(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                handler.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.colony_nations.trade_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        boolean owner = player instanceof ServerPlayer serverPlayer && canManage(serverPlayer);
        return new TradeTerminalMenu(id, inventory, this, owner);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Stock", stock.serializeNBT(registries));
        tag.put("Coins", coins.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
        if (nationId != null) {
            tag.putLong("NationMost", nationId.getMostSignificantBits());
            tag.putLong("NationLeast", nationId.getLeastSignificantBits());
        }
        if (!goods.isEmpty()) {
            tag.put("Goods", goods.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Stock")) {
            stock.deserializeNBT(registries, tag.getCompound("Stock"));
        }
        if (tag.contains("Coins")) {
            coins.deserializeNBT(registries, tag.getCompound("Coins"));
        }
        if (tag.contains("Output")) {
            output.deserializeNBT(registries, tag.getCompound("Output"));
        }
        if (tag.contains("NationMost") && tag.contains("NationLeast")) {
            nationId = new UUID(tag.getLong("NationMost"), tag.getLong("NationLeast"));
        } else {
            nationId = null;
        }
        goods = tag.contains("Goods")
                ? ItemStack.parse(registries, tag.get("Goods")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    private final class Notifying extends ItemStackHandler {
        private final boolean anyItem;

        private Notifying(int slots, boolean anyItem) {
            super(slots);
            this.anyItem = anyItem;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return anyItem || NumismaticsCoins.isCoin(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    }
}
