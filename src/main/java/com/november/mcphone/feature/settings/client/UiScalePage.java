package com.november.mcphone.feature.settings.client;

import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneScale;
import com.november.mcphone.core.client.PhoneTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 「设置 → 界面大小」 —— 手机开出来多大。
 *
 * 为什么这一页不需要预览
 *
 * 拖着条改的时候，整个手机（连同这一页自己）就在跟着变——预览就是它本身。所以这一页
 * 只画一条可拖的进度条、两个加减键和当前的百分比，没有别的东西。
 *
 * 三个入口都留着，因为它们各有各的场合：条是"大概拖到那儿"，加减键是"再多一档"，
 * 而 25% 一档正好让整数倍落得到（GUI 缩放 2 配 150% 就是整 3 倍，字最清楚）。
 *
 * 窗口放不下时
 *
 * 真正生效的倍数会被窗口夹住（见 {@link PhoneScale#effective}），但配置里的数不改——
 * 玩家把窗口拉大之后应该回到他原本要的大小。夹着的时候这一页写一行"窗口放不下"，
 * 不写的话他会以为是加减键坏了：数字在变，手机不变。
 */
public final class UiScalePage {

    private static final int PAD_X = 6;

    /** 加减键的边长，也是那一行的高度 */
    private static final int BTN = 14;

    /** 进度条高度。比按钮矮一截，看着才像"条"而不是第三个按钮 */
    private static final int BAR_H = 6;

    /** 拖动时按 5% 对齐：手拖不出 1% 的精度，对齐之后数字不会跳得没规律 */
    private static final int DRAG_SNAP = 5;

    /** 上一帧算出来的几何，点击与拖动时复用 */
    private int minusX, plusX, rowY, barX, barW;
    private int resetY;

    /** 正拖着那个条 */
    private boolean dragging;

    /** 进入这一页 */
    public void open() {
        dragging = false;
    }

    public void close() {
        dragging = false;
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop,
                       int screenW, int screenH, int statusH, int navH,
                       int mouseX, int mouseY, Font font,
                       int windowW, int windowH) {

        final int x = phoneLeft + PAD_X;
        final int w = screenW - PAD_X * 2;
        int y = phoneTop + statusH + 2;

        //  标题 + 当前值 
        g.drawString(font, Component.translatable("mcphone.settings.ui_scale").getString(),
                x, y, FontPalette.title(), true);

        String value = PhoneScale.percent() + "%";
        g.drawString(font, value, x + w - font.width(value), y, FontPalette.confirm(), false);
        y += font.lineHeight + 4;

        g.fill(x, y, x + w, y + 1, PhoneTheme.COLOR_DIVIDER);
        y += 6;

        //  加减键 + 进度条 
        rowY = y;
        minusX = x;
        plusX = x + w - BTN;
        barX = x + BTN + 4;
        barW = w - (BTN + 4) * 2;

        drawStepButton(g, font, minusX, rowY, "−", PhoneScale.percent() > PhoneScale.MIN_PERCENT,
                mouseX, mouseY);
        drawStepButton(g, font, plusX, rowY, "+", PhoneScale.percent() < PhoneScale.MAX_PERCENT,
                mouseX, mouseY);

        int barY = rowY + (BTN - BAR_H) / 2;
        g.fill(barX, barY, barX + barW, barY + BAR_H, PhoneTheme.COLOR_BUTTON_DISABLED);

        float t = (float) (PhoneScale.percent() - PhoneScale.MIN_PERCENT)
                / (PhoneScale.MAX_PERCENT - PhoneScale.MIN_PERCENT);
        int fill = Math.round(barW * t);
        if (fill > 0) g.fill(barX, barY, barX + fill, barY + BAR_H, PhoneTheme.COLOR_PAGE_DOT_ACTIVE);

        // 滑块：压在填充的末端，拖起来看得见自己在拖什么
        int knobX = Math.clamp(barX + fill - 1, barX, barX + barW - 2);
        g.fill(knobX, rowY, knobX + 2, rowY + BTN, FontPalette.title());

        y = rowY + BTN + 4;

        //  被窗口夹住时说一声 
        if (PhoneScale.clampedByWindow(windowW, windowH)) {
            int real = Math.round(PhoneScale.fit(windowW, windowH) * 100);
            g.drawString(font, GuiUtil.truncate(font,
                            Component.translatable("mcphone.settings.ui_scale_clamped", real + "%").getString(), w),
                    x, y, FontPalette.notice(), false);
        } else {
            g.drawString(font, GuiUtil.truncate(font,
                            Component.translatable("mcphone.settings.ui_scale_hint").getString(), w),
                    x, y, FontPalette.dim(), false);
        }
        y += font.lineHeight + 6;

        //  还原默认 
        resetY = y;
        boolean resetHovered = GuiUtil.hit(mouseX, mouseY, x, resetY, w, BTN);
        if (resetHovered) g.fill(x, resetY, x + w, resetY + BTN, PhoneTheme.COLOR_ROW_HOVER);

        String reset = Component.translatable("mcphone.settings.ui_scale_reset",
                PhoneScale.DEFAULT_PERCENT + "%").getString();
        g.drawString(font, GuiUtil.truncate(font, reset, w - 4),
                x + (w - Math.min(font.width(reset), w - 4)) / 2,
                resetY + (BTN - font.lineHeight) / 2,
                PhoneScale.percent() == PhoneScale.DEFAULT_PERCENT
                        ? FontPalette.dim() : FontPalette.body(),
                false);
    }

    private void drawStepButton(GuiGraphics g, Font font, int x, int y, String glyph,
                                boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && GuiUtil.hit(mouseX, mouseY, x, y, BTN, BTN);
        g.fill(x, y, x + BTN, y + BTN,
                hovered ? PhoneTheme.COLOR_ROW_HOVER : PhoneTheme.COLOR_BUTTON_DISABLED);
        g.drawString(font, glyph, x + (BTN - font.width(glyph)) / 2,
                y + (BTN - font.lineHeight) / 2,
                enabled ? FontPalette.title() : FontPalette.dim(), false);
    }

    /** 点一下。落在条上等于"跳到这儿"并开始拖 */
    public void mouseClicked(double mx, double my) {
        if (GuiUtil.hit(mx, my, minusX, rowY, BTN, BTN)) {
            PhoneScale.nudge(-PhoneScale.STEP_PERCENT);
            return;
        }
        if (GuiUtil.hit(mx, my, plusX, rowY, BTN, BTN)) {
            PhoneScale.nudge(PhoneScale.STEP_PERCENT);
            return;
        }
        if (GuiUtil.hit(mx, my, minusX, resetY, barW + (BTN + 4) * 2, BTN)) {
            PhoneScale.setPercent(PhoneScale.DEFAULT_PERCENT);
            return;
        }
        // 条本身放宽一点点命中：它只有 6 像素高，按整行算才点得准
        if (GuiUtil.hit(mx, my, barX, rowY, barW, BTN)) {
            dragging = true;
            applyFromX(mx);
        }
    }

    /** 拖动。只认横坐标——这条只有横向有意义 */
    public boolean mouseDragged(double mx) {
        if (!dragging) return false;
        applyFromX(mx);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }

    private void applyFromX(double mx) {
        if (barW <= 0) return;
        float t = (float) Math.clamp((mx - barX) / barW, 0.0, 1.0);
        int raw = Math.round(PhoneScale.MIN_PERCENT
                + t * (PhoneScale.MAX_PERCENT - PhoneScale.MIN_PERCENT));
        PhoneScale.setPercent(Math.round((float) raw / DRAG_SNAP) * DRAG_SNAP);
    }
}
