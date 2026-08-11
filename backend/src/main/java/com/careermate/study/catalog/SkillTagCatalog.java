package com.careermate.study.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 八股题库技能标签词表：预置标签 + 同义写法归一化。
 *
 * <p>标签本身仍是自由文本（用户可自建任意标签），这里只负责两件事：
 * <ol>
 *   <li>给出一组预置标签，作为筛选面与录入候选的默认项；</li>
 *   <li>把大小写 / 空白 / 常见同义写法归一到唯一形态，避免「Java并发 / java并发 / 并发编程」
 *       被拆成三个互不相通的筛选项。</li>
 * </ol>
 *
 * <p>不在词表内的标签原样保留（仅做空白归一），用户自建词汇不会被吞掉；
 * 这类标签由 {@code /api/study/notes/skills} 与预置项合并后一起出现在筛选面上，
 * 因此不存在「存得进去、筛不出来」的死角。
 */
public final class SkillTagCatalog {

    /** 预置标签，顺序即筛选面展示顺序（AI 相关前置，为当前高频考察方向）。 */
    public static final List<String> PRESET = List.of(
            "AI大模型", "RAG", "Agent",
            "Java并发", "JVM", "MySQL", "Redis",
            "算法", "系统设计", "行为面");

    /** 归一化键 -> 规范标签。键为「小写 + 去掉全部空白」后的形态。 */
    private static final Map<String, String> ALIASES = buildAliases();

    private SkillTagCatalog() {
    }

    /**
     * 归一化一个技能标签。
     *
     * @param raw 原始输入（可为 null）
     * @return 规范标签；空白输入返回 null；词表未命中时返回「去首尾空白 + 折叠内部空白」后的原值
     */
    public static String canonicalize(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.trim().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return null;
        }
        String key = collapsed.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String canonical = ALIASES.get(key);
        return canonical != null ? canonical : collapsed;
    }

    /** 是否为预置标签。 */
    public static boolean isPreset(String tag) {
        return tag != null && PRESET.contains(tag);
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        // 预置标签自身也要能被自己的小写/带空格写法命中
        for (String preset : PRESET) {
            put(map, preset, preset);
        }
        // 只折叠「确实是同一件事」的写法：大小写、空白、公认同义词。
        // 刻意不做语义归并（如「缓存」不并入 Redis、「数据库」不并入 MySQL），
        // 否则会把用户本来想区分的标签吞掉；这类标签走「用户自建标签」通道照样能筛。
        // AI 方向
        put(map, "AI大模型", "ai", "大模型", "llm", "大语言模型", "aigc", "大模型应用", "ai大模型");
        put(map, "RAG", "rag", "检索增强", "检索增强生成");
        put(map, "Agent", "agent", "智能体", "ai agent", "agent开发", "多智能体");
        // Java 基础方向
        put(map, "Java并发", "并发", "并发编程", "多线程", "juc", "concurrency", "java并发编程");
        put(map, "JVM", "jvm", "java虚拟机", "垃圾回收", "gc");
        put(map, "MySQL", "mysql", "my sql");
        put(map, "Redis", "redis");
        // 通用方向
        put(map, "算法", "algorithm", "数据结构与算法", "leetcode");
        put(map, "系统设计", "systemdesign", "system design", "架构设计");
        // 平台题库的 category 取值为 技术/行为/HR，后两者在个人题库里就是「行为面」这一格
        put(map, "行为面", "行为", "行为面试", "behavior", "behavioral", "hr", "hr面");
        return map;
    }

    private static void put(Map<String, String> map, String canonical, String... aliases) {
        for (String alias : aliases) {
            map.put(alias.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""), canonical);
        }
    }
}
