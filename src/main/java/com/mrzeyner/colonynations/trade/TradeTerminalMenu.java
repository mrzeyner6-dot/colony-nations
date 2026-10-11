package com.mrzeyner.colonynations.trade;

import com.mrzeyner.colonynations.compat.numismatics.NumismaticsCoins;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Owner menu exposes the three inventories. A visitor gets the same window id with no slots.
 */
public final class TradeTerminalMenu extends AbstractContainerMenu {
    public static final int STOCK_Y = 28;
    public static final int OUTPUT_Y = 96;
    public static final int COIN_Y = 128;
    public static final int PLAYER_Y = 186;
    private static final int COLUMNS = 9;

    private final BlockPos pos;
    private final Level level;
    private final boolean owner;

    public TradeTerminalMenu(int id, Inventory inventory, TradeTerminalBlockEntity terminal, boolean owner) {
        super(TradeBlocks.TRADE_TERMINAL_MENU.get(), id);
        this.pos = terminal.getBlockPos();
        this.level = terminal.getLevel();
        this.owner = owner;
        if (owner) {
            addTerminal(terminal.stock(), terminal.output(), terminal.coins());
            addPlayer(inventory);
        }
    }

    public TradeTerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(TradeBlocks.TRADE_TERMINAL_MENU.get(), id);
        this.pos = buf.readBlockPos();
        this.owner = buf.readBoolean();
        this.level = inventory.player.level();
        if (!owner) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        TradeTerminalBlockEntity terminal = entity instanceof TradeTerminalBlockEntity found ? found : null;
        IItemHandler stock = terminal != null ? terminal.stock() : new ItemStackHandler(TradeTerminalBlockEntity.STOCK_SLOTS);
        IItemHandler output = terminal != null ? terminal.output() : new ItemStackHandler(TradeTerminalBlockEntity.OUTPUT_SLOTS);
        IItemHandler coins = terminal != null ? terminal.coins() : new ItemStackHandler(TradeTerminalBlockEntity.COIN_SLOTS);
        addTerminal(stock, output, coins);
        addPlayer(inventory);
    }

    public boolean owner() {
        return owner;
    }

    private void addTerminal(IItemHandler stock, IItemHandler output, IItemHandler coins) {
        addGrid(stock, 3, STOCK_Y);
        addGrid(output, 1, OUTPUT_Y);
        addGrid(coins, 1, COIN_Y);
    }

    private void addGrid(IItemHandler handler, int rows, int y) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new SlotItemHandler(handler, column + row * COLUMNS, 8 + column * 18, y + row * 18));
            }
        }
    }

    private void addPlayer(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(inventory, column + row * COLUMNS + COLUMNS, 8 + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, PLAYER_Y + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) {
            return false;
        }
        return stillValid(ContainerLevelAccess.create(level, pos), player, TradeBlocks.TRADE_TERMINAL.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!owner || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int terminalSlots = TradeTerminalBlockEntity.STOCK_SLOTS + TradeTerminalBlockEntity.OUTPUT_SLOTS + TradeTerminalBlockEntity.COIN_SLOTS;
        if (index < terminalSlots) {
            if (!moveItemStackTo(stack, terminalSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (NumismaticsCoins.isCoin(stack)) {
            int coinStart = TradeTerminalBlockEntity.STOCK_SLOTS + TradeTerminalBlockEntity.OUTPUT_SLOTS;
            if (!moveItemStackTo(stack, coinStart, coinStart + TradeTerminalBlockEntity.COIN_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, TradeTerminalBlockEntity.STOCK_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }
}
