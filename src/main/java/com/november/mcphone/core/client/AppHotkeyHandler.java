package com.november.mcphone.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.november.mcphone.api.client.app.IPhoneApp;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 按下某个 App 的快捷键 —— 开机，并且直接进那个 App。
 *
 * 绑定表在 {@link AppHotkeys}，界面在「设置 → App 管理器 → 某个 App」。
 *
 * 为什么听 InputEvent.Key，而开机键 {@link PhoneKeyHandler} 是在 tick 里读
 *
 * 那个键是 KeyMapping，{@code consumeClick()} 会把积压的按下逐个取走，同一
 * tick 内连按几下不丢也不重；而这里的键不是 KeyMapping（理由见 AppHotkeys），
 * 没有那口队列。改在 tick 里轮询 {@code isKeyDown} 的话，比一个 tick（50ms）
 * 更短的一下就会整个丢掉——按得快正是快捷键的常态。所以听按下事件本身。
 *
 * 只认 GLFW_PRESS：REPEAT 是按住不放时系统补发的，那会变成一直重开手机。
 *
 * 界面开着时不响应
 *
 * {@code mc.screen != null} 就直接回来。玩家正在背包、聊天框、甚至手机自己
 * 里面时，他按的键属于那个界面——尤其聊天框，那时候每一个字母键都是在打字。
 * （手机已经开着时按某个 App 的键不会切过去，这是刻意的：那一下多半是在
 * 手机里输入，而不是想换 App。）
 */
public final class AppHotkeyHandler {

    private AppHotkeyHandler() {}

    /** 由 MCphoneClient 构造函数挂到游戏总线 */
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) return;

        // 与原版记按键同一套：有键位符号的用 KEYSYM，没有的退回扫描码
        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());

        ResourceLocation appId = AppHotkeys.appFor(key);
        if (appId == null) return;

        // 卸载了就当没绑：绑定按机器存、安装状态按存档存，同一台电脑换个存档
        // 完全可能没装这个 App。此时什么都不做，也不提示——按错键是很常见的事，
        // 为此弹一句话反而聒噪（开机键那边同一条规矩）
        if (!PhoneScreenRegistry.isInstalled(appId)) return;

        IPhoneApp app = PhoneScreenRegistry.getApp(appId);
        if (app == null) return;    // 目录里没有＝前置模组这局没装，不可用

        // 身上没有手机就开不了机，那就更谈不上进 App
        if (!PhoneScreenOpener.open(mc.player)) return;

        if (mc.screen instanceof PhoneScreen phone) phone.launchApp(app);

        drainConflicting(key);
    }

    /**
     * 这个键要是同时还挂着一条 KeyMapping，把它这一下攒的点击倒掉。
     *
     * 绑定界面会拦住"这个键原版已经在用"（见 AppManagerDetail.captureKey），所以正常
     * 走不到这里。走得到的是手改配置那条路——那份配置是公开可编辑的，注释里还写了格式。
     *
     * 不倒的话会变成这样：按 E，我们开了手机，原版那一下 click 排在队里没人取（原版
     * 取它的地方要求当前没有界面），等玩家关掉手机，背包【补开一次】。玩家会觉得是
     * 手机把背包键弄坏了，而且第二次按才对——这种时序错位最难查。
     */
    private static void drainConflicting(InputConstants.Key key) {
        KeyMapping clash = AppHotkeys.conflictingMapping(key);
        if (clash == null) return;
        while (clash.consumeClick()) { /* 倒空 */ }
    }
}
