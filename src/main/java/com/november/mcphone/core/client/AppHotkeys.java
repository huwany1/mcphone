package com.november.mcphone.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.november.mcphone.MCphone;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每个 App 一个快捷键 —— 在世界里按一下，直接开机并进这个 App。
 *
 * 绑在哪里、由谁改，见「设置 → App 管理器 → 某个 App」那一页；这里只管
 * 「哪个键是哪个 App 的」这张表本身，以及它怎么落盘、怎么判冲突。
 *
 * 为什么不做成 KeyMapping
 *
 * 原版的按键设置界面要求键位在【游戏启动时】就全部注册好（
 * {@code RegisterKeyMappingsEvent} 在客户端 setup 阶段发一次，此后原版
 * 不认新的）。而 App 的名单是运行期才知道的：SPI 扫描要等模组加载完，
 * 玩家还能在商店里装、在管理器里卸，附属模组随时可以再添一个。为一份
 * 会变的名单去注册一批固定的 KeyMapping，只有两条路——要么预留 N 个
 * "App 快捷键 1..N" 这种玩家看不懂的空位，要么每次名单变了就骗原版重扫。
 * 两条都比自己认一下键码难看得多。
 *
 * 所以这张表是我们自己的：存在客户端配置里（见 {@link ClientConfig}），
 * 按下时由 {@link AppHotkeyHandler} 认。代价是它不出现在原版的「按键设置」
 * 界面里，因此 {@link #conflictingMapping} 必须替玩家把冲突挡在绑定那一步——
 * 玩家没法在原版界面里发现"这个键被手机占了"。
 *
 * 为什么值是 InputConstants.Key 而不是 int
 *
 * 键码只对 KEYSYM 那一类有意义。原版把"没有键位符号的键"（某些键盘布局上
 * 的额外键）按扫描码记，两者的整数值是各自独立的编号空间，混在一个 int 里
 * 迟早撞车。Key 自带类型，序列化成 "key.keyboard.k" / "scancode.87" 这种
 * 字符串，和原版 options.txt 记按键是同一套写法。
 */
public final class AppHotkeys {

    private AppHotkeys() {}

    /** appId → 键。用 LinkedHashMap：写回配置时顺序稳定，不然每次存盘文件都在无谓地变 */
    private static final Map<ResourceLocation, InputConstants.Key> BOUND = new LinkedHashMap<>();

    /** 配置里一条的写法：{@code <appId>=<键名>}。appId 里不可能有等号，切一刀就够 */
    private static final char SEP = '=';

    //  查

    /** 这个 App 绑的键，没绑就是 null */
    public static InputConstants.Key get(ResourceLocation appId) {
        return BOUND.get(appId);
    }

    /** 这个键绑给了哪个 App，没有就是 null */
    public static ResourceLocation appFor(InputConstants.Key key) {
        for (Map.Entry<ResourceLocation, InputConstants.Key> e : BOUND.entrySet()) {
            if (e.getValue().equals(key)) return e.getKey();
        }
        return null;
    }

    /**
     * 这个键在原版的按键设置里已经有主了吗，有就把那一条还回来。
     *
     * 包括别的模组的键位：{@code options.keyMappings} 装的是所有注册过的
     * KeyMapping，不分是谁的。我们自己那三个（开机、拍照、退出相机）也在里面，
     * 所以把 App 绑到 H 上会在这里被拦下——那正是想要的。
     */
    public static KeyMapping conflictingMapping(InputConstants.Key key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyMappings == null) return null;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (key.equals(mapping.getKey())) return mapping;
        }
        return null;
    }

    //  改

    /**
     * 绑上去并存盘。同一个 App 再绑一次就是换键。
     *
     * 不在这里判冲突：判冲突要给玩家说清楚"被谁占了"，那是界面的事。
     * 这里只保证一个键不会同时挂在两个 App 上——真出现了以后来的为准，
     * 因为玩家刚按的那一下是他现在的意思。
     */
    public static void bind(ResourceLocation appId, InputConstants.Key key) {
        if (key == null || key.equals(InputConstants.UNKNOWN)) return;
        BOUND.entrySet().removeIf(e -> e.getValue().equals(key));
        BOUND.put(appId, key);
        ClientConfig.saveAppHotkeys(serialize());
    }

    /** 解绑并存盘。本来就没绑就什么都不做，省一次无谓的写盘 */
    public static void clear(ResourceLocation appId) {
        if (BOUND.remove(appId) != null) ClientConfig.saveAppHotkeys(serialize());
    }

    //  配置 ←→ 这张表

    /**
     * 配置读进来（或被改了）时把整张表换掉。
     *
     * 认不出来的条目【丢掉并留一行日志】，不让整份配置作废：这份配置里还有
     * 字体颜色和音量，为一条手改坏了的快捷键把它们一起退回默认值不值当。
     * 丢掉的那条会在下一次存盘时从文件里消失。
     */
    static void load(List<? extends String> entries) {
        BOUND.clear();
        if (entries == null) return;

        for (String entry : entries) {
            int sep = entry.indexOf(SEP);
            if (sep <= 0 || sep == entry.length() - 1) {
                MCphone.LOGGER.warn("[MCphone] 快捷键配置里这一条不认识，已忽略: {}", entry);
                continue;
            }

            ResourceLocation appId = ResourceLocation.tryParse(entry.substring(0, sep).trim());
            if (appId == null) {
                MCphone.LOGGER.warn("[MCphone] 快捷键配置里的 App id 不合法，已忽略: {}", entry);
                continue;
            }

            InputConstants.Key key;
            try {
                key = InputConstants.getKey(entry.substring(sep + 1).trim());
            } catch (IllegalArgumentException e) {
                // 键名是原版那套 "key.keyboard.k"，手改配置时最容易写错的就是这一段
                MCphone.LOGGER.warn("[MCphone] 快捷键配置里的键名不认识，已忽略: {}", entry);
                continue;
            }

            if (key.equals(InputConstants.UNKNOWN)) continue;   // 等同于没绑，不必留着
            BOUND.entrySet().removeIf(e -> e.getValue().equals(key));   // 手改出的重复键
            BOUND.put(appId, key);
        }
    }

    /** 写回配置用的那一串。顺序跟着 BOUND，稳定 */
    static List<String> serialize() {
        List<String> out = new ArrayList<>(BOUND.size());
        for (Map.Entry<ResourceLocation, InputConstants.Key> e : BOUND.entrySet()) {
            out.add(e.getKey() + String.valueOf(SEP) + e.getValue().getName());
        }
        return out;
    }
}
