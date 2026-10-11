package com.mrzeyner.colonynations.client;

import java.util.List;
import java.util.Locale;

import com.mrzeyner.colonynations.economy.FreeCityBuy;
import com.mrzeyner.colonynations.economy.PopulationTax;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.economy.TaxHappiness;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.network.NationActionPayload;
import com.mrzeyner.colonynations.network.NationMenuRequestPayload;
import com.mrzeyner.colonynations.network.NationOverviewPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Nation window. Vanilla widgets on a dark panel; the server owns every change.
 */
public final class NationMenuScreen extends Screen {
    private static final int PANEL_W = 392;
    private static final int PANEL_H = 230;
    private static final int PAGE = 3;
    private static final int FREE_PAGE = 2;
    private static final int TEXT = 0xFFD8D2E8;
    private static final int VALUE = 0xFFE0C3FF;
    private static final int ACCENT = 0xFFB07CFF;
    private static final int ERROR = 0xFFFF7A90;
    private static final int[] PRESETS = {
            0xB07CFF, 0x7C5CBF, 0xC9A6FF, 0x5B8CFF, 0x3DDC97, 0x8D8D8D,
            0xF4D35E, 0xFF9F1C, 0xE63946, 0xFF7A90, 0xF72585, 0xE8E8E8
    };

    private NationOverviewPayload.Overview view;
    private int tab;
    private int cityPage;
    private int freePage;
    private int offerPage;
    private int draftTax;
    private Button taxApply;
    private int serverTax = Integer.MIN_VALUE;
    private boolean confirmDisband;
    private NationOverviewPayload.CityLine confirmCity;
    private NationOverviewPayload.FreeLine confirmFree;
    private boolean dirty = true;
    private int left;
    private int top;

    public NationMenuScreen(NationOverviewPayload.Overview view) {
        super(Component.translatable("gui.colony_nations.title"));
        this.view = view == null ? NationOverviewPayload.Overview.none() : view;
        this.draftTax = this.view.taxRate();
        this.serverTax = this.view.taxRate();
    }

    public void apply(NationOverviewPayload.Overview overview) {
        view = overview == null ? NationOverviewPayload.Overview.none() : overview;
        if (!view.member()) {
            tab = 0;
            confirmDisband = false;
            confirmCity = null;
            confirmFree = null;
        }
        if (view.taxRate() != serverTax) {
            serverTax = view.taxRate();
            draftTax = view.taxRate();
        }
        cityPage = clampPage(cityPage, view.cities().size(), cityPageSize());
        freePage = clampPage(freePage, view.frees().size(), FREE_PAGE);
        offerPage = clampPage(offerPage, view.offers().size());
        dirty = true;
    }

    @Override
    protected void init() {
        dirty = true;
        rebuild();
    }

    @Override
    public void tick() {
        if (dirty) {
            rebuild();
        }
    }

    private void rebuild() {
        dirty = false;
        clearWidgets();
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        if (!view.member()) {
            founding();
            return;
        }
        tabs();
        if (confirmDisband || confirmCity != null || confirmFree != null) {
            confirm();
            return;
        }
        switch (tab) {
            case 1 -> cities();
            case 2 -> territory();
            case 3 -> treasury();
            case 4 -> taxes();
            case 5 -> offers();
            default -> overview();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF014101C);
        graphics.fill(left, top, left + PANEL_W, top + 1, ACCENT);
        graphics.fill(left, top + PANEL_H - 1, left + PANEL_W, top + PANEL_H, ACCENT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + 8, top + 8, ACCENT, false);
        if (!view.member()) {
            drawLines(graphics, left + 16, top + 32, Component.translatable("gui.colony_nations.found.lead"));
            return;
        }
        if (confirmDisband || confirmCity != null || confirmFree != null) {
            if (confirmDisband) {
                drawLines(graphics, left + 116, top + 40, Component.translatable("gui.colony_nations.disband.ask", view.name()));
            } else if (confirmCity != null) {
                drawLines(graphics, left + 116, top + 40, Component.translatable("gui.colony_nations.city.remove.ask", confirmCity.name()));
            } else {
                drawLines(graphics, left + 116, top + 40, Component.translatable(
                        "gui.colony_nations.city.free_ask", confirmFree.name(), confirmFree.price()));
            }
            return;
        }
        int x = left + 116;
        int y = top + 32;
        switch (tab) {
            case 0 -> renderOverview(graphics, x, y);
            case 1 -> renderCities(graphics, x, y);
            case 2 -> renderTerritory(graphics, x, y);
            case 3 -> graphics.drawString(font, Component.translatable("gui.colony_nations.treasury.balance", view.treasury()), x, y, TEXT, false);
            case 4 -> renderTax(graphics, x, y);
            case 5 -> renderOffers(graphics, x, y);
            default -> {
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (view.member() && scrollY != 0) {
            if (tab == 1) {
                if (!view.frees().isEmpty() && mouseY >= top + PANEL_H / 2.0) {
                    shift(PAGE_FREES, scrollY > 0 ? -1 : 1, view.frees().size(), FREE_PAGE);
                } else {
                    shift(PAGE_CITIES, scrollY > 0 ? -1 : 1, view.cities().size(), cityPageSize());
                }
                return true;
            }
            if (tab == 5) {
                shift(PAGE_OFFERS, scrollY > 0 ? -1 : 1, view.offers().size(), PAGE);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void founding() {
        EditBox name = box(left + 16, top + 48, PANEL_W - 32, Nation.MAX_NAME_LENGTH, "gui.colony_nations.found.name");
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.found.button"), button ->
                        send(NationActionPayload.CREATE, name.getValue(), 0L, "", 0))
                .bounds(left + 16, top + 76, 160, 20)
                .build());
    }

    private void tabs() {
        String[] keys = {
                "gui.colony_nations.tab.overview",
                "gui.colony_nations.tab.cities",
                "gui.colony_nations.tab.territory",
                "gui.colony_nations.tab.treasury",
                "gui.colony_nations.tab.tax",
                "gui.colony_nations.tab.offers"
        };
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            Component label = Component.translatable(keys[i]);
            if (i == 5 && !view.offers().isEmpty()) {
                label = Component.translatable("gui.colony_nations.tab.offers_new", view.offers().size());
            }
            if (i == tab) {
                label = Component.literal("• ").withColor(ACCENT).append(label);
            }
            addRenderableWidget(Button.builder(label, button -> select(index))
                    .bounds(left + 6, top + 26 + i * 22, 102, 20)
                    .build());
        }
    }

    private void select(int index) {
        tab = index;
        confirmDisband = false;
        confirmCity = null;
        confirmFree = null;
        dirty = true;
        if (index == 2) {
            PacketDistributor.sendToServer(new NationMenuRequestPayload(false));
        }
    }

    private void overview() {
        int x = left + 116;
        int y = top + 118;
        EditBox name = box(x, y, 120, Nation.MAX_NAME_LENGTH, "gui.colony_nations.overview.rename");
        name.setValue(view.name());
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.overview.rename_button"), button ->
                        send(NationActionPayload.RENAME, name.getValue(), 0L, "", 0))
                .bounds(x + 124, y, 100, 20)
                .build());
        EditBox color = box(x, y + 24, 120, 7, "gui.colony_nations.overview.color");
        color.setValue(String.format(Locale.ROOT, "#%06X", view.color() & 0xFFFFFF));
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.overview.color_button"), button ->
                        send(NationActionPayload.COLOR, color.getValue(), 0L, "", 0))
                .bounds(x + 124, y + 24, 100, 20)
                .build());
        for (int i = 0; i < PRESETS.length; i++) {
            int rgb = PRESETS[i];
            int px = x + (i % 6) * 18;
            int py = y + 48 + (i / 6) * 18;
            addRenderableWidget(Button.builder(Component.literal(""), button ->
                            send(NationActionPayload.COLOR, String.format(Locale.ROOT, "#%06X", rgb), 0L, "", 0))
                    .bounds(px, py, 16, 16)
                    .tooltip(Tooltip.create(Component.literal(String.format(Locale.ROOT, "#%06X", rgb))))
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.overview.disband"), button -> {
                    confirmDisband = true;
                    dirty = true;
                })
                .bounds(x, y + 86, 120, 20)
                .build());
    }

    private void cities() {
        List<NationOverviewPayload.CityLine> cities = view.cities();
        List<NationOverviewPayload.FreeLine> frees = view.frees();
        int page = cityPageSize();
        int stride = frees.isEmpty() ? 48 : 40;
        int from = cityPage * page;
        int x = left + 220;
        int y = top + 46;
        for (int i = from; i < Math.min(cities.size(), from + page); i++) {
            NationOverviewPayload.CityLine city = cities.get(i);
            int row = y + (i - from) * stride;
            addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.city.capital"), button ->
                            send(NationActionPayload.CAPITAL, "", 0L, city.dimension(), city.colonyId()))
                    .bounds(x, row, 150, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.city.remove"), button -> {
                        confirmCity = city;
                        dirty = true;
                    })
                    .bounds(x, row + 20, 150, 18)
                    .build());
        }
        if (!frees.isEmpty()) {
            int freeFrom = freePage * FREE_PAGE;
            int base = freeHeaderY() + 12;
            for (int i = freeFrom; i < Math.min(frees.size(), freeFrom + FREE_PAGE); i++) {
                NationOverviewPayload.FreeLine free = frees.get(i);
                int row = base + (i - freeFrom) * 32;
                Button join = Button.builder(Component.translatable("gui.colony_nations.city.free_join"), button -> {
                            confirmFree = free;
                            dirty = true;
                        })
                        .bounds(left + PANEL_W - 104, row, 96, 18)
                        .build();
                join.active = free.block() == FreeCityBuy.OK;
                Component tip = freeTip(free);
                if (tip != null) {
                    join.setTooltip(Tooltip.create(tip));
                }
                addRenderableWidget(join);
            }
        }
        if (pageCount(cities.size(), page) > 1) {
            pager(cities.size(), PAGE_CITIES, page);
        } else if (!frees.isEmpty() && pageCount(frees.size(), FREE_PAGE) > 1) {
            pager(frees.size(), PAGE_FREES, FREE_PAGE);
        }
    }

    private void territory() {
        int x = left + 116;
        int y = top + 112;
        Button annex = Button.builder(Component.translatable("gui.colony_nations.territory.annex"), button ->
                        send(NationActionPayload.ANNEX, "", 0L, "", 0))
                .bounds(x, y, 250, 20)
                .build();
        annex.active = view.annexCode() == NationTerritory.OK;
        annex.setTooltip(Tooltip.create(annexNote()));
        addRenderableWidget(annex);
        Button release = Button.builder(Component.translatable("gui.colony_nations.territory.release"), button ->
                        send(NationActionPayload.UNANNEX, "", 0L, "", 0))
                .bounds(x, y + 48, 250, 20)
                .build();
        release.active = view.unannexCode() == NationTerritory.OK;
        release.setTooltip(Tooltip.create(releaseNote()));
        addRenderableWidget(release);
    }

    private void treasury() {
        int x = left + 116;
        int y = top + 56;
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.treasury.hand"), button ->
                        send(NationActionPayload.DEPOSIT_HAND, "", 0L, "", 0))
                .bounds(x, y, 250, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.treasury.all"), button ->
                        send(NationActionPayload.DEPOSIT_ALL, "", 0L, "", 0))
                .bounds(x, y + 24, 250, 20)
                .build());
        EditBox amount = box(x, y + 52, 80, 18, "gui.colony_nations.treasury.amount");
        amount.setFilter(text -> text.chars().allMatch(Character::isDigit));
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.treasury.withdraw"), button -> {
                    if (amount.getValue().isBlank()) {
                        return;
                    }
                    try {
                        send(NationActionPayload.WITHDRAW, "", Long.parseLong(amount.getValue()), "", 0);
                    } catch (NumberFormatException ignored) {
                        // The field only accepts digits; an oversized number is ignored.
                    }
                })
                .bounds(x + 86, y + 52, 164, 20)
                .build());
    }

    private void taxes() {
        int x = left + 116;
        int y = top + 78;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> {
                    if (draftTax > 0) {
                        draftTax--;
                    }
                    refreshTaxApply();
                })
                .bounds(x, y, 20, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    if (draftTax < view.maxTax()) {
                        draftTax++;
                    }
                    refreshTaxApply();
                })
                .bounds(x + 64, y, 20, 20)
                .build());
        taxApply = Button.builder(Component.translatable("gui.colony_nations.tax.apply"), button ->
                        send(NationActionPayload.TAX, "", draftTax, "", 0))
                .bounds(x, y + 28, 120, 20)
                .build();
        refreshTaxApply();
        addRenderableWidget(taxApply);
    }

    /**
     * The apply button is built once per tab; the draft rate changes with the -/+ buttons,
     * so its state and tooltip are recomputed on every change.
     */
    private void refreshTaxApply() {
        if (taxApply == null) {
            return;
        }
        taxApply.active = view.canTax() && draftTax != view.taxRate();
        if (!view.canTax()) {
            taxApply.setTooltip(Tooltip.create(Component.translatable("gui.colony_nations.tax.ready", view.taxReadyDay())));
        } else if (draftTax == view.taxRate()) {
            taxApply.setTooltip(Tooltip.create(Component.translatable("gui.colony_nations.tax.same")));
        } else {
            taxApply.setTooltip(null);
        }
    }

    private void offers() {
        List<NationOverviewPayload.OfferLine> offers = view.offers();
        int from = offerPage * PAGE;
        int x = left + 116;
        int y = top + 56;
        for (int i = from; i < Math.min(offers.size(), from + PAGE); i++) {
            NationOverviewPayload.OfferLine offer = offers.get(i);
            int row = y + (i - from) * 36;
            addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.offer.accept"), button ->
                            send(NationActionPayload.ACCEPT, "", 0L, offer.dimension(), offer.colonyId()))
                    .bounds(x + 150, row, 100, 18)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.offer.decline"), button ->
                            send(NationActionPayload.DECLINE, "", 0L, offer.dimension(), offer.colonyId()))
                    .bounds(x + 150, row + 18, 100, 18)
                    .build());
        }
        pager(offers.size(), PAGE_OFFERS, PAGE);
    }

    private void confirm() {
        int x = left + 116;
        int y = top + 90;
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.confirm.yes"), button -> {
                    if (confirmDisband) {
                        send(NationActionPayload.DISBAND, "", 0L, "", 0);
                    } else if (confirmCity != null) {
                        send(NationActionPayload.REMOVE, "", 0L, confirmCity.dimension(), confirmCity.colonyId());
                    } else if (confirmFree != null) {
                        send(NationActionPayload.BUY, "", 0L, confirmFree.dimension(), confirmFree.colonyId());
                    }
                    confirmDisband = false;
                    confirmCity = null;
                    confirmFree = null;
                    dirty = true;
                })
                .bounds(x, y, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.confirm.no"), button -> {
                    confirmDisband = false;
                    confirmCity = null;
                    confirmFree = null;
                    dirty = true;
                })
                .bounds(x + 108, y, 100, 20)
                .build());
    }

    private void pager(int size, int target, int pageSize) {
        if (pageCount(size, pageSize) <= 1) {
            return;
        }
        int x = left + 116;
        int y = top + PANEL_H - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.page.prev"), button -> shift(target, -1, size, pageSize))
                .bounds(x, y, 80, 18)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.colony_nations.page.next"), button -> shift(target, 1, size, pageSize))
                .bounds(x + 88, y, 80, 18)
                .build());
    }

    private void shift(int target, int delta, int size, int pageSize) {
        if (target == PAGE_CITIES) {
            cityPage = clampPage(cityPage + delta, size, pageSize);
        } else if (target == PAGE_FREES) {
            freePage = clampPage(freePage + delta, size, pageSize);
        } else {
            offerPage = clampPage(offerPage + delta, size, pageSize);
        }
        dirty = true;
    }

    private void renderOverview(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 10, y + 10, 0xFF000000 | (view.color() & 0xFFFFFF));
        y = line(graphics, x + 14, y, Component.translatable("gui.colony_nations.overview.name", view.name()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.overview.owner", view.owner()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.overview.capital", view.capital()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.overview.treasury", view.treasury()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.overview.tax", view.taxRate()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.overview.cities", view.cityCount()));
        line(graphics, x, y, Component.translatable("gui.colony_nations.overview.land", view.cityChunks(), view.annexed(), view.limit()));
        for (int i = 0; i < PRESETS.length; i++) {
            int px = x + (i % 6) * 18;
            int py = top + 166 + (i / 6) * 18;
            graphics.fill(px, py, px + 16, py + 16, 0xFF000000 | PRESETS[i]);
        }
    }

    private void renderCities(GuiGraphics graphics, int x, int y) {
        List<NationOverviewPayload.CityLine> cities = view.cities();
        List<NationOverviewPayload.FreeLine> frees = view.frees();
        if (cities.isEmpty() && frees.isEmpty()) {
            line(graphics, x, y, Component.translatable("gui.colony_nations.city.empty"));
            return;
        }
        int page = cityPageSize();
        int stride = frees.isEmpty() ? 48 : 40;
        if (cities.isEmpty()) {
            line(graphics, x, y, Component.translatable("gui.colony_nations.city.empty"));
        } else {
            int from = cityPage * page;
            for (int i = from; i < Math.min(cities.size(), from + page); i++) {
                NationOverviewPayload.CityLine city = cities.get(i);
                int row = y + (i - from) * stride;
                Component name = Component.literal(city.name()).withColor(VALUE);
                if (city.capital()) {
                    name = name.copy().append(Component.translatable("gui.colony_nations.city.capital_mark").withColor(ACCENT));
                }
                graphics.drawString(font, name, x, row, VALUE, false);
                graphics.drawString(
                        font,
                        Component.translatable("gui.colony_nations.city.people", city.citizens(), happiness(city.happinessMilli())),
                        x,
                        row + 12,
                        TEXT,
                        false);
                int score = Math.max(0, Math.min(100, city.separatism()));
                int color = stageColor(score);
                int bar = frees.isEmpty() ? row + 26 : row + 24;
                graphics.fill(x, bar, x + 80, bar + 4, 0xFF2A2438);
                graphics.fill(x, bar, x + (int) (80 * (score / 100.0)), bar + 4, color);
                graphics.drawString(font, Integer.toString(score), x + 84, bar - 4, color, false);
            }
        }
        if (frees.isEmpty()) {
            return;
        }
        int header = freeHeaderY();
        graphics.drawString(font, Component.translatable("gui.colony_nations.city.free_header"), x, header, ACCENT, false);
        int freeFrom = freePage * FREE_PAGE;
        for (int i = freeFrom; i < Math.min(frees.size(), freeFrom + FREE_PAGE); i++) {
            NationOverviewPayload.FreeLine free = frees.get(i);
            int row = header + 12 + (i - freeFrom) * 32;
            drawWidth(
                    graphics,
                    x,
                    row,
                    PANEL_W - 116 - 112,
                    Component.translatable(
                            "gui.colony_nations.city.free_line",
                            Component.literal(free.name()).withColor(VALUE),
                            free.citizens(),
                            chunks(free.distance()),
                            free.price()));
        }
    }

    private int freeHeaderY() {
        if (view.cities().isEmpty()) {
            return top + 48;
        }
        int stride = view.frees().isEmpty() ? 48 : 40;
        return top + 46 + cityPageSize() * stride + 6;
    }

    private void renderTerritory(GuiGraphics graphics, int x, int y) {
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.territory.city", view.cityChunks()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.territory.core", view.coreChunks()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.territory.annexed", view.annexed(), view.limit()));
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.territory.upkeep", view.upkeep()));
        drawLines(graphics, x, y + 4, annexNote().copy().withColor(view.annexCode() == NationTerritory.OK ? VALUE : ERROR));
        drawLines(graphics, x, y + 52, releaseNote().copy().withColor(view.unannexCode() == NationTerritory.OK ? VALUE : ERROR));
    }

    private void renderTax(GuiGraphics graphics, int x, int y) {
        line(graphics, x + 28, y + 52, Component.literal(Integer.toString(draftTax)).withColor(VALUE));
        double factor = TaxHappiness.multiplier(
                draftTax,
                view.neutralTax(),
                view.maxTax(),
                view.bonusMilli() / 1000.0,
                view.penaltyMilli() / 1000.0);
        long income = PopulationTax.sum(List.of(view.adults()), draftTax).income();
        y = line(graphics, x, y, Component.translatable("gui.colony_nations.tax.income", income));
        line(graphics, x, y, Component.translatable("gui.colony_nations.tax.happiness", String.format(Locale.ROOT, "%.2f", factor)));
    }

    private void renderOffers(GuiGraphics graphics, int x, int y) {
        List<NationOverviewPayload.OfferLine> offers = view.offers();
        if (offers.isEmpty()) {
            line(graphics, x, y, Component.translatable("gui.colony_nations.offer.empty"));
            return;
        }
        int from = offerPage * PAGE;
        for (int i = from; i < Math.min(offers.size(), from + PAGE); i++) {
            NationOverviewPayload.OfferLine offer = offers.get(i);
            int row = y + (i - from) * 36;
            graphics.drawString(font, Component.literal(offer.name()).withColor(VALUE), x, row, VALUE, false);
            graphics.drawString(
                    font,
                    Component.translatable("gui.colony_nations.offer.deadline", offer.deadlineDay()),
                    x,
                    row + 12,
                    TEXT,
                    false);
        }
    }

    private Component annexNote() {
        return switch (view.annexCode()) {
            case NationTerritory.YOURS -> Component.translatable("command.colony_nations.annex.yours");
            case NationTerritory.FOREIGN -> Component.translatable("command.colony_nations.annex.foreign");
            case NationTerritory.CITY_LAND -> Component.translatable("command.colony_nations.annex.city");
            case NationTerritory.NOT_ADJACENT -> Component.translatable("command.colony_nations.annex.not_adjacent");
            case NationTerritory.NO_CITY -> Component.translatable("command.colony_nations.annex.no_city");
            case NationTerritory.LIMIT -> Component.translatable("command.colony_nations.annex.limit", view.limit());
            case NationTerritory.POOR -> Component.translatable("command.colony_nations.annex.poor", view.annexPrice(), view.treasury());
            default -> Component.translatable("gui.colony_nations.territory.price", view.annexPrice());
        };
    }

    private Component releaseNote() {
        return switch (view.unannexCode()) {
            case NationTerritory.NOT_YOURS -> Component.translatable("command.colony_nations.unannex.not_yours");
            case NationTerritory.CORE -> Component.translatable("command.colony_nations.unannex.core");
            case NationTerritory.DISCONNECTS -> Component.translatable("command.colony_nations.unannex.disconnects");
            default -> Component.translatable("gui.colony_nations.territory.can_release");
        };
    }

    private EditBox box(int x, int y, int width, int max, String narration) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.translatable(narration));
        box.setMaxLength(max);
        box.setTextColor(VALUE);
        addRenderableWidget(box);
        return box;
    }

    private int line(GuiGraphics graphics, int x, int y, Component text) {
        graphics.drawString(font, text, x, y, TEXT, false);
        return y + 12;
    }

    private void drawLines(GuiGraphics graphics, int x, int y, Component text) {
        for (FormattedCharSequence line : font.split(text, 250)) {
            graphics.drawString(font, line, x, y, TEXT, false);
            y += 12;
        }
    }

    private void drawWidth(GuiGraphics graphics, int x, int y, int width, Component text) {
        int drawn = 0;
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, TEXT, false);
            y += 10;
            if (++drawn == 2) {
                return;
            }
        }
    }

    private void send(byte action, String text, long amount, String dimension, int colonyId) {
        PacketDistributor.sendToServer(new NationActionPayload(action, text == null ? "" : text, amount, dimension == null ? "" : dimension, colonyId));
    }

    private static int stageColor(int score) {
        return switch (Separatism.stage(score)) {
            case UNREST -> 0xFFFF9F1C;
            case MOVEMENT, INDEPENDENCE -> 0xFFE63946;
            case CALM -> TEXT;
        };
    }

    private static String happiness(int milli) {
        if (milli < 0) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f", milli / 1000.0);
    }

    private int cityPageSize() {
        return view.frees().isEmpty() ? PAGE : FREE_PAGE;
    }

    private Component freeTip(NationOverviewPayload.FreeLine free) {
        return switch (free.block()) {
            case FreeCityBuy.TOO_FAR -> Component.translatable("gui.colony_nations.city.free_far");
            case FreeCityBuy.COOLDOWN -> Component.translatable("gui.colony_nations.city.free_cooldown", free.readyDay());
            case FreeCityBuy.POOR -> Component.translatable("gui.colony_nations.city.free_poor", free.price());
            default -> null;
        };
    }

    private static String chunks(int distance) {
        if (distance < 0 || distance == Integer.MAX_VALUE) {
            return "—";
        }
        return Integer.toString(distance);
    }

    private static int pageCount(int size) {
        return pageCount(size, PAGE);
    }

    private static int pageCount(int size, int pageSize) {
        int step = Math.max(1, pageSize);
        return Math.max(1, (size + step - 1) / step);
    }

    private static int clampPage(int page, int size) {
        return clampPage(page, size, PAGE);
    }

    private static int clampPage(int page, int size, int pageSize) {
        int pages = pageCount(size, pageSize);
        if (page < 0) {
            return 0;
        }
        return Math.min(page, pages - 1);
    }

    private static final int PAGE_CITIES = 0;
    private static final int PAGE_OFFERS = 1;
    private static final int PAGE_FREES = 2;

    /**
     * Same codes as {@link com.mrzeyner.colonynations.compat.minecolonies.NationTerritoryCommands.Preview}.
     * Duplicated here so the client class does not need to load the territory command type's extra graph
     * beyond a few ints the payload already carries.
     */
    private static final class NationTerritory {
        private static final int OK = 0;
        private static final int YOURS = 1;
        private static final int FOREIGN = 2;
        private static final int CITY_LAND = 3;
        private static final int NOT_ADJACENT = 4;
        private static final int NO_CITY = 5;
        private static final int LIMIT = 6;
        private static final int POOR = 7;
        private static final int NOT_YOURS = 1;
        private static final int CORE = 2;
        private static final int DISCONNECTS = 3;

        private NationTerritory() {
        }
    }
}
