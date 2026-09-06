package com.november.mcphone.feature.settings.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.november.mcphone.MCphone;
import com.november.mcphone.api.client.app.IPhoneApp;
import com.november.mcphone.api.client.app.RequiredMod;
import com.november.mcphone.core.client.AppHotkeys;
import com.november.mcphone.core.client.FontPalette;
import com.november.mcphone.core.client.GuiUtil;
import com.november.mcphone.core.client.PhoneScreenRegistry;
import com.november.mcphone.core.client.PhoneTheme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

/**
 * 一个 App 的管理页 —— 它是谁、谁给的、以及能对它做什么。
 *
 * 为什么要有这一页
 *
 * 在它之前，App 管理器是一个平列表，【点一行就卸载】。那有两个毛病：玩家点开这一页
 * 多半是想看看某个 App 是什么来路，结果手一抖就把它卸了；而删一张照片反倒是要点两次的
 * （见 Gallery 的删除键），同一部手机里两套规矩。
 *
 * 更要紧的是往后：每个 App 迟早要有自己的开关。没有"一个 App 一页"这个地方，那些开关
 * 只能往列表行上挤，或者散到各自的 App 里去——前者那一行只有 108 像素宽，后者等于没有
 * 统一的入口。所以先把这一页立起来，操作区做成一行一个，以后加开关就是加一行。
 *
 * 操作区：一行一个
 *
 * 现在有两行：上面是快捷键，下面是卸载。安装不在这儿——那是应用商店的事，
 * 那一页管的是"有价钱的、远程来源的"，而这里只列已经装上的。系统 App 的卸载键
 * 是灰的，并写明为什么——不写的话玩家会以为是坏了。
 *
 * 快捷键这一行是【每个 App 各绑各的】，默认未指定：点一下开始等键，按哪个是哪个，
 * ESC 清除。绑定表与"为什么不做成 KeyMapping"见
 * {@link com.november.mcphone.core.client.AppHotkeys}。
 *
 * 读第三方 App 的元数据一律兜住
 *
 * getVersion / getAuthor / getDescription 都是附属实现的，抛什么全凭它们高兴。
 * 这一页的全部内容都来自这些方法，不兜的话一个坏附属能让整页画不出来——而玩家
 * 恰恰是为了搞清楚"这个 App 有什么毛病"才点进来的。见 {@link #safe}。
 */
public final class AppManagerDetail {

    private static final int PAD = 6;
    private static final int BIG_ICON = 32;
    private static final int BUTTON_H = 16;

    private IPhoneApp app;

    /** 卸载键：第一次点上膛，第二次才真卸。与相册删照片同一条规矩 */
    private boolean uninstallArmed;

    /** 渲染时算出来，点击时复用 */
    private int btnX, btnY, btnW;
    private boolean btnHovered;

    /** 快捷键那一行的位置与悬停，同样是渲染时算、点击时用 */
    private int keyRowY;
    private boolean keyRowHovered;

    /**
     * 正在等玩家按一个键。
     *
     * 开着的时候 PhoneScreen 会把【所有】按键先送到这里来（包括 ESC，它在这一页上
     * 的意思是"清除绑定"而不是关机），所以这个状态必须能被离开这一页、点别处、
     * 换 App 之类的动作可靠地关掉，否则玩家会发现手机的 ESC 不灵了。
     */
    private boolean capturingKey;

    /** 上一下为什么没绑上。短暂显示在那一行上，到点自己消失 */
    private String keyNotice;
    private long keyNoticeUntilMs;

    /** 提示停留多久。够读完一行短句，又不至于挡着下一次尝试 */
    private static final long NOTICE_MS = 2500L;

    /** 卸载完了，请求退回列表页，等 PhoneScreen 来取 */
    private boolean backRequest;

    /**
     * 正文往上滚了多少像素。
     *
     * 描述是附属自己写的，长度不由我们定；再加上前置与联动各占一行，正文放不下是常态。
     * 原来放不下就直接不画（{@code drawInfoLine} 里那句提前 return），玩家看不到自己
     * 缺哪个前置 —— 而那正是他点进这一页要找的答案。
     */
    private int scrollPx;

    /** 上一帧量出来的滚动上限，正文有多高只有画完才知道 */
    private int maxScroll;

    public void open(IPhoneApp target) {
        this.app = target;
        this.uninstallArmed = false;
        this.btnHovered = false;
        this.backRequest = false;
        this.scrollPx = 0;
        this.maxScroll = 0;
        this.capturingKey = false;
        this.keyNotice = null;
    }

    public void close() {
        this.app = null;
        this.uninstallArmed = false;
        // 离开这一页必须收掉：不收的话 ESC 会一直被当成"清除绑定"吃掉
        this.capturingKey = false;
        this.keyNotice = null;
    }

    public boolean consumeBackRequest() {
        boolean r = backRequest;
        backRequest = false;
        return r;
    }

    public void render(GuiGraphics g, int phoneLeft, int phoneTop,
                       int screenW, int screenH, int statusH, int navH,
                       int mouseX, int mouseY, float partialTick, Font font) {

        int x = phoneLeft + PAD;
        int y = phoneTop + statusH + 4;
        int w = screenW - PAD * 2;
        int bottom = phoneTop + screenH - navH;

        if (app == null) {
            g.drawString(font, Component.translatable("mcphone.gui.app_manager_empty").getString(),
                    x, y, FontPalette.subtle(), false);
            return;
        }

        //  头部：图标 + 名字 + 作者·版本 
        // 用 App 自己的 renderIcon 而不是直接画贴图：图标可以是自己画的、甚至是动的
        final int iconX = x;
        final int iconY = y;
        safeRun(() -> app.renderIcon(g, iconX, iconY, BIG_ICON, partialTick));

        int textX = x + BIG_ICON + 5;
        int textW = w - BIG_ICON - 5;

        String name = safe(() -> app.getDisplayName().getString(), app.getId().toString());
        g.drawString(font, GuiUtil.truncate(font, name, textW), textX, y + 2,
                FontPalette.title(), false);

        String author = safe(app::getAuthor, "");
        String version = safe(app::getVersion, "");
        String meta = author.isBlank() ? "v" + version : author + " · v" + version;
        g.drawString(font, GuiUtil.truncate(font, meta, textW),
                textX, y + 2 + font.lineHeight + 2, FontPalette.subtle(), false);

        y += BIG_ICON + 6;
        g.fill(x, y, x + w, y + 1, PhoneTheme.COLOR_DIVIDER);
        y += 4;

        //  正文：描述 + 由谁提供 + 前置/联动 
        // 操作区的位置先扣出来，正文只能画到它上面为止。两行操作 + 系统 App 那句说明
        final int bodyBottom = bottom - BUTTON_H * 2 - font.lineHeight - 10;
        final int bodyTop = y;

        scrollPx = Math.clamp(scrollPx, 0, maxScroll);
        y -= scrollPx;

        // 越界的部分交给 scissor 裁，不再"放不下就不画"——那样卸载键上方会凭空少几行
        g.enableScissor(x, bodyTop, x + w, bodyBottom);

        String desc = safe(app::getDescription, "");
        if (desc.isBlank()) desc = Component.translatable("mcphone.store.no_description").getString();
        for (var line : font.split(Component.literal(desc), w)) {
            g.drawString(font, line, x, y, FontPalette.body(), false);
            y += font.lineHeight + 1;
        }

        y += 3;
        y = drawInfoLine(g, font, x, y, w,
                Component.translatable("mcphone.gui.app_provider").getString(), providerName());

        for (RequiredMod required : PhoneScreenRegistry.requiredModsOf(app)) {
            y = drawModLine(g, font, x, y, w, "mcphone.gui.app_requires", required);
        }
        for (RequiredMod companion : PhoneScreenRegistry.companionModsOf(app)) {
            y = drawModLine(g, font, x, y, w, "mcphone.gui.app_companion", companion);
        }

        g.disableScissor();

        maxScroll = Math.max(0, (y + scrollPx) - bodyBottom);

        //  操作区：快捷键一行，卸载一行 
        renderUninstallButton(g, font, x, bottom, w, mouseX, mouseY);
        renderHotkeyRow(g, font, x, w, mouseX, mouseY);
    }

    /** 滚轮翻正文。头部与底下那两行操作不跟着滚：它们得一直够得着 */
    public boolean mouseScrolled(double scrollY, Font font) {
        int before = scrollPx;
        scrollPx = Math.clamp(scrollPx - (int) (scrollY * font.lineHeight * 3), 0, maxScroll);
        return scrollPx != before;
    }

    /** 「标签：值」一行。越界由调用方的 scissor 裁，这里只管画 */
    private static int drawInfoLine(GuiGraphics g, Font font, int x, int y, int w,
                                    String label, String value) {
        g.drawString(font, GuiUtil.truncate(font, label + " " + value, w), x, y,
                FontPalette.subtle(), false);
        return y + font.lineHeight + 1;
    }

    /** 前置 / 联动那几行：模组名 + 装没装 */
    private static int drawModLine(GuiGraphics g, Font font, int x, int y, int w,
                                   String labelKey, RequiredMod mod) {
        boolean loaded = ModList.get().isLoaded(mod.modId());
        String label = Component.translatable(labelKey).getString() + " " + mod.displayName();
        String mark = Component.translatable(loaded
                ? "mcphone.gui.app_mod_present" : "mcphone.gui.app_mod_absent").getString();

        g.drawString(font, GuiUtil.truncate(font, label, w - font.width(mark) - 4), x, y,
                FontPalette.subtle(), false);
        g.drawString(font, mark, x + w - font.width(mark), y,
                loaded ? FontPalette.confirm() : FontPalette.danger(), false);
        return y + font.lineHeight + 1;
    }

    /**
     * 快捷键那一行：左边写"快捷键"，右边写绑的是哪个键、或者"未指定"。
     *
     * 位置贴着卸载键上方，所以要在它之后画（btnY 是那边算出来的）。它和卸载键一样
     * 不跟着正文滚——绑键是这一页的操作，不是它的内容。
     */
    private void renderHotkeyRow(GuiGraphics g, Font font, int x, int w, int mouseX, int mouseY) {
        keyRowY = btnY - BUTTON_H - 2;
        keyRowHovered = GuiUtil.hit(mouseX, mouseY, x, keyRowY, w, BUTTON_H);

        if (keyRowHovered || capturingKey) {
            g.fill(x, keyRowY, x + w, keyRowY + BUTTON_H, PhoneTheme.COLOR_ROW_HOVER);
        }

        final int textY = keyRowY + (BUTTON_H - font.lineHeight) / 2;

        // 刚才那一下没绑上的话，先把理由说完再说别的
        if (keyNotice != null && System.currentTimeMillis() < keyNoticeUntilMs) {
            g.drawString(font, GuiUtil.truncate(font, keyNotice, w - 4),
                    x + 2, textY, FontPalette.danger(), false);
            return;
        }
        keyNotice = null;

        String left = Component.translatable(capturingKey
                ? "mcphone.gui.hotkey_press" : "mcphone.gui.hotkey").getString();

        String right;
        int rightColor;
        if (capturingKey) {
            // 等键的时候右边写 ESC 干什么用：这一页上它是"清除"，不是"关机"，
            // 不写的话玩家只会按 ESC 想退出，然后发现绑定没了
            right = Component.translatable("mcphone.gui.hotkey_esc_clears").getString();
            rightColor = FontPalette.dim();
        } else {
            InputConstants.Key bound = AppHotkeys.get(app.getId());
            right = bound == null
                    ? Component.translatable("mcphone.gui.hotkey_none").getString()
                    : bound.getDisplayName().getString();
            rightColor = bound == null ? FontPalette.dim() : FontPalette.confirm();
        }

        int rightW = font.width(right);
        g.drawString(font, right, x + w - rightW - 2, textY, rightColor, false);
        g.drawString(font, GuiUtil.truncate(font, left, w - rightW - 8),
                x + 2, textY, capturingKey ? FontPalette.armed() : FontPalette.body(), false);
    }

    /** 正在等玩家按键吗。PhoneScreen 据此把按键抢在 ESC 关机之前送进来 */
    public boolean isCapturingKey() {
        return capturingKey;
    }

    /**
     * 收玩家按的那一个键。
     *
     * ESC 是清除，与原版「按键设置」里的意思一致——那儿也是按 ESC 解绑，玩家不用
     * 学第二套。别的键先过两道冲突：
     *
     * 一是别的 App 已经绑了它。这种必须拦：两个 App 抢同一个键，按下去谁开都不对，
     * 而玩家在这一页上看不到别的 App 绑了什么。
     *
     * 二是原版或别的模组的键位已经占了它。这种同样拦——我们这套快捷键不出现在原版的
     * 「按键设置」界面里（理由见 AppHotkeys），放行的话玩家会得到一个"按 E 同时开背包
     * 和手机"的局面，而且他没有任何地方能查出来是谁干的。想用那个键，先去原版界面里
     * 把占着的那条改掉。
     */
    public void captureKey(int keyCode, int scanCode) {
        capturingKey = false;
        if (app == null) return;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            AppHotkeys.clear(app.getId());
            return;
        }

        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (key.equals(InputConstants.UNKNOWN)) return;

        // 占着这个键的如果是个【没装】的 App，就直接抢过来：它不在管理器的列表里，
        // 玩家看不到、也解不掉那条绑定，拦下来只会变成一个谁也解释不了的"这个键不能用"。
        // AppHotkeys.bind 本来就会把旧的那条摘掉
        ResourceLocation taken = AppHotkeys.appFor(key);
        if (taken != null && !taken.equals(app.getId()) && PhoneScreenRegistry.isInstalled(taken)) {
            notice("mcphone.gui.hotkey_taken_app", takenAppName(taken));
            return;
        }

        KeyMapping conflict = AppHotkeys.conflictingMapping(key);
        if (conflict != null) {
            notice("mcphone.gui.hotkey_taken_key",
                    Component.translatable(conflict.getName()).getString());
            return;
        }

        AppHotkeys.bind(app.getId(), key);
    }

    /** 那一行上短暂显示一句话，到点自己消失 */
    private void notice(String key, Object... args) {
        keyNotice = Component.translatable(key, args).getString();
        keyNoticeUntilMs = System.currentTimeMillis() + NOTICE_MS;
    }

    /** 占着这个键的那个 App 叫什么。它可能是个坏附属，所以照样兜住 */
    private static String takenAppName(ResourceLocation id) {
        IPhoneApp other = PhoneScreenRegistry.getApp(id);
        if (other == null) return id.toString();
        return safe(() -> other.getDisplayName().getString(), id.toString());
    }

    /**
     * 卸载键。系统 App 画成灰的、点不动，下面写一行为什么。
     *
     * 上膛之后字变成「再点一次确认」，颜色也换——玩家得看得出来这一下与上一下不是同一件事。
     */
    private void renderUninstallButton(GuiGraphics g, Font font, int x, int bottom, int w,
                                       int mouseX, int mouseY) {

        boolean system = app.isSystemApp();

        btnX = x;
        btnW = w;
        btnY = bottom - BUTTON_H - font.lineHeight - 2;
        btnHovered = !system && GuiUtil.hit(mouseX, mouseY, btnX, btnY, btnW, BUTTON_H);

        int bg = system ? PhoneTheme.COLOR_BUTTON_DISABLED
                : (btnHovered ? PhoneTheme.COLOR_ROW_HOVER_DANGER : PhoneTheme.COLOR_ROW_HOVER);
        g.fill(btnX, btnY, btnX + btnW, btnY + BUTTON_H, bg);

        String label = Component.translatable(uninstallArmed
                ? "mcphone.gui.uninstall_confirm" : "mcphone.gui.uninstall").getString();
        int color = system ? FontPalette.dim()
                : (uninstallArmed ? FontPalette.dangerArmed() : FontPalette.uninstall());
        g.drawString(font, label, btnX + (btnW - font.width(label)) / 2,
                btnY + (BUTTON_H - font.lineHeight) / 2, color, false);

        if (system) {
            String why = Component.translatable("mcphone.gui.system_app_locked").getString();
            g.drawString(font, GuiUtil.truncate(font, why, w),
                    x, btnY + BUTTON_H + 2, FontPalette.dim(), false);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || app == null) return true;

        // 快捷键那一行：点一下开始等键，等键时再点一下就是算了
        if (keyRowHovered) {
            capturingKey = !capturingKey;
            uninstallArmed = false;
            keyNotice = null;
            return true;
        }

        if (!btnHovered) {
            // 点别处＝把上膛的卸载卸下来，也把等键收掉。
            // 与相册的删除键同一条：走开就等于反悔
            uninstallArmed = false;
            capturingKey = false;
            return true;
        }
        if (app.isSystemApp()) return true;

        if (!uninstallArmed) {
            uninstallArmed = true;
            return true;
        }

        PhoneScreenRegistry.uninstall(app.getId());
        uninstallArmed = false;
        backRequest = true;      // 这个 App 已经不在列表里了，留在它的详情页上没有意义
        return true;
    }

    /** 这个 App 是哪个模组给的：按 id 的命名空间查，查不到就把命名空间本身显示出来 */
    private String providerName() {
        String namespace = app.getId().getNamespace();
        return ModList.get().getModContainerById(namespace)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(namespace);
    }

    /**
     * 读一个第三方 App 的字符串，抛了就用兜底值。
     *
     * 连 Throwable 一起接：不可用的 App（前置没装）读它的方法会抛 NoClassDefFoundError，
     * 那不是 Exception。PhoneScreenRegistry.requiredModsOf 里已经踩过同一个坑。
     */
    private static String safe(Supplier<String> getter, String fallback) {
        try {
            String value = getter.get();
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            MCphone.LOGGER.warn("[MCphone] App 管理页里画图标失败: {}", t.toString());
        }
    }
}
