package com.mrzeyner.colonynations.trade;

import org.jetbrains.annotations.Nullable;

import com.mrzeyner.colonynations.compat.numismatics.NumismaticsCoins;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Sides Create and vanilla hoppers actually touch.
 * A chute or funnel above or beside the block inserts through that face.
 * A chute or hopper below extracts through {@link Direction#DOWN}, which is the output only.
 * A funnel mounted on a train does not call this: Create drops an item entity instead.
 */
public final class TradeTerminalAutomation {
    private TradeTerminalAutomation() {
    }

    public static IItemHandler forSide(TradeTerminalBlockEntity terminal, @Nullable Direction side) {
        if (side == Direction.DOWN) {
            return terminal.outputHandler();
        }
        return terminal.insertHandler();
    }

    public static final class Insert implements IItemHandler {
        private final TradeTerminalBlockEntity terminal;

        Insert(TradeTerminalBlockEntity terminal) {
            this.terminal = terminal;
        }

        @Override
        public int getSlots() {
            return TradeTerminalBlockEntity.STOCK_SLOTS + TradeTerminalBlockEntity.COIN_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < TradeTerminalBlockEntity.STOCK_SLOTS) {
                return terminal.stock().getStackInSlot(slot);
            }
            int coin = slot - TradeTerminalBlockEntity.STOCK_SLOTS;
            if (coin < TradeTerminalBlockEntity.COIN_SLOTS) {
                return terminal.coins().getStackInSlot(coin);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }
            if (slot < TradeTerminalBlockEntity.STOCK_SLOTS) {
                return terminal.stock().insertItem(slot, stack, simulate);
            }
            return terminal.coins().insertItem(slot - TradeTerminalBlockEntity.STOCK_SLOTS, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < TradeTerminalBlockEntity.STOCK_SLOTS) {
                return terminal.stock().getSlotLimit(slot);
            }
            return terminal.coins().getSlotLimit(slot - TradeTerminalBlockEntity.STOCK_SLOTS);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            TerminalInsert.Target target = TerminalInsert.route(NumismaticsCoins.isCoin(stack), terminal.acceptsGoods(stack));
            if (target == TerminalInsert.Target.STOCK) {
                return slot < TradeTerminalBlockEntity.STOCK_SLOTS;
            }
            if (target == TerminalInsert.Target.COIN) {
                return slot >= TradeTerminalBlockEntity.STOCK_SLOTS && slot < getSlots();
            }
            return false;
        }
    }

    public static final class Output implements IItemHandler {
        private final TradeTerminalBlockEntity terminal;

        Output(TradeTerminalBlockEntity terminal) {
            this.terminal = terminal;
        }

        @Override
        public int getSlots() {
            return terminal.output().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return terminal.output().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return terminal.output().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return terminal.output().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
