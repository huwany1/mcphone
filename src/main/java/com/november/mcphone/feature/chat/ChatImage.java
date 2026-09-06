package com.november.mcphone.feature.chat;

/**
 * 图片消息的几个硬上限。客户端压图、服务端收图、存档清理三边共用这一份，
 * 改一个数不必去别处找配套的另一个。
 *
 * 长边为什么是 384
 *
 * 气泡里那张最宽只有 80 个 GUI 像素，只按气泡算的话 256 都嫌多。但图是【点得开的】：
 * 放大之后铺满整块内容区，那是 112×170 个 GUI 像素，而 GUI 缩放最高 4 倍——
 * 屏幕上是 448×680 个真实像素。256 在气泡里绰绰有余，一放大就是 1.75 倍拉伸，
 * 糊得看得出来；384 让放大后基本是原尺寸显示。
 *
 * 再往上（448）收益就没了：那已经超过高度方向能显示的像素数，而 PNG 的体积是
 * 按面积涨的——448 的噪点图要 242 KB，是 384 的 1.4 倍，换来的清晰度看不见。
 *
 * 上限之所以要卡死，是因为硬盘与带宽都是服主替所有人付的。
 *
 * 为什么要分片传
 *
 * 原版对【客户端发给服务端】的自定义包有 32767 字节的硬上限
 * （ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE），超了在编码阶段就断线。
 * 一张 384 长边的 PNG 少则几 KB、多则上百 KB，装不进一个包，所以上传按 {@link #CHUNK_BYTES}
 * 切片。反过来【服务端发给客户端】那条路上限是 1 MB，一个包发得完，不必切。
 */
public final class ChatImage {

    private ChatImage() {}

    /** 存下来的图片长边上限（像素），客户端在发送前就压到这个尺寸 */
    public static final int MAX_SIDE = 384;

    /**
     * 一张图的字节上限。压过之后仍超出的，客户端会再降一档尺寸重压。
     *
     * 128 KB 是照着实测定的：Minecraft 的截图大片是天空、地形这类平面，384 长边压出来
     * 常常只有几 KB；难压的是雨天、树叶、粒子、光影那种满屏噪点的画面，实测纯随机噪点
     * 384 要 177 KB——那种图会自动降到 320（122 KB）装进来。也就是说这个数卡的是最坏情况，
     * 正常截图离它很远。
     */
    public static final int MAX_BYTES = 128 * 1024;

    /** 上传切片大小。32767 的硬上限之外还要留出包头与 UUID 等字段的余量，取 16 KB 稳妥 */
    public static final int CHUNK_BYTES = 16 * 1024;

    /** 一次上传最多几片。按上限算是 8 片，多给一片的余量；超过即判定为伪造包 */
    public static final int MAX_CHUNKS = MAX_BYTES / CHUNK_BYTES + 1;

    /**
     * 每对会话最多留几张图的像素。
     *
     * 消息本身留 100 条（{@link ChatData#MAX_MESSAGES_PER_CONVERSATION}），但图不能照这个数留：
     * 100 张 × 128 KB × 每人上百个好友，硬盘是服主的。超出之后【只删像素、不删消息】——
     * 那条消息还在，显示成「图片已过期」。删掉整条的话，聊天记录会凭空少几行，
     * 而玩家不会知道少的是什么。
     */
    public static final int MAX_IMAGES_PER_CONVERSATION = 20;

    /** 一次上传从第一片到最后一片的时限。超时即丢弃，防止半截上传永远占着内存 */
    public static final long UPLOAD_TIMEOUT_MS = 15_000L;

    //  动图

    /**
     * 动图怎么传：所有帧拼成【一张 PNG】。
     *
     * 这样一来上面那些数一个都不用改——还是一张 PNG、还是 128 KB 上限、还是按内容去重
     * （同一张动图表情反复发仍然只存一份）、服务端仍然不必解码。收件人也只上传一张贴图，
     * 播放就是按时间挑一个子矩形画出来。
     *
     * 转发原始 GIF 字节那条路走不通：源文件常有一两百 KB，超了上限就得重新编码，
     * 而重编 GIF 要重新量化调色板，画质掉得比缩成 PNG 还厉害。
     *
     * 帧摆成尽量方的网格（见 {@link #cols}）而不是排成一长条：一长条的长边很快就撞上
     * 显卡的贴图尺寸上限，而方阵的长边只按帧数开平方涨。
     */
    public static final int MAX_FRAMES = 36;

    /**
     * 抽稀的底线：抽到少于这么多帧就别抽了。
     *
     * 压不进上限时会隔帧抽稀，但抽到只剩四五帧的动画看着是卡顿而不是动画——
     * 那还不如老老实实发第一帧，至少它是清楚的。
     *
     * 卡的只是抽稀。原图本来就只有三帧的眨眼表情照发不误——那是它本来的样子，
     * 不是被我们抽坏的。
     */
    public static final int MIN_FRAMES = 6;

    /**
     * 雪碧图的长边上限。
     *
     * 卡的是显存：一张 768×768 的贴图解出来是 2.25 MB，而客户端要同时留着好几张
     * （见 ChatImageCache）。再大一格的收益也很有限——帧本身最宽才 160，
     * 而气泡里显示出来只有 80。
     */
    public static final int SHEET_MAX_SIDE = 768;

    /**
     * 雪碧图一行摆几帧。
     *
     * 发件人按它拼图、收件人按它取帧，两边必须是同一份实现——差一列，
     * 收到的动画就是每帧都错位的一团乱麻。所以这个方法放在这个两边都依赖的类里。
     */
    public static int cols(int frames) {
        return Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, frames))));
    }

    /** 雪碧图一共几行 */
    public static int rows(int frames) {
        int cols = cols(frames);
        return (Math.max(1, frames) + cols - 1) / cols;
    }
}
