package com.november.mcphone.feature.settings.client.changelog;

import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** 无游戏/声卡/网络：验证提醒语义、翻译、包内契约、隔离和重启后的持久化。 */
public final class ChangelogTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) throws Exception {
        String data = """
                [{"version":"2.0.0","date":"2026-10-23","languages":{
                  "zh_cn":[{"title":"新增","entries":["中文 [详情](https://example.com)"]}],
                  "en_us":[{"title":"Added","entries":["English"]}]}},
                 {"version":"1.9.0","date":"","languages":{
                  "zh_cn":[{"title":"修复","entries":["旧版"]}],
                  "en_us":[{"title":"Fixed","entries":["Previous"]}]}}]
                """;
        var releases = Changelog.read(new StringReader(data));
        check(releases.size() == 2);
        check(Changelog.current(releases, "2.0.0").sections("zh_cn").get(0).title().equals("新增"));
        check(Changelog.current(releases, "2.0.0").sections("de_de").get(0).title().equals("Added"));
        check(Changelog.current(releases, "2.0.0-beta.1").version().equals("2.0.0"));
        check(Changelog.current(releases, "3.0.0") == null);
        check(Changelog.shouldNotify("2.0.0", "", true));
        check(Changelog.shouldNotify("2.0.0", "1.0.0", true));
        check(!Changelog.shouldNotify("2.0.0", "2.0.0", true));
        check(!Changelog.shouldNotify("2.0.0-beta.1", "", true));
        check(!Changelog.shouldNotify("2.0.0-alpha.1", "", true));
        check(!Changelog.shouldNotify("2.0.0", "", false));
        // 没有版本高水位/降级特殊规则，任何不同的正式版本都只展示自己的正文。
        check(Changelog.shouldNotify("1.9.0", "2.0.0", true));
        check(Changelog.current(releases, "1.9.0").sections("en_us").get(0).entries().equals(List.of("Previous")));

        var entry = ChangelogText.parse("[Forge] A [details](https://example.com/a?q=1&x=2) B [more](http://example.org)");
        check(entry.text().equals("[Forge] A details B more"));
        check(entry.links().size() == 2);
        check(entry.links().get(0).url().equals("https://example.com/a?q=1&x=2"));
        check(ChangelogText.parse("[bad](file:///tmp/a) [bad](javascript:alert)").links().isEmpty());
        check(ChangelogText.parse("[bad](https://user@example.com)").links().isEmpty());
        check(ChangelogText.parse("[bad](https://)").links().isEmpty());

        var directory = Files.createTempDirectory("mcphone-changelog-test");
        UUID account = UUID.randomUUID();
        var a = ChangelogStore.path(directory, account, "local:world-a");
        var b = ChangelogStore.path(directory, account, "local:world-b");
        var c = ChangelogStore.path(directory, account, "remote:example.com:25565");
        var d = ChangelogStore.path(directory, UUID.randomUUID(), "local:world-a");
        check(!a.equals(b) && !a.equals(c) && !a.equals(d));
        check(a.startsWith(directory) && c.startsWith(directory));
        check(ChangelogStore.read(a).isEmpty());
        ChangelogStore.shown(a, "2.0.0");
        check(ChangelogStore.read(a).equals("2.0.0"));
        check(ChangelogStore.read(b).isEmpty() && ChangelogStore.read(c).isEmpty());
        check(!Changelog.shouldNotify("2.0.0", ChangelogStore.read(a), true));
        ChangelogStore.shown(a, "2.1.0");
        check(ChangelogStore.read(a).equals("2.1.0"));
        try { Changelog.read(new StringReader("[{\"version\":\"1.0.0\",\"date\":\"\",\"languages\":{}}]")); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }

        try (var stream = ChangelogTest.class.getResourceAsStream("/assets/mcphone/changelog.json")) {
            check(stream != null);
            var bundled = Changelog.read(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            check(!bundled.isEmpty());
            check(bundled.stream().allMatch(r -> !r.sections("en_us").isEmpty() && !r.sections("zh_cn").isEmpty()));
        }
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        System.out.println("ChangelogTest: " + checks + " checks passed");
    }
}
