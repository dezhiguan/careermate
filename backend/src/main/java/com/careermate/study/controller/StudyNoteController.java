package com.careermate.study.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.security.CurrentUserContext;
import com.careermate.study.dto.SaveStudyNoteRequest;
import com.careermate.study.dto.StudyNotePageVO;
import com.careermate.study.dto.StudyNoteVO;
import com.careermate.study.dto.StudySkillTagsVO;
import com.careermate.study.service.StudyNoteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 八股题库（个人）REST 接口：收录平台题 + 手写答案，跨机会复用。
 */
@RestController
@RequestMapping("/api/study/notes")
public class StudyNoteController {

    private final StudyNoteService studyNoteService;

    public StudyNoteController(StudyNoteService studyNoteService) {
        this.studyNoteService = studyNoteService;
    }

    /**
     * 分页查询个人题库（可按技能 + 关键词过滤）。
     *
     * <p>{@code untagged=true} 只看未打标签的题，与 {@code skill} 互斥（此时 skill 被忽略）。
     */
    @GetMapping
    public ApiResponse<StudyNotePageVO> list(
            @RequestParam(required = false) String skill,
            @RequestParam(defaultValue = "false") boolean untagged,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(
                studyNoteService.list(CurrentUserContext.getUserId(), skill, untagged, keyword, page, size));
    }

    /** 筛选面标签：预置标签 ∪ 用户自建标签，各带题数。 */
    @GetMapping("/skills")
    public ApiResponse<StudySkillTagsVO> skills() {
        return ApiResponse.success(studyNoteService.skills(CurrentUserContext.getUserId()));
    }

    /** 收录/更新一条题（按 question 去重 upsert）。 */
    @PostMapping
    public ApiResponse<StudyNoteVO> save(@RequestBody SaveStudyNoteRequest request) {
        return ApiResponse.success(studyNoteService.save(CurrentUserContext.getUserId(), request));
    }

    /** 删除（软删）一条题。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        studyNoteService.delete(CurrentUserContext.getUserId(), id);
        return ApiResponse.success();
    }
}
