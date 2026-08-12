package com.careermate.resume.version.verify;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 确定性简历事实校验器（P2）——不调用任何 LLM。
 *
 * <p>哲学：从生成版本里抽取<b>强事实</b>（公司/院校/机构、学历、证书、显著量化数字），
 * 逐条做字符串级比对——只要该事实（或其核心词）在<b>源简历 + 画像信号</b>里出现过即视为有据，
 * 否则列为「疑似无出处」。它是 LLM Writer/Critic 判断之外的兜底底线，覆盖
 * MAX_ROUND 落库、Critic 解析失败给中立分等漏稿窗口。
 *
 * <p>校验为纯函数、可重放：同一输入永远得出同一结论。仅在输入非法（generated 为空）时返回
 * {@link FactCheckResult.Status#ERROR}，由调用方按 fail-closed 处理。
 */
@Component
public class ResumeFactVerifier {

    /** 机构/院校后缀，长者在前（Java 正则按书写顺序匹配，需保证最长优先）。 */
    private static final Pattern INSTITUTION = Pattern.compile(
            "[\\u4e00-\\u9fa5A-Za-z0-9（）()&·\\-]{2,30}?"
                    + "(?:股份有限公司|有限责任公司|有限公司|集团|大学|学院|研究院|研究所|银行|证券|基金管理)");

    /** 学历/学位词。 */
    private static final Pattern DEGREE = Pattern.compile(
            "(?:博士后|博士|硕士|学士|本科|大专|专科|MBA|EMBA)");

    /** 证书/资格：后缀式 + 常见缩写。 */
    private static final Pattern CERT_SUFFIX = Pattern.compile(
            "[\\u4e00-\\u9fa5A-Za-z0-9]{2,20}?(?:资格证书|资格证|证书|认证)");
    private static final Pattern CERT_ACRONYM = Pattern.compile(
            "(?:PMP|CPA|CFA|ACCA|CET-?[46]|注册会计师|注册[\\u4e00-\\u9fa5]{1,4}师|司法考试|软考)");

    /** 显著量化数字：百分比 / 金额 / 倍数。年份、普通计数不入强事实，降低噪声。 */
    private static final Pattern SIGNIFICANT_NUMBER = Pattern.compile(
            "\\d+(?:\\.\\d+)?%|\\d+(?:\\.\\d+)?(?:万|亿)(?:元)?|[￥$]\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?倍");

    /**
     * 技术栈词典。
     *
     * <p>生成器为了贴合 JD，最爱做的事就是给候选人凭空加技能——实测一位 Java/Go 候选人被写成
     * 「熟练 TypeScript + Python 双栈，用 TypeScript(Node.js) 构建管理端，Python 做向量预处理与
     * RAG 文档切片，掌握 FastAPI」，而源简历里这些词一个都没有。这类编造比数字编造更致命：
     * 面试第一个问题就穿帮。技术名是封闭集合，用词典做确定性比对即可，不需要 LLM。
     */
    private static final List<String> TECH_TERMS = List.of(
            // 语言
            "Java", "Kotlin", "Scala", "Golang", "Python", "TypeScript", "JavaScript",
            "Node.js", "C++", "C#", "Rust", "PHP", "Ruby", "Swift", "Objective-C",
            // 后端框架 / RPC
            "Spring Boot", "Spring Cloud", "Spring Security", "Dubbo", "MyBatis", "Hibernate",
            "Netty", "gRPC", "FastAPI", "Django", "Flask", "Express", "Gin",
            // 前端
            "React", "Vue", "Angular", "Next.js", "Svelte",
            // 存储
            "MySQL", "PostgreSQL", "Oracle", "MongoDB", "Redis", "Elasticsearch",
            "ClickHouse", "HBase", "Cassandra", "Neo4j", "TiDB", "SQLite",
            // 消息 / 大数据
            "Kafka", "RocketMQ", "RabbitMQ", "Pulsar", "Flink", "Spark", "Hadoop", "Hive", "Airflow",
            // 云原生 / 运维
            "Docker", "Kubernetes", "K8s", "Istio", "Service Mesh", "Jenkins", "Terraform",
            "Prometheus", "Grafana", "SkyWalking", "Nginx",
            // AI / 大模型
            "LangChain", "LlamaIndex", "RAG", "Milvus", "Qdrant", "Pinecone", "Faiss", "pgvector",
            "TensorFlow", "PyTorch", "Transformers", "Prompt Engineering",
            "向量检索", "向量数据库", "知识图谱", "模型微调");

    /**
     * 校验生成版本的强事实是否都能在源文本中找到支撑。
     *
     * @param generatedMarkdown 待校验的生成简历 Markdown
     * @param sourceText        源简历正文（唯一事实来源）
     * @param extraSignals      额外可信信号（如画像技能词、目标岗位），可为 null
     */
    public FactCheckResult verify(String generatedMarkdown, String sourceText, List<String> extraSignals) {
        if (generatedMarkdown == null) {
            return FactCheckResult.error(nowIso());
        }
        String haystack = normalize(buildHaystack(sourceText, extraSignals));
        Set<String> facts = extractStrongFacts(generatedMarkdown);

        String haystackCi = haystack.toLowerCase(Locale.ROOT);
        List<String> unsourced = new ArrayList<>();
        for (String fact : facts) {
            String needle = normalize(fact);
            if (needle.isEmpty()) {
                continue;
            }
            // 技术名大小写写法不统一（TypeScript/typescript、K8s/k8s），比对一律忽略大小写：
            // 宁可放过也不能误报——把源简历里真有的技能标成「无出处」会让用户不敢信这个功能。
            if (!haystackCi.contains(needle.toLowerCase(Locale.ROOT))) {
                unsourced.add(fact);
            }
        }
        String checkedAt = nowIso();
        return unsourced.isEmpty()
                ? FactCheckResult.pass(checkedAt)
                : FactCheckResult.suspect(unsourced, checkedAt);
    }

    public FactCheckResult verify(String generatedMarkdown, String sourceText) {
        return verify(generatedMarkdown, sourceText, null);
    }

    /** 抽取强事实集合（去重、保持出现顺序，便于测试与可读性）。 */
    Set<String> extractStrongFacts(String text) {
        Set<String> facts = new LinkedHashSet<>();
        collect(facts, INSTITUTION, text);
        collect(facts, DEGREE, text);
        collect(facts, CERT_SUFFIX, text);
        collect(facts, CERT_ACRONYM, text);
        collect(facts, SIGNIFICANT_NUMBER, text);
        collectTechTerms(facts, text);
        return facts;
    }

    /** 词典式抽取：生成稿里出现过的技术名都算强事实，逐个回源简历找支撑。 */
    private static void collectTechTerms(Set<String> sink, String text) {
        if (text == null) {
            return;
        }
        String hay = normalize(text).toLowerCase(Locale.ROOT);
        for (String term : TECH_TERMS) {
            if (hay.contains(normalize(term).toLowerCase(Locale.ROOT))) {
                sink.add(term);
            }
        }
    }

    private static void collect(Set<String> sink, Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String g = m.group();
            if (g != null && !g.isBlank()) {
                sink.add(g.trim());
            }
        }
    }

    private static String buildHaystack(String sourceText, List<String> extraSignals) {
        StringBuilder sb = new StringBuilder();
        if (sourceText != null) {
            sb.append(sourceText);
        }
        if (extraSignals != null) {
            for (String s : extraSignals) {
                if (s != null && !s.isBlank()) {
                    sb.append('\n').append(s);
                }
            }
        }
        return sb.toString();
    }

    /** 归一化：去除所有空白（含全角空格）以做稳健的子串比对；其余字符保留。 */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s\\u3000]+", "");
    }

    private static String nowIso() {
        return OffsetDateTime.now().toString();
    }
}
