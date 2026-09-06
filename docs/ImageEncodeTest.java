package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.ImageCodec;
import com.november.mcphone.feature.chat.ChatImage;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 发图那条路的断言测试：压缩的结果、透明通道、以及"同一张只压一次"的缓存。
 * 要 Minecraft 的类路径（ImageCodec 引了 NativeImage），照着下面两行跑：
 *   CP="build/classes/java/main:build/moddev/artifacts/neoforge-21.1.248-merged.jar:$(tr '\n' ':' &lt; build/moddev/serverLegacyClasspath.txt)"
 *   javac -cp "$CP" -d /tmp/ie docs/ImageEncodeTest.java &amp;&amp; java -cp "/tmp/ie:$CP" com.november.mcphone.feature.chat.client.ImageEncodeTest
 *
 * 守着的几件事：
 *   1. 表情的透明底不能变成黑底——玩家在表情页看到的与发出去的必须是同一张；
 *   2. 截图不能白留一个 alpha 通道，这条路上每个字节都要过网络；
 *   3. 压不进上限的自动降档，最后一定 ≤ MAX_BYTES、长边 ≤ MAX_SIDE；
 *   4. 同一个文件第二次发直接拿缓存，文件被换掉之后缓存要失效。
 *
 * 测不了的：所有会写日志的失败路径。MCphone.LOGGER 一碰就要初始化模组主类，而那要 FML。
 */
public class ImageEncodeTest {

    static int checks = 0;
    static final List<String> failures = new ArrayList<>();

    static void check(boolean cond, String what) {
        checks++;
        if (!cond) failures.add(what);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("mcphone-image-test");

        Path sticker = dir.resolve("sticker.png");
        write(sticker, transparentSticker(512));

        Path shot = dir.resolve("screenshot.png");
        write(shot, screenshot(1920, 1080));

        Path noisy = dir.resolve("noisy.png");
        write(noisy, noise(2048));

        //  1. 透明底压完还得是透明的
        ImageCodec.Encoded encodedSticker = encodeWithinLimit(sticker);
        check(encodedSticker != null, "透明底的表情应当发得出去");
        if (encodedSticker != null) {
            BufferedImage back = ImageIO.read(new ByteArrayInputStream(encodedSticker.png()));
            check((back.getRGB(0, 0) >>> 24) == 0, "表情四角的透明像素压完仍应透明（曾经变成纯黑）");
            check(back.getColorModel().hasAlpha(), "有透明像素的图必须带 alpha 通道");
            check((back.getRGB(back.getWidth() / 2, back.getHeight() / 2) >>> 24) == 0xFF,
                    "图案本身仍应是不透明的");
        }

        //  2. 截图不该白留一个通道
        ImageCodec.Encoded encodedShot = encodeWithinLimit(shot);
        check(encodedShot != null, "截图应当发得出去");
        if (encodedShot != null) {
            BufferedImage back = ImageIO.read(new ByteArrayInputStream(encodedShot.png()));
            check(!back.getColorModel().hasAlpha(), "没有透明像素的图不该带 alpha 通道");
        }

        //  3. 上限：降档之后一定装得进去
        for (Path p : List.of(sticker, shot, noisy)) {
            ImageCodec.Encoded e = encodeWithinLimit(p);
            check(e != null, p.getFileName() + " 应当压得进上限");
            if (e != null) {
                check(e.png().length <= ChatImage.MAX_BYTES,
                        p.getFileName() + " 压完 " + e.png().length + " 字节，超过上限 " + ChatImage.MAX_BYTES);
                check(Math.max(e.width(), e.height()) <= ChatImage.MAX_SIDE,
                        p.getFileName() + " 长边 " + Math.max(e.width(), e.height()) + " 超过 " + ChatImage.MAX_SIDE);
                check(e.width() > 0 && e.height() > 0, p.getFileName() + " 尺寸不该是 0");
            }
        }

        //  4. 同一个文件第二次直接拿缓存；文件换掉之后不能再拿旧的
        ImageCodec.Encoded first = encodeWithinLimit(noisy);
        ImageCodec.Encoded second = encodeWithinLimit(noisy);
        check(first == second, "同一个文件压第二次应当命中缓存，拿到同一份字节");

        write(noisy, transparentSticker(256));          // 同名换内容：大小与改动时间都变了
        ImageCodec.Encoded third = encodeWithinLimit(noisy);
        check(third != first, "文件被换掉之后缓存必须失效");
        if (third != null) {
            BufferedImage back = ImageIO.read(new ByteArrayInputStream(third.png()));
            check((back.getRGB(0, 0) >>> 24) == 0, "换上去的那张是透明底，压完应当还是透明的");
        }

        // 解不开的文件（拖错了、下了一半）这里测不了：那条路要写一行 warn，而 MCphone.LOGGER
        // 一碰就会把整个模组主类初始化起来，那要 FML 已经装好。留给游戏里跑。

        if (failures.isEmpty()) {
            System.out.println("全部通过（" + checks + " 项）");
        } else {
            System.out.println("失败 " + failures.size() + " / " + checks + " 项：");
            failures.forEach(f -> System.out.println("  ✗ " + f));
            System.exit(1);
        }
    }

    /** ChatImageSender.encodeWithinLimit 是私有的：它是实现细节，但正是要守的那一段 */
    static ImageCodec.Encoded encodeWithinLimit(Path photo) throws Exception {
        Method m = ChatImageSender.class.getDeclaredMethod("encodeWithinLimit", Path.class);
        m.setAccessible(true);
        return (ImageCodec.Encoded) m.invoke(null, photo);
    }

    static void write(Path path, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }

    /** 一张透明底的表情：中间一张黄脸，四周全透明 */
    static BufferedImage transparentSticker(int n) {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xFFFFD93B, true));
        g.fillOval(n / 8, n / 8, n * 3 / 4, n * 3 / 4);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(Math.max(1f, n / 32f)));
        g.drawArc(n * 5 / 16, n * 7 / 16, n * 3 / 8, n / 4, 200, 140);
        g.dispose();
        return img;
    }

    /** 一张普通截图：天到地的渐变，没有透明像素 */
    static BufferedImage screenshot(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x87CEEB), 0, h, new Color(0x228B22)));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    /** 最坏情况：满屏噪点，压出来必然超上限，逼着降档 */
    static BufferedImage noise(int n) {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(20260906L);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        return img;
    }
}
