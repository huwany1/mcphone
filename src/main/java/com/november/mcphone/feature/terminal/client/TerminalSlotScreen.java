package com.november.mcphone.feature.terminal.client;

import com.november.mcphone.feature.terminal.integration.Terminals;
import com.november.mcphone.feature.terminal.menu.TerminalSlotMenu;
import com.november.mcphone.feature.terminal.net.TerminalActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 终端卡槽的界面。
 *
 * 为什么没有贴图
 *
 * 整块底板是用 {@code fill} 画出来的，不走 {@code blit}。一张 GUI 背景图要 176×166 的
 * PNG，而这一页上只有一个格子和一行字——为它维护一张贴图，换个配色就得重画。用矩形画
 * 反而改起来只动几个常量。
 *
 * 格子底板是遍历 {@code menu.slots} 画的，不是把 36 个位置抄一遍：位置已经在
 * {@link TerminalSlotMenu} 里定死了，这里再写一份就是等着两边对不上。
 */
public class TerminalSlotScreen extends AbstractContainerScreen<TerminalSlotMenu> {

    //  配色。深色面板，和手机的观感一路，但这是我们自己的屏，不必跟手机主题走
    private static final int PANEL = 0xFF1B222B;
    private static final int PANEL_EDGE = 0xFF39485A;
    private static final int SLOT_BG = 0xFF0E141B;
    private static final int SLOT_EDGE = 0xFF2C3947;
    private static final int TEXT = 0xFFD8E2EC;
    private static final int TEXT_SUBTLE = 0xFF7E8C9B;

    /*
     * 纵向预算。原来提示文字在 42、按钮在 44，两者重叠 6 像素——字直接压在按钮上。
     * 一行字占 font.lineHeight（9），格子连边框占 18，按钮 20，各段之间留 2：
     *
     *     标题        6 … 15
     *     卡槽(含边) 17 … 35     SLOT_Y = 18
     *     提示文字   39 … 48     HINT_Y
     *     按钮       50 … 70     BUTTON_Y + BUTTON_H
     *     「物品栏」  72 … 81     inventoryLabelY = imageHeight - 94，原版算式
     *     背包(含边) 83 … 137
     *     快捷栏     141 … 159
     */
    private static final int HINT_Y = TerminalSlotMenu.SLOT_Y + 21;
    private static final int BUTTON_X = 8;
    private static final int BUTTON_Y = 50;
    private static final int BUTTON_W = 160;
    private static final int BUTTON_H = 20;

    private Button openButton;

    public TerminalSlotScreen(TerminalSlotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        // 与原版同一个算式，这样"物品栏"那行字和下面的格子对得上
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        openButton = Button.builder(
                        Component.translatable("mcphone.terminal.open"),
                        b -> PacketDistributor.sendToServer(
                                new TerminalActionPacket(TerminalActionPacket.Action.OPEN_TERMINAL)))
                .bounds(leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H)
                .build();
        addRenderableWidget(openButton);
    }

    /**
     * 卡槽和背包里都没有终端时，按钮才点不动。
     *
     * 背包也算，是因为服务端本来就会退回背包里第一台（见 TerminalOpener）。点 App 不再
     * 自动开背包里那台之后，这个按钮就是那条路唯一的入口——身上明明带着终端却看到一个灰
     * 按钮，那才是真的没得用。
     *
     * 每帧问一次而不是在插拔时更新：格子内容是服务端同步过来的，客户端不知道它什么时候
     * 变；每帧读一次现成的值，代价可以忽略，也不会漏掉任何一次变化。
     */
    @Override
    public void containerTick() {
        super.containerTick();
        if (openButton != null) {
            openButton.active = !menu.getTerminal().isEmpty() || carriesTerminal();
        }
    }

    /**
     * 背包里有没有一台<b>开得了的</b>终端。
     *
     * 范围与服务端那一级对齐：Inventory 的全部槽位，含副手与盔甲位。认哪些牌子、以及
     * "认得出但远程开不了"（Tom's 的基础无线终端）都交给 {@link Terminals} 判断——这个类
     * 不认识任何一家存储模组，三家全没装时它也要能加载。
     */
    private boolean carriesTerminal() {
        if (minecraft == null || minecraft.player == null) return false;
        return Terminals.anyOpenableIn(minecraft.player.getInventory());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // 面板
        g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, PANEL_EDGE);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);

        // 每个格子的底板。位置从 menu 里读，不在这儿重写一遍
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
            g.fill(x, y, x + 16, y + 16, SLOT_BG);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        // 卡槽旁边那行说明。空着和装着说的不是一回事
        Component hint = menu.getTerminal().isEmpty()
                ? Component.translatable("mcphone.terminal.hint_empty")
                : Component.translatable("mcphone.terminal.hint_installed");
        g.drawString(font, hint, BUTTON_X, HINT_Y, TEXT_SUBTLE, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
