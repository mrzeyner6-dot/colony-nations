package com.mrzeyner.colonynations.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.compat.create.CreateStations;
import com.mrzeyner.colonynations.compat.minecolonies.ChunkNation;
import com.mrzeyner.colonynations.compat.numismatics.NumismaticsCoins;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.PopulationTax;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationRegistry;
import com.mrzeyner.colonynations.network.MenuLimits;
import com.mrzeyner.colonynations.network.TerminalCreditsPayload;
import com.mrzeyner.colonynations.network.TerminalVisitPayload;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Stock, coin receiver, output, and the nation this terminal belongs to.
 * The sample slot is the good for sale. Coins move only while a foreign train is stopped at a nearby station.
 */
public final class TradeTerminalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STOCK_SLOTS = 27;
    public static final int COIN_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 9;

    private final ItemStackHandler stock = new Notifying(STOCK_SLOTS, true);
    private final ItemStackHandler coins = new Notifying(COIN_SLOTS, false);
    private final ItemStackHandler output = new Notifying(OUTPUT_SLOTS, true);
    private final ItemStackHandler sample = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (!reading) {
                copySample();
            }
        }
    };
    private final TradeTerminalAutomation.Insert insert = new TradeTerminalAutomation.Insert(this);
    private final TradeTerminalAutomation.Output extract = new TradeTerminalAutomation.Output(this);
    private final Map<UUID, Long> credits = new LinkedHashMap<>();
    private ItemStack goods = ItemStack.EMPTY;
    private UUID nationId;
    private long price;
    private int packSize = 1;
    private TerminalLedger ledger = TerminalLedger.unset();
    private String creditSnapshot = "";
    private TerminalStation.Binding visit = TerminalStation.Binding.none();
    private String visitSnapshot = "";
    private boolean reading;

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

    public ItemStackHandler sample() {
        return sample;
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

    public long price() {
        return price;
    }

    public int packSize() {
        return packSize;
    }

    public TerminalLedger ledger() {
        return ledger;
    }

    public long creditOf(@Nullable UUID buyer) {
        if (buyer == null) {
            return 0L;
        }
        return credits.getOrDefault(buyer, 0L);
    }

    public Map<UUID, Long> credits() {
        return Map.copyOf(credits);
    }

    /**
     * Who is paying right now: the train owner's nation, that player when they have no nation,
     * or {@link TerminalTrade#UNOWNED} when the train has no owner. Null when the terminal is not selling.
     */
    public UUID buyer() {
        return visit.trades() ? visit.buyer() : null;
    }

    public boolean acceptsGoods(ItemStack stack) {
        return !goods.isEmpty() && stack != null && !stack.isEmpty() && ItemStack.isSameItemSameComponents(goods, stack);
    }

    public void setOffer(long nextPrice, int nextPack) {
        int maxStack = goods.isEmpty() ? 64 : Math.max(1, goods.getMaxStackSize());
        price = TerminalTrade.clampPrice(nextPrice);
        packSize = TerminalTrade.clampPack(nextPack, maxStack);
        setChanged();
    }

    public void adoptLand(@Nullable UUID landNation) {
        UUID next = TerminalNation.follow(nationId, landNation);
        if (next == null ? nationId != null : !next.equals(nationId)) {
            nationId = next;
            setChanged();
            if (level instanceof ServerLevel server) {
                TerminalDirectory.put(server, worldPosition, nationId);
            }
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
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long time = server.getGameTime();
        if (time % 20L == 0L) {
            adoptLand(ChunkNation.of(server, worldPosition));
        }
        if (time % 10L == 0L) {
            exchange(server);
        }
    }

    public void sendCredits(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, creditsPayload());
        creditSnapshot = snapshot();
    }

    public void refreshVisit(ServerPlayer player) {
        NationSavedData data = NationSavedData.get(player.server);
        if (!data.unreadable()) {
            visit = CreateStations.bind(
                    player.serverLevel(), worldPosition, Config.TERMINAL_STATION_RADIUS.get(), nationId, data.registry());
        }
        visitSnapshot = visit.token();
        PacketDistributor.sendToPlayer(player, visitPayload(data.unreadable() ? null : data.registry()));
    }

    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, stock);
        drop(level, pos, coins);
        drop(level, pos, output);
        drop(level, pos, sample);
    }

    private void exchange(ServerLevel server) {
        NationSavedData data = NationSavedData.get(server.getServer());
        if (data.unreadable()) {
            return;
        }
        long today = server.getServer().overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
        TerminalLedger rolled = ledger.onDay(today);
        boolean dayChanged = !rolled.equals(ledger);
        ledger = rolled;
        TerminalStation.Binding next = CreateStations.bind(
                server, worldPosition, Config.TERMINAL_STATION_RADIUS.get(), nationId, data.registry());
        boolean visitChanged = !next.token().equals(visit.token());
        visit = next;
        boolean selling = visit.trades() && !goods.isEmpty() && price > 0L && packSize > 0;
        if (!selling) {
            hold(server, data.registry(), dayChanged, visitChanged);
            return;
        }
        TradeTerms.Quote quote = quote(data, visit);
        if (!quote.allowed()) {
            hold(server, data.registry(), dayChanged, visitChanged);
            return;
        }
        UUID buyer = visit.buyer();
        TerminalTrade.Exchange deal = TerminalTrade.exchange(
                coinSpurs(), creditOf(buyer), quote.price(), packSize, countGoods(stock), outputRoom());
        if (!deal.selling()) {
            hold(server, data.registry(), dayChanged, visitChanged);
            return;
        }
        if (deal.treasurySpurs() > 0L && !payTreasury(server, deal.treasurySpurs())) {
            hold(server, data.registry(), dayChanged, visitChanged);
            return;
        }
        if (deal.treasurySpurs() > 0L) {
            clear(coins);
        }
        if (deal.packs() > 0) {
            moveGoods(deal.packs() * packSize);
        }
        storeCredit(buyer, deal.credit());
        ledger = ledger.record(deal.packs(), deal.treasurySpurs());
        if (nationId != null && (deal.packs() > 0 || deal.treasurySpurs() > 0L)) {
            data.recordTrade(nationId, today, deal.packs(), deal.treasurySpurs());
        }
        setChanged();
        syncCredits(server, true);
        syncVisit(server, data.registry(), visitChanged);
    }

    private TradeTerms.Quote quote(NationSavedData data, TerminalStation.Binding binding) {
        int max = Config.MAX_TARIFF_PERCENT.get();
        if (binding.kind() != TerminalStation.Kind.NATION || nationId == null || binding.buyer() == null) {
            return TradeTerms.quote(price, 0, false, true, max);
        }
        TradePolicy.Duty duty = data.tradePolicy(nationId).duty(binding.buyer());
        return TradeTerms.quote(price, duty.tariff(), duty.embargo(), false, max);
    }

    public TradeTerms.Quote quoteFor(@Nullable UUID viewer) {
        int max = Config.MAX_TARIFF_PERCENT.get();
        if (!(level instanceof ServerLevel server) || nationId == null || viewer == null) {
            return TradeTerms.quote(price, 0, false, true, max);
        }
        NationSavedData data = NationSavedData.get(server.getServer());
        if (data.registry().byId(viewer).isEmpty()) {
            return TradeTerms.quote(price, 0, false, true, max);
        }
        TradePolicy.Duty duty = data.tradePolicy(nationId).duty(viewer);
        return TradeTerms.quote(price, duty.tariff(), duty.embargo(), false, max);
    }

    public boolean embargoFor(@Nullable UUID viewer) {
        if (!(level instanceof ServerLevel server) || nationId == null || viewer == null) {
            return false;
        }
        NationSavedData data = NationSavedData.get(server.getServer());
        if (data.registry().byId(viewer).isEmpty()) {
            return false;
        }
        return data.tradePolicy(nationId).duty(viewer).embargo();
    }

    public int stockCount() {
        return countGoods(stock);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            TerminalDirectory.put(server, worldPosition, nationId);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            TerminalDirectory.remove(server, worldPosition);
        }
    }

    private void hold(ServerLevel server, NationRegistry registry, boolean dayChanged, boolean visitChanged) {
        if (dayChanged) {
            setChanged();
        }
        if (dayChanged || visitChanged) {
            syncVisit(server, registry, false);
        }
    }

    private boolean payTreasury(ServerLevel server, long spurs) {
        if (nationId == null) {
            return false;
        }
        NationSavedData data = NationSavedData.get(server.getServer());
        Nation nation = data.registry().byId(nationId).orElse(null);
        if (nation == null) {
            return false;
        }
        PopulationTax.Deposit deposit = PopulationTax.deposit(nation.treasury(), spurs);
        data.registry().setTreasury(nation.id(), deposit.treasury());
        data.setDirty();
        if (deposit.overflow()) {
            ColonyNations.LOGGER.warn("Treasury of {} overflowed while a trade terminal paid in", nation.name());
        }
        return true;
    }

    private void storeCredit(UUID buyer, long credit) {
        if (buyer == null || credit <= 0L) {
            credits.remove(buyer);
            return;
        }
        credits.put(buyer, credit);
    }

    private long coinSpurs() {
        long total = 0L;
        for (int slot = 0; slot < coins.getSlots(); slot++) {
            long part = NumismaticsCoins.stackSpurs(coins.getStackInSlot(slot));
            try {
                total = Math.addExact(total, part);
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    private int countGoods(ItemStackHandler handler) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (acceptsGoods(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int outputRoom() {
        if (goods.isEmpty()) {
            return 0;
        }
        int max = Math.max(1, goods.getMaxStackSize());
        int room = 0;
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.getStackInSlot(slot);
            if (stack.isEmpty()) {
                room += max;
            } else if (acceptsGoods(stack)) {
                room += Math.max(0, max - stack.getCount());
            }
        }
        return room;
    }

    private void moveGoods(int amount) {
        int left = amount;
        while (left > 0) {
            int slot = -1;
            for (int index = 0; index < stock.getSlots(); index++) {
                if (acceptsGoods(stock.getStackInSlot(index))) {
                    slot = index;
                    break;
                }
            }
            if (slot < 0) {
                return;
            }
            ItemStack stack = stock.getStackInSlot(slot);
            int take = Math.min(left, stack.getCount());
            ItemStack piece = stack.copyWithCount(take);
            ItemStack remainder = piece;
            for (int index = 0; index < output.getSlots() && !remainder.isEmpty(); index++) {
                remainder = output.insertItem(index, remainder, false);
            }
            int moved = take - remainder.getCount();
            if (moved <= 0) {
                return;
            }
            stack.shrink(moved);
            stock.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            left -= moved;
        }
    }

    private void copySample() {
        ItemStack stack = sample.getStackInSlot(0);
        goods = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (!goods.isEmpty()) {
            packSize = TerminalTrade.clampPack(packSize, goods.getMaxStackSize());
        }
        setChanged();
    }

    private void syncCredits(ServerLevel server, boolean force) {
        String next = snapshot();
        if (!force && next.equals(creditSnapshot)) {
            return;
        }
        creditSnapshot = next;
        TerminalCreditsPayload payload = creditsPayload();
        for (ServerPlayer player : server.players()) {
            if (player.containerMenu instanceof TradeTerminalMenu menu
                    && worldPosition.equals(menu.blockPos())
                    && menu.owner()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private TerminalCreditsPayload creditsPayload() {
        NationSavedData data = level instanceof ServerLevel server ? NationSavedData.get(server.getServer()) : null;
        List<TerminalCreditsPayload.Line> lines = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : credits.entrySet()) {
            if (entry.getValue() <= 0L || lines.size() >= TerminalCreditsPayload.MAX_LINES) {
                continue;
            }
            lines.add(creditLine(data, entry.getKey(), entry.getValue()));
        }
        return new TerminalCreditsPayload(worldPosition, lines);
    }

    private TerminalCreditsPayload.Line creditLine(NationSavedData data, UUID buyer, long credit) {
        if (TerminalTrade.COUNTER.equals(buyer)) {
            return new TerminalCreditsPayload.Line(buyer, TerminalCreditsPayload.ROLE_COUNTER, "", credit);
        }
        if (TerminalTrade.UNOWNED.equals(buyer)) {
            return new TerminalCreditsPayload.Line(buyer, TerminalCreditsPayload.ROLE_UNOWNED, "", credit);
        }
        if (data != null) {
            Nation nation = data.registry().byId(buyer).orElse(null);
            if (nation != null) {
                return new TerminalCreditsPayload.Line(buyer, TerminalCreditsPayload.ROLE_NATION, nation.name(), credit);
            }
        }
        return new TerminalCreditsPayload.Line(buyer, TerminalCreditsPayload.ROLE_PRIVATE, playerName(buyer), credit);
    }

    private String playerName(UUID id) {
        if (!(level instanceof ServerLevel server) || id == null) {
            return "";
        }
        ServerPlayer online = server.getServer().getPlayerList().getPlayer(id);
        if (online != null) {
            return MenuLimits.clip(online.getGameProfile().getName(), MenuLimits.LABEL);
        }
        if (server.getServer().getProfileCache() == null) {
            return "";
        }
        return server.getServer().getProfileCache().get(id).map(GameProfile::getName).map(name -> MenuLimits.clip(name, MenuLimits.LABEL)).orElse("");
    }

    private void syncVisit(ServerLevel server, NationRegistry registry, boolean force) {
        String token = visit.token();
        if (!force && token.equals(visitSnapshot)) {
            return;
        }
        visitSnapshot = token;
        TerminalVisitPayload payload = visitPayload(registry);
        for (ServerPlayer player : server.players()) {
            if (player.containerMenu instanceof TradeTerminalMenu menu && worldPosition.equals(menu.blockPos())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private TerminalVisitPayload visitPayload(@Nullable NationRegistry registry) {
        String nation = "";
        if (registry != null && visit.ownerNation() != null) {
            nation = registry.byId(visit.ownerNation()).map(Nation::name).orElse("");
        }
        return new TerminalVisitPayload(
                worldPosition,
                visit.station(),
                visit.stationName(),
                visit.train(),
                visit.trainName(),
                nation,
                visit.kind().ordinal(),
                TerminalTrade.UNOWNED.equals(visit.buyer()));
    }

    private String snapshot() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<UUID, Long> entry : credits.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append(';');
        }
        return builder.toString();
    }

    private static void clear(ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
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
        UUID viewer = null;
        if (player instanceof ServerPlayer serverPlayer) {
            NationSavedData data = NationSavedData.get(serverPlayer.server);
            viewer = data.registry().byOwner(serverPlayer.getUUID()).map(Nation::id).orElse(serverPlayer.getUUID());
        }
        return new TradeTerminalMenu(id, inventory, this, owner, viewer);
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
        tag.putLong("Price", price);
        tag.putInt("Pack", packSize);
        tag.putLong("StatDay", ledger.day());
        tag.putInt("PacksToday", ledger.packsToday());
        tag.putLong("SpursToday", ledger.spursToday());
        tag.putInt("PacksYesterday", ledger.packsYesterday());
        tag.putLong("SpursYesterday", ledger.spursYesterday());
        ListTag rows = new ListTag();
        for (Map.Entry<UUID, Long> entry : credits.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putLong("Most", entry.getKey().getMostSignificantBits());
            row.putLong("Least", entry.getKey().getLeastSignificantBits());
            row.putLong("Credit", entry.getValue());
            rows.add(row);
        }
        tag.put("Credits", rows);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        reading = true;
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
        if (!goods.isEmpty()) {
            goods.setCount(1);
        }
        sample.setStackInSlot(0, goods.copy());
        price = TerminalTrade.clampPrice(tag.getLong("Price"));
        packSize = tag.contains("Pack") ? tag.getInt("Pack") : 1;
        packSize = TerminalTrade.clampPack(packSize, goods.isEmpty() ? 64 : goods.getMaxStackSize());
        long day = tag.contains("StatDay") ? tag.getLong("StatDay") : Long.MIN_VALUE;
        ledger = new TerminalLedger(
                day,
                Math.max(0, tag.getInt("PacksToday")),
                Math.max(0L, tag.getLong("SpursToday")),
                Math.max(0, tag.getInt("PacksYesterday")),
                Math.max(0L, tag.getLong("SpursYesterday")));
        credits.clear();
        ListTag rows = tag.getList("Credits", Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            long credit = row.getLong("Credit");
            if (credit > 0L) {
                credits.put(new UUID(row.getLong("Most"), row.getLong("Least")), credit);
            }
        }
        reading = false;
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
