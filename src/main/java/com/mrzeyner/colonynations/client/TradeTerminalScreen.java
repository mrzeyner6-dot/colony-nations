package com.mrzeyner.colonynations.client;

import java.util.List;

import com.mrzeyner.colonynations.network.TerminalCreditsPayload;
import com.mrzeyner.colonynations.network.TerminalSettingsPayload;
import com.mrzeyner.colonynations.trade.TradeBlocks;
import com.mrzeyner.colonynations.trade.TradeTerminalMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Owner view is the inventories, the sample, the price, and every buyer's credit.
 * A visitor sees the good, the price, the terms, and their own nation's credit.
 */
public final class TradeTerminalScreen extends AbstractContainerScreen<TradeTerminalMenu> {
    private static final int PANEL = 0xF0181028;
    private static final int EDGE = 0xFFB07CFF;
    private static final int SLOT = 0xFF2A2040;
    private static final int TEXT = 0xFFD8D2E8;
    private static final int VALUE = 0xFFE0C3FF;

    private EditBox priceBox;
    private EditBox packBox;

    public TradeTerminalScreen(TradeTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.owner() ? 360 : 176;
        this.imageHeight = menu.owner() ? 270 : 120;
        this.titleLabelY = 6;
        this.inventoryLabelY = menu.owner() ? TradeTerminalMenu.PLAYER_Y - 12 : 10_000;
    }

    public static void register(RegisterMenuScreensEvent event) {
        event.register(TradeBlocks.TRADE_TERMINAL_MENU.get(), TradeTerminalScreen::new);
    }

    @Override
    protected void init() {
        super.init();
        if (!menu.owner()) {
            return;
        }
        priceBox = box(leftPos + 184, topPos + 62, "gui.colony_nations.terminal.price_field");
        packBox = box(leftPos + 184, topPos + 96, "gui.colony_nations.terminal.pack_field");
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.terminal.apply"), button -> apply())
                .bounds(leftPos + 184, topPos + 120, 96, 20)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!menu.owner() || priceBox == null) {
            return;
        }
        if (!priceBox.isFocused()) {
            priceBox.setValue(Long.toString(menu.price()));
        }
        if (!packBox.isFocused()) {
            packBox.setValue(Integer.toString(Math.max(1, menu.pack())));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, EDGE);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        for (Slot slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, SLOT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.owner()) {
            line(graphics, "gui.colony_nations.terminal.stock", 8, 18);
            line(graphics, "gui.colony_nations.terminal.output", 8, TradeTerminalMenu.OUTPUT_Y - 10);
            line(graphics, "gui.colony_nations.terminal.coins", 8, TradeTerminalMenu.COIN_Y - 10);
            line(graphics, "gui.colony_nations.terminal.sample", 184, 18);
            line(graphics, "gui.colony_nations.terminal.price_field", 184, 52);
            line(graphics, "gui.colony_nations.terminal.pack_field", 184, 86);
            graphics.drawString(font, Component.translatable("gui.colony_nations.terminal.sold_today", menu.packsToday(), menu.spursToday()), 184, 148, TEXT, false);
            graphics.drawString(font, Component.translatable("gui.colony_nations.terminal.sold_yesterday", menu.packsYesterday(), menu.spursYesterday()), 184, 160, TEXT, false);
            line(graphics, "gui.colony_nations.terminal.credits", 184, 178);
            List<TerminalCreditsPayload.Line> credits = TerminalCreditsClient.lines(menu.blockPos());
            int y = 190;
            if (credits.isEmpty()) {
                line(graphics, "gui.colony_nations.terminal.credits_empty", 184, y);
                return;
            }
            for (TerminalCreditsPayload.Line credit : credits) {
                if (y > imageHeight - 12) {
                    break;
                }
                Component name = credit.name().isEmpty()
                        ? Component.translatable("gui.colony_nations.terminal.counter")
                        : Component.literal(credit.name());
                graphics.drawString(font, Component.translatable("gui.colony_nations.terminal.credit_line", name, credit.credit()), 184, y, VALUE, false);
                y += 12;
            }
            return;
        }
        Component price = menu.price() <= 0
                ? Component.translatable("gui.colony_nations.terminal.price")
                : Component.translatable("gui.colony_nations.terminal.price_set", menu.price(), menu.pack());
        graphics.drawString(font, price, 32, 26, TEXT, false);
        line(graphics, "gui.colony_nations.terminal.terms", 8, 48);
        graphics.drawString(font, Component.translatable("gui.colony_nations.terminal.credit", menu.viewerCredit()), 8, 64, TEXT, false);
    }

    private void apply() {
        String price = priceBox == null ? "" : priceBox.getValue();
        String pack = packBox == null ? "" : packBox.getValue();
        PacketDistributor.sendToServer(new TerminalSettingsPayload(menu.blockPos(), price, pack));
    }

    private EditBox box(int x, int y, String narration) {
        EditBox box = new EditBox(font, x, y, 96, 18, Component.translatable(narration));
        box.setMaxLength(12);
        box.setTextColor(VALUE);
        addRenderableWidget(box);
        return box;
    }

    private void line(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(font, Component.translatable(key), x, y, TEXT, false);
    }
}
