package com.careermate.study.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.StudyNoteMapper;
import com.careermate.model.entity.StudyNoteEntity;
import com.careermate.study.catalog.SkillTagCatalog;
import com.careermate.study.dto.SaveStudyNoteRequest;
import com.careermate.study.dto.StudyNotePageVO;
import com.careermate.study.dto.StudyNoteVO;
import com.careermate.study.dto.StudySkillTagVO;
import com.careermate.study.dto.StudySkillTagsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 八股题库（个人）服务：用户从平台题库收录的题 + 手写答案，user 级、跨机会复用。
 *
 * <p>按 question 归一化 hash 去重 upsert；分页读 page/size（web 页码与移动滚动共用同一取数方式）。
 */
@Slf4j
@Service
public class StudyNoteService {

    private static final int MAX_QUESTION = 2000;
    private static final int MAX_ANSWER = 20000;
    private static final int MAX_SKILL = 64;

    private final StudyNoteMapper studyNoteMapper;

    public StudyNoteService(StudyNoteMapper studyNoteMapper) {
        this.studyNoteMapper = studyNoteMapper;
    }

    /** 分页查询个人题库（可按技能标签 + 关键词过滤，最近更新倒序）。 */
    public StudyNotePageVO list(Long userId, String skillTag, String keyword, int page, int size) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));

        LambdaQueryWrapper<StudyNoteEntity> filter = baseFilter(userId, skillTag, keyword);
        Long total = studyNoteMapper.selectCount(filter);
        long totalCount = total == null ? 0L : total;

        int offset = (safePage - 1) * safeSize;
        List<StudyNoteEntity> rows = totalCount == 0 ? List.of() : studyNoteMapper.selectList(
                baseFilter(userId, skillTag, keyword)
                        .orderByDesc(StudyNoteEntity::getUpdatedAt)
                        .last("LIMIT " + safeSize + " OFFSET " + offset));

        List<StudyNoteVO> items = rows.stream()
                .map(e -> new StudyNoteVO(e.getId(), e.getQuestion(), e.getSkillTag(), e.getAnswer(), e.getUpdatedAt()))
                .toList();
        return new StudyNotePageVO(items, totalCount, safePage, safeSize);
    }

    /**
     * 筛选面标签：预置标签 ∪ 该用户已用过的标签，各带题数。
     *
     * <p>写侧标签是自由文本，若筛选面只认死写的预置项，用户自建标签（含平台题带过来的分类）
     * 就会「存得进去、筛不出来」。这里把两边合并返回，保证任何已存在的标签都有入口。
     */
    public StudySkillTagsVO skills(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        QueryWrapper<StudyNoteEntity> grouped = new QueryWrapper<StudyNoteEntity>()
                .select("skill_tag", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .isNull("deleted_at")
                .groupBy("skill_tag");

        Map<String, Long> counts = new LinkedHashMap<>();
        long untagged = 0L;
        for (Map<String, Object> row : studyNoteMapper.selectMaps(grouped)) {
            long cnt = asLong(pick(row, "cnt"));
            // 历史数据可能存着未归一的写法，读出来再归一一次，同义标签的计数合并到一起
            String tag = SkillTagCatalog.canonicalize(asString(pick(row, "skill_tag")));
            if (tag == null) {
                untagged += cnt;
            } else {
                counts.merge(tag, cnt, Long::sum);
            }
        }

        List<StudySkillTagVO> tags = new ArrayList<>();
        for (String preset : SkillTagCatalog.PRESET) {
            tags.add(new StudySkillTagVO(preset, counts.getOrDefault(preset, 0L), true));
        }
        counts.entrySet().stream()
                .filter(e -> !SkillTagCatalog.isPreset(e.getKey()))
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(e -> -e.getValue())
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> tags.add(new StudySkillTagVO(e.getKey(), e.getValue(), false)));

        long tagged = counts.values().stream().mapToLong(Long::longValue).sum();
        return new StudySkillTagsVO(List.copyOf(tags), tagged, untagged);
    }

    /** 收录/更新一条题（按 question 去重 upsert）。 */
    @Transactional
    public StudyNoteVO save(Long userId, SaveStudyNoteRequest request) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        String question = request == null ? null : trimTo(request.getQuestion(), MAX_QUESTION);
        if (question == null) {
            throw new BizException(400, "题目不能为空");
        }
        // 写入即归一：大小写/空白/同义写法收敛到唯一形态，否则筛选面会碎成一堆同义标签
        String skillTag = trimTo(SkillTagCatalog.canonicalize(request == null ? null : request.getSkillTag()), MAX_SKILL);
        String answer = request == null ? null : trimTo(request.getAnswer(), MAX_ANSWER);
        String hash = sha256(normalize(question));
        LocalDateTime now = LocalDateTime.now();

        StudyNoteEntity existing = studyNoteMapper.selectOne(
                new LambdaQueryWrapper<StudyNoteEntity>()
                        .eq(StudyNoteEntity::getUserId, userId)
                        .eq(StudyNoteEntity::getQuestionHash, hash)
                        .isNull(StudyNoteEntity::getDeletedAt)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setSkillTag(skillTag);
            existing.setAnswer(answer);
            existing.setUpdatedAt(now);
            studyNoteMapper.updateById(existing);
            return toVO(existing);
        }

        StudyNoteEntity entity = new StudyNoteEntity();
        entity.setUserId(userId);
        entity.setQuestion(question);
        entity.setQuestionHash(hash);
        entity.setSkillTag(skillTag);
        entity.setAnswer(answer);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        studyNoteMapper.insert(entity);
        return toVO(entity);
    }

    /** 删除（软删）一条题。 */
    @Transactional
    public void delete(Long userId, Long id) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        if (id == null) {
            throw new BizException(400, "缺少 id");
        }
        StudyNoteEntity entity = studyNoteMapper.selectById(id);
        if (entity == null || entity.getDeletedAt() != null) {
            throw new BizException(404, "题目不存在");
        }
        if (!userId.equals(entity.getUserId())) {
            throw new BizException(403, "无权删除该题目");
        }
        entity.setDeletedAt(LocalDateTime.now());
        studyNoteMapper.updateById(entity);
    }

    private static LambdaQueryWrapper<StudyNoteEntity> baseFilter(Long userId, String skillTag, String keyword) {
        LambdaQueryWrapper<StudyNoteEntity> wrapper = new LambdaQueryWrapper<StudyNoteEntity>()
                .eq(StudyNoteEntity::getUserId, userId)
                .isNull(StudyNoteEntity::getDeletedAt);
        // 读侧同样归一，保证「用 java并发 筛」能命中存成「Java并发」的题
        String skill = SkillTagCatalog.canonicalize(skillTag);
        if (skill != null) {
            wrapper.eq(StudyNoteEntity::getSkillTag, skill);
        }
        String kw = trimOrNull(keyword);
        if (kw != null) {
            wrapper.and(w -> w.like(StudyNoteEntity::getQuestion, kw).or().like(StudyNoteEntity::getAnswer, kw));
        }
        return wrapper;
    }

    private static StudyNoteVO toVO(StudyNoteEntity e) {
        return new StudyNoteVO(e.getId(), e.getQuestion(), e.getSkillTag(), e.getAnswer(), e.getUpdatedAt());
    }

    /** 归一化：去首尾空白 + 折叠内部空白，稳健去重。 */
    static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 一定存在；兜底用 hashCode 也不影响正确性
            return Integer.toHexString(s.hashCode());
        }
    }

    /** 取聚合结果里的列：忽略大小写与下划线，兼容驱动/映射层对列标签的不同处理。 */
    private static Object pick(Map<String, Object> row, String column) {
        Object direct = row.get(column);
        if (direct != null) {
            return direct;
        }
        String want = column.replace("_", "");
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().replace("_", "").equalsIgnoreCase(want)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimTo(String s, int max) {
        String t = trimOrNull(s);
        if (t == null) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }
}
