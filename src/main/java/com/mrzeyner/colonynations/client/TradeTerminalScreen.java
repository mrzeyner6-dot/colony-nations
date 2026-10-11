package com.mrzeyner.colonynations.client;

import com.mrzeyner.colonynations.trade.TradeBlocks;
import com.mrzeyner.colonynations.trade.TradeTerminalMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Owner view is the three inventories plus a goods/price line that T25 fills in.
 * A visitor sees the good, the price, the terms, and their nation's credit.
 */
public final class TradeTerminalScreen extends AbstractContainerScreen<TradeTerminalMenu> {
    private static final int PANEL = 0xF0181028;
    private static final int EDGE = 0xFFB07CFF;
    private static final int SLOT = 0xFF2A2040;
    private static final int TEXT = 0xFFD8D2E8;

    public TradeTerminalScreen(TradeTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = menu.owner() ? 270 : 108;
        this.titleLabelY = 6;
        this.inventoryLabelY = menu.owner() ? TradeTerminalMenu.PLAYER_Y - 12 : 10_000;
    }

    public static void register(RegisterMenuScreensEvent event) {
        event.register(TradeBlocks.TRADE_TERMINAL_MENU.get(), TradeTerminalScreen::new);
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
            line(graphics, "gui.colony_nations.terminal.stock", 18);
            line(graphics, "gui.colony_nations.terminal.output", TradeTerminalMenu.OUTPUT_Y - 10);
            line(graphics, "gui.colony_nations.terminal.coins", TradeTerminalMenu.COIN_Y - 10);
            line(graphics, "gui.colony_nations.terminal.goods", TradeTerminalMenu.COIN_Y + 20);
            line(graphics, "gui.colony_nations.terminal.price", TradeTerminalMenu.COIN_Y + 32);
            return;
        }
        line(graphics, "gui.colony_nations.terminal.goods", 22);
        line(graphics, "gui.colony_nations.terminal.price", 36);
        line(graphics, "gui.colony_nations.terminal.terms", 50);
        graphics.drawString(font, Component.translatable("gui.colony_nations.terminal.credit", 0), 8, 64, TEXT, false);
    }

    private void line(GuiGraphics graphics, String key, int y) {
        graphics.drawString(font, Component.translatable(key), 8, y, TEXT, false);
    }
}
