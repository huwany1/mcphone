package com.november.mcphone.core.client;

/**
 * 手机界面开多大 —— 玩家自己定的那个倍数。
 *
 * 为什么需要它
 *
 * 手机的每一处尺寸都是按 120×200 这个屏幕写死的（{@link PhoneTheme}），画出来是
 * GUI 单位 1:1。这在 1080p、GUI 缩放 3 上正好，但换到 4K + GUI 缩放 2 就成了一块
 * 邮票——而 GUI 缩放是【全局】设置，为看清手机把它调大，聊天框和物品栏跟着一起变大。
 * 所以这一档得是手机自己的。
 *
 * 它不是"改布局"，是【整体缩放】
 *
 * 界面里没有一个数需要跟着变：渲染时把整个手机套进一层 pose 缩放，鼠标坐标反过来
 * 除掉同一个倍数。各页照旧按 120×200 算自己的行高和命中，一行代码都不用改。
 * 代价是非整数倍时字会稍软——字体是位图，2.5 倍下每个字模跨不满整数个屏幕像素。
 * 所以步进给的是 25%，而不是 1%：让"整数倍"落得到（GUI 缩放 2 配 150% 正好是 3 倍）。
 *
 * 存哪儿
 *
 * 客户端配置（{@link ClientConfig#UI_SCALE}），跟着这台电脑走——它描述的是"这块屏幕
 * 上多大合适"，与存档、与服务器都没关系，和字体颜色同一个道理。存的是整数百分比而
 * 不是浮点：配置文件里 uiScale = 150 比 1.5000000596 好读，玩家手改时尤其。
 */
public final class PhoneScale {

    private PhoneScale() {}

    /** 最小 75%。再小字就开始糊成一团，那时候放大手机反而不如放大 GUI 缩放 */
    public static final int MIN_PERCENT = 75;

    /** 最大 300%。再大在 1080p 上已经顶到窗口，实际会被 {@link #fit} 夹回去 */
    public static final int MAX_PERCENT = 300;

    /** 加减一次走多少。25 是为了让整数倍落得到，理由见类注释 */
    public static final int STEP_PERCENT = 25;

    public static final int DEFAULT_PERCENT = 100;

    private static int percent = DEFAULT_PERCENT;

    public static int percent() {
        return percent;
    }

    /** 渲染要的那个倍数 */
    public static float get() {
        return percent / 100.0F;
    }

    /** 配置读进来时推给这里。渲染每帧都要问，不能去碰配置 */
    static void load(int value) {
        percent = clamp(value);
    }

    /** 玩家在设置里改了。先落到这里（下一帧就是新的），再存盘 */
    public static void setPercent(int value) {
        int wanted = clamp(value);
        if (wanted == percent) return;
        percent = wanted;
        ClientConfig.saveUiScale(percent);
    }

    /** 加减键走这条，直接夹在两端不回绕：回绕会让人以为点漏了 */
    public static void nudge(int deltaPercent) {
        setPercent(percent + deltaPercent);
    }

    public static int clamp(int value) {
        return Math.clamp(value, MIN_PERCENT, MAX_PERCENT);
    }

    /**
     * 窗口放得下多大 —— 手机连边框一起，不能比窗口还高还宽。
     *
     * 夹在这里而不是夹在设置里：窗口是随时会变的（拖窗口、切全屏、改 GUI 缩放），
     * 存进配置的那个数是玩家的意愿，不该被一次临时的小窗口永久改小。
     */
    public static float fit(int windowWidth, int windowHeight) {
        float byWidth = (float) windowWidth / PhoneTheme.PHONE_TOTAL_WIDTH;
        float byHeight = (float) windowHeight / PhoneTheme.PHONE_TOTAL_HEIGHT;
        return Math.max(0.1F, Math.min(byWidth, byHeight));
    }

    /** 这一帧真正用的倍数：玩家要的，与窗口放得下的，取小 */
    public static float effective(int windowWidth, int windowHeight) {
        return Math.min(get(), fit(windowWidth, windowHeight));
    }

    /** 现在是不是被窗口夹着——设置页据此说一句"窗口放不下" */
    public static boolean clampedByWindow(int windowWidth, int windowHeight) {
        return fit(windowWidth, windowHeight) < get() - 0.001F;
    }
}
