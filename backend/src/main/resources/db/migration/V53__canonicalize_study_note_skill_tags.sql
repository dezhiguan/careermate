-- 八股题库技能标签一次性归一。
--
-- 背景：skill_tag 是自由文本，筛选走精确匹配，导致「Java并发 / java并发 / 并发编程」互不相通；
-- 平台题库带过来的 category（技术/行为/HR）更是完全落在筛选面之外。
-- 服务端已改为「写入即归一」（SkillTagCatalog），这里把存量数据一次性对齐到同一套规范写法，
-- 否则筛选面统计出的标签会与精确匹配查出的结果对不上。
--
-- 别名表须与 com.careermate.study.catalog.SkillTagCatalog 保持一致；
-- 未命中别名的标签原样保留（用户自建词汇不吞），仅做空白归一。

-- 1) 空白归一：去首尾空白 + 折叠内部空白，全空白置 NULL
UPDATE study_notes
SET skill_tag = NULLIF(btrim(regexp_replace(skill_tag, '\s+', ' ', 'g')), '')
WHERE skill_tag IS NOT NULL;

-- 2) 同义写法归并（比较键 = 小写 + 去掉全部空白）
UPDATE study_notes
SET skill_tag = CASE lower(regexp_replace(skill_tag, '\s+', '', 'g'))
    -- AI 方向
    WHEN 'ai'             THEN 'AI大模型'
    WHEN 'ai大模型'        THEN 'AI大模型'
    WHEN '大模型'          THEN 'AI大模型'
    WHEN '大模型应用'       THEN 'AI大模型'
    WHEN 'llm'            THEN 'AI大模型'
    WHEN '大语言模型'       THEN 'AI大模型'
    WHEN 'aigc'           THEN 'AI大模型'
    WHEN 'rag'            THEN 'RAG'
    WHEN '检索增强'         THEN 'RAG'
    WHEN '检索增强生成'      THEN 'RAG'
    WHEN 'agent'          THEN 'Agent'
    WHEN 'aiagent'        THEN 'Agent'
    WHEN 'agent开发'       THEN 'Agent'
    WHEN '智能体'          THEN 'Agent'
    WHEN '多智能体'         THEN 'Agent'
    -- Java 基础方向
    WHEN 'java并发'        THEN 'Java并发'
    WHEN 'java并发编程'     THEN 'Java并发'
    WHEN '并发'            THEN 'Java并发'
    WHEN '并发编程'         THEN 'Java并发'
    WHEN '多线程'          THEN 'Java并发'
    WHEN 'juc'            THEN 'Java并发'
    WHEN 'concurrency'    THEN 'Java并发'
    WHEN 'jvm'            THEN 'JVM'
    WHEN 'java虚拟机'      THEN 'JVM'
    WHEN '垃圾回收'         THEN 'JVM'
    WHEN 'gc'             THEN 'JVM'
    WHEN 'mysql'          THEN 'MySQL'
    WHEN 'redis'          THEN 'Redis'
    -- 通用方向
    WHEN '算法'            THEN '算法'
    WHEN 'algorithm'      THEN '算法'
    WHEN '数据结构与算法'    THEN '算法'
    WHEN 'leetcode'       THEN '算法'
    WHEN '系统设计'         THEN '系统设计'
    WHEN 'systemdesign'   THEN '系统设计'
    WHEN '架构设计'         THEN '系统设计'
    WHEN '行为面'          THEN '行为面'
    WHEN '行为'            THEN '行为面'
    WHEN '行为面试'         THEN '行为面'
    WHEN 'behavior'       THEN '行为面'
    WHEN 'behavioral'     THEN '行为面'
    WHEN 'hr'             THEN '行为面'
    WHEN 'hr面'           THEN '行为面'
    ELSE skill_tag
END
WHERE skill_tag IS NOT NULL;
