package com.careermate.study.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.StudyNoteMapper;
import com.careermate.model.entity.StudyNoteEntity;
import com.careermate.study.dto.SaveStudyNoteRequest;
import com.careermate.study.dto.StudyNotePageVO;
import com.careermate.study.dto.StudyNoteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

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
        String skillTag = request == null ? null : trimTo(request.getSkillTag(), MAX_SKILL);
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
        String skill = trimOrNull(skillTag);
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
