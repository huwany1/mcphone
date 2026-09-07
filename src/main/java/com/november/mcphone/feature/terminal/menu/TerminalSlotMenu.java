package com.november.mcphone.feature.terminal.menu;

import com.november.mcphone.core.menu.ModMenus;
import com.november.mcphone.feature.terminal.integration.Terminals;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 手机的终端卡槽 —— 一格，加玩家背包。
 *
 * 它解决什么
 *
 * 「身上有好几台终端时，点开 App 到底开哪一台」。在它之前的答案是"背包顺序里第一台"，
 * 那是个玩家没法预测、也没法改的规则。现在的答案是"你自己装进去的那一台"。
 *
 * 装进去是可选的
 *
 * 卡槽空着时点 App 到的就是这一页，而这一页上的「打开终端」按钮照样会去背包里找——所以
 * 不想用这个机制的人是多按一下，不是没得用。这一条不是客气：终端一旦离开背包，各家自己的
 * 快捷键（AE2WTLib 的补货/磁铁/收纳、RS 的打开无线网格）就找不到它了。把卡槽做成加成而
 * 不是替代，这些东西才不会被我们顺手弄坏。
 *
 * 为什么是一个真的容器界面，而不是画在手机屏幕里
 *
 * 附属的 {@code IPhonePage} 只给 render / mouseClicked / keyPressed，没有任何 Slot 支持；
 * 本体自己那个能拖拽的容器界面在 {@code core} 包里，附属碰不到。而"把终端放进去"这件事
 * 要的恰恰是拖拽、shift 搬运这些原版行为。所以这里另开一个 {@code AbstractContainerMenu}
 * ——它是我们自己的，不依赖本体任何东西。
 */
public class TerminalSlotMenu extends AbstractContainerMenu {

    /** 卡槽在界面里的位置。屏幕那边画底板要用同一个数，所以放在这里公开 */
    public static final int SLOT_X = 80;
    /** 18 而不是 20：下面要塞进提示文字和按钮，纵向预算见 TerminalSlotScreen 的常量 */
    public static final int SLOT_Y = 18;

    private final TerminalSlotContainer terminal;

    /**
     * 客户端与服务端共用这一个构造。
     *
     * 不需要额外的开局数据：卡槽的内容由 attachment 自己同步过来（见 TerminalSlot），不必再
     * 随开菜单的包发一遍。
     */
    public TerminalSlotMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.TERMINAL_SLOT.get(), containerId);

        Player player = playerInventory.player;
        this.terminal = new TerminalSlotContainer(player);

        addSlot(new Slot(terminal, 0, SLOT_X, SLOT_Y) {
            /**
             * 只收<b>能从手机上打开</b>的终端。
             *
             * 问的是 {@link Terminals#isOpenable}，不是"是不是终端"——两者会不一样：
             * Tom's 的基础无线终端是终端，但它没有"隔空打开"这回事（{@code canOpen} 恒为
             * false，{@code open} 的方法体是一句 return）。装得进去却点不开，比装不进去
             * 更难解释，所以它在这一步就被挡住。
             *
             * 认哪些牌子由 {@link Terminals} 现问现答，这里不认识任何一家存储模组——三家
             * 全是软前置，这个类要在一家都没装的情况下也能加载。
             */
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Terminals.isOpenable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // 玩家背包三行
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** 手机里现在装着的那台终端。屏幕拿它决定「打开」按钮亮不亮 */
    public ItemStack getTerminal() {
        return terminal.getItem(0);
    }

    /**
     * shift 点击。
     *
     * 卡槽 → 背包，背包 → 卡槽。后者靠 {@code moveItemStackTo} 自己去问 mayPlace，所以
     * shift 一个非终端物品不会有任何反应，这是对的。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == 0) {
            // 卡槽 → 玩家背包（含快捷栏）
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // 玩家背包 → 卡槽
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * 一直有效。
     *
     * 卡槽挂在玩家身上而不是世界里的某个方块，所以不存在"走远了要关掉"这回事。
     */
    @Override
    public boolean stillValid(Player player) {
        return terminal.stillValid(player);
    }
}
