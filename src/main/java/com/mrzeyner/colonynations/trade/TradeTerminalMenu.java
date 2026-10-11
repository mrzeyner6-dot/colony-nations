package com.mrzeyner.colonynations.trade;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mrzeyner.colonynations.compat.numismatics.NumismaticsCoins;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Owner menu exposes the three inventories and the sample. A visitor gets the sample, locked, plus the price.
 */
public final class TradeTerminalMenu extends AbstractContainerMenu {
    public static final int STOCK_Y = 28;
    public static final int OUTPUT_Y = 96;
    public static final int COIN_Y = 128;
    public static final int PLAYER_Y = 186;
    private static final int COLUMNS = 9;
    private static final int DATA = 13;

    private final BlockPos pos;
    private final Level level;
    private final boolean owner;
    private final boolean remote;
    private final TradeTerminalBlockEntity terminal;
    private final UUID viewerNation;
    private final int[] mirror = new int[DATA];

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return remote ? mirror[index] : live(index);
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < mirror.length) {
                mirror[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public TradeTerminalMenu(int id, Inventory inventory, TradeTerminalBlockEntity terminal, boolean owner, @Nullable UUID viewerNation) {
        super(TradeBlocks.TRADE_TERMINAL_MENU.get(), id);
        this.pos = terminal.getBlockPos();
        this.level = terminal.getLevel();
        this.owner = owner;
        this.remote = false;
        this.terminal = terminal;
        this.viewerNation = viewerNation;
        if (owner) {
            addTerminal(terminal.stock(), terminal.output(), terminal.coins());
            addSlot(new SampleSlot(terminal.sample(), 188, 28, true));
            addPlayer(inventory);
        } else {
            addSlot(new SampleSlot(terminal.sample(), 8, 22, false));
        }
        addDataSlots(data);
    }

    public TradeTerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(TradeBlocks.TRADE_TERMINAL_MENU.get(), id);
        this.pos = buf.readBlockPos();
        this.owner = buf.readBoolean();
        this.remote = true;
        this.level = inventory.player.level();
        this.viewerNation = null;
        BlockEntity entity = level.getBlockEntity(pos);
        this.terminal = entity instanceof TradeTerminalBlockEntity found ? found : null;
        if (owner) {
            IItemHandler stock = terminal != null ? terminal.stock() : new ItemStackHandler(TradeTerminalBlockEntity.STOCK_SLOTS);
            IItemHandler output = terminal != null ? terminal.output() : new ItemStackHandler(TradeTerminalBlockEntity.OUTPUT_SLOTS);
            IItemHandler coins = terminal != null ? terminal.coins() : new ItemStackHandler(TradeTerminalBlockEntity.COIN_SLOTS);
            IItemHandler sample = terminal != null ? terminal.sample() : new ItemStackHandler(1);
            addTerminal(stock, output, coins);
            addSlot(new SampleSlot(sample, 188, 28, true));
            addPlayer(inventory);
        } else {
            IItemHandler sample = terminal != null ? terminal.sample() : new ItemStackHandler(1);
            addSlot(new SampleSlot(sample, 8, 22, false));
        }
        addDataSlots(data);
    }

    public boolean owner() {
        return owner;
    }

    public BlockPos blockPos() {
        return pos;
    }

    public int price() {
        return remote ? mirror[0] : live(0);
    }

    public int pack() {
        return remote ? mirror[1] : live(1);
    }

    public int packsToday() {
        return remote ? mirror[2] : live(2);
    }

    public int packsYesterday() {
        return remote ? mirror[3] : live(3);
    }

    public long spursToday() {
        return join(4);
    }

    public long spursYesterday() {
        return join(6);
    }

    public long viewerCredit() {
        return join(8);
    }

    public long quotedPrice() {
        return join(10);
    }

    public boolean embargo() {
        int flag = remote ? mirror[12] : live(12);
        return flag != 0;
    }

    private long join(int lowIndex) {
        int low = remote ? mirror[lowIndex] : live(lowIndex);
        int high = remote ? mirror[lowIndex + 1] : live(lowIndex + 1);
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    private int live(int index) {
        if (terminal == null) {
            return 0;
        }
        TerminalLedger ledger = terminal.ledger();
        return switch (index) {
            case 0 -> (int) Math.min(Integer.MAX_VALUE, terminal.price());
            case 1 -> terminal.packSize();
            case 2 -> ledger.packsToday();
            case 3 -> ledger.packsYesterday();
            case 4 -> low(ledger.spursToday());
            case 5 -> high(ledger.spursToday());
            case 6 -> low(ledger.spursYesterday());
            case 7 -> high(ledger.spursYesterday());
            case 8 -> low(terminal.creditOf(viewerNation));
            case 9 -> high(terminal.creditOf(viewerNation));
            case 10 -> low(terminal.quoteFor(viewerNation).price());
            case 11 -> high(terminal.quoteFor(viewerNation).price());
            case 12 -> terminal.embargoFor(viewerNation) ? 1 : 0;
            default -> 0;
        };
    }

    private static int low(long value) {
        return (int) value;
    }

    private static int high(long value) {
        return (int) (value >>> 32);
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
        int sample = TradeTerminalBlockEntity.STOCK_SLOTS + TradeTerminalBlockEntity.OUTPUT_SLOTS + TradeTerminalBlockEntity.COIN_SLOTS;
        int playerStart = sample + 1;
        if (index <= sample) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) {
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

    private static final class SampleSlot extends SlotItemHandler {
        private final boolean editable;

        private SampleSlot(IItemHandler handler, int x, int y, boolean editable) {
            super(handler, 0, x, y);
            this.editable = editable;
        }

        @Override
        public boolean mayPickup(Player player) {
            return editable && super.mayPickup(player);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return editable && super.mayPlace(stack);
        }
    }
}
