package com.november.mcphone.feature.terminal;

import com.november.mcphone.core.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 手机替卡槽里那台终端供电 —— 装进去就一直是满的。
 *
 * 它解决的是什么
 *
 * 终端装进手机卡槽之后，充电这件事变得很难受：卡槽是玩家附件，世界里的充电器够不着它，
 * 于是"没电了"的唯一出路是<b>把它从卡槽里取出来、充完、再装回去</b>。而没电的终端连开都
 * 开不了（AE2 自己会拒绝），所以这一趟躲不掉。装进手机反而比揣在背包里麻烦，那这个卡槽
 * 就白做了。
 *
 * 手机把电管了：卡槽里的终端每秒补满，于是它不会没电，也不用取出来充。
 *
 * 我们只管「电」这一件事
 *
 * 范围、维度、绑没绑网络、增幅卡加了多远，一条都没动，仍然是 AE2 / RS 自己查、自己拒绝、
 * 自己给玩家发消息（见 {@link TerminalOpener}）。这里也没有碰它们的菜单：充电走的是
 * <b>NeoForge 的物品能量能力</b>，AE2 与 RS 都为自己的终端注册了它
 * （{@code InitCapabilityProviders.initPoweredItem} → {@code PoweredItemCapabilities}；
 * {@code EnergyStorageAdapter}），我们调的是它们自己那份实现，和拿去充电器里充是同一条路。
 * 认能力而不认牌子，所以这个类里一个 AE2 / RS 的类型都没有，将来谁家的终端只要挂了这个
 * 能力就自动跟着享受。
 *
 * Tom's Simple Storage 无事发生：它的终端整个模组里连能量的影子都没有，本来就不用电。
 *
 * 这不是一台无限发电机
 *
 * 充进去的电<b>取不回来</b>：AE2 与 RS 的那两份实现 {@code canExtract()} 都恒为 false
 * （AE2 的 {@code extractEnergy} 方法体就是一句 {@code return 0}）。所以没法拿终端当电池，
 * 从卡槽里取出来插进机器抽电这条路不通。
 *
 * 服主不想要可以关：{@code serverconfig/mcphone-server.toml} 里的 {@code terminalKeepPowered}。
 * 关掉之后卡槽里的终端和拿在手上完全一样，该耗多少耗多少。
 *
 * 为什么是每秒一次，而不是每 tick
 *
 * 一秒的电量差在任何一家那里都远小于一次开机所需，玩家察觉不到；而每 tick 灌一次意味着
 * 每 tick 一次能力查询加一次附件同步（{@link TerminalSlot#markChanged}），一整台服务器的
 * 每个玩家都要付这份钱。补不满也没关系——终端自己耗电的速度比这慢得多。
 */
public final class TerminalCharger {

    private TerminalCharger() {}

    /** 多久补一次电，单位 tick */
    private static final int INTERVAL_TICKS = 20;

    /** 由 MCphone 构造函数挂到游戏总线 */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % INTERVAL_TICKS != 0) return;
        if (!ServerConfig.terminalKeepPowered()) return;

        ItemStack terminal = TerminalSlot.get(player);
        if (terminal.isEmpty()) return;

        // 没挂能量能力的（Tom's 的终端、以及任何不用电的东西）到这儿就结束了
        IEnergyStorage energy = terminal.getCapability(Capabilities.EnergyStorage.ITEM);
        if (energy == null || !energy.canReceive()) return;

        int missing = energy.getMaxEnergyStored() - energy.getEnergyStored();
        if (missing <= 0) return;

        // 满了就不必再同步。这一句也顺带兜住"能力说得收、实际一点都收不进"的实现
        if (energy.receiveEnergy(missing, false) > 0) TerminalSlot.markChanged(player);
    }
}
