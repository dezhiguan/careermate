package com.careermate.study.service;

import com.careermate.common.exception.BizException;
import com.careermate.mapper.StudyNoteMapper;
import com.careermate.model.entity.StudyNoteEntity;
import com.careermate.study.dto.SaveStudyNoteRequest;
import com.careermate.study.dto.StudyNotePageVO;
import com.careermate.study.dto.StudyNoteVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyNoteServiceTest {

    @Mock
    private StudyNoteMapper mapper;

    private StudyNoteService service;

    @BeforeEach
    void setUp() {
        service = new StudyNoteService(mapper);
    }

    @Test
    void saveNewInserts() {
        when(mapper.selectOne(any())).thenReturn(null);
        SaveStudyNoteRequest req = new SaveStudyNoteRequest();
        req.setQuestion("Redis 持久化 RDB vs AOF");
        req.setSkillTag("Redis");
        req.setAnswer("RDB 快照、AOF 追加日志…");

        StudyNoteVO vo = service.save(1L, req);

        verify(mapper, times(1)).insert(any(StudyNoteEntity.class));
        assertEquals("Redis 持久化 RDB vs AOF", vo.question());
        assertEquals("Redis", vo.skillTag());
    }

    @Test
    void saveExistingUpdatesByHash() {
        StudyNoteEntity existing = entity(9L, 1L, "缓存一致性", "旧答案");
        when(mapper.selectOne(any())).thenReturn(existing);
        SaveStudyNoteRequest req = new SaveStudyNoteRequest();
        req.setQuestion("缓存一致性");
        req.setAnswer("新答案：先删缓存再更库…");

        StudyNoteVO vo = service.save(1L, req);

        verify(mapper, never()).insert(any(StudyNoteEntity.class));
        ArgumentCaptor<StudyNoteEntity> cap = ArgumentCaptor.forClass(StudyNoteEntity.class);
        verify(mapper).updateById(cap.capture());
        assertEquals("新答案：先删缓存再更库…", cap.getValue().getAnswer());
        assertEquals(9L, vo.id());
    }

    @Test
    void saveBlankQuestionRejected() {
        SaveStudyNoteRequest req = new SaveStudyNoteRequest();
        req.setQuestion("   ");
        assertThrows(BizException.class, () -> service.save(1L, req));
    }

    @Test
    void listPaginatesAndMaps() {
        when(mapper.selectCount(any())).thenReturn(3L);
        when(mapper.selectList(any())).thenReturn(List.of(
                entity(1L, 1L, "题A", "答A"),
                entity(2L, 1L, "题B", "答B")));

        StudyNotePageVO pageVO = service.list(1L, "Java", false, "并发", 1, 2);

        assertEquals(3L, pageVO.total());
        assertEquals(2, pageVO.items().size());
        assertEquals(1, pageVO.page());
        assertEquals("题A", pageVO.items().get(0).question());
    }

    @Test
    void listEmptyWhenZeroTotal() {
        when(mapper.selectCount(any())).thenReturn(0L);
        StudyNotePageVO pageVO = service.list(1L, null, false, null, 1, 10);
        assertEquals(0L, pageVO.total());
        assertEquals(0, pageVO.items().size());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void deleteSoftDeletesOwned() {
        when(mapper.selectById(9L)).thenReturn(entity(9L, 1L, "题", "答"));
        service.delete(1L, 9L);
        ArgumentCaptor<StudyNoteEntity> cap = ArgumentCaptor.forClass(StudyNoteEntity.class);
        verify(mapper).updateById(cap.capture());
        assertNotNull(cap.getValue().getDeletedAt());
    }

    @Test
    void deleteForeignRejected() {
        when(mapper.selectById(9L)).thenReturn(entity(9L, 2L, "题", "答"));
        assertThrows(BizException.class, () -> service.delete(1L, 9L));
    }

    @Test
    void deleteNotFoundRejected() {
        when(mapper.selectById(9L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.delete(1L, 9L));
    }

    @Test
    void unauthenticatedRejected() {
        assertThrows(BizException.class, () -> service.list(null, null, false, null, 1, 10));
        assertThrows(BizException.class, () -> service.save(null, new SaveStudyNoteRequest()));
    }

    @Test
    void normalizeCollapsesWhitespaceForDedup() {
        assertEquals(StudyNoteService.sha256(StudyNoteService.normalize("a  b\nc")),
                StudyNoteService.sha256(StudyNoteService.normalize(" a b c ")));
    }

    private static StudyNoteEntity entity(Long id, Long userId, String q, String a) {
        StudyNoteEntity e = new StudyNoteEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setQuestion(q);
        e.setAnswer(a);
        e.setSkillTag("Java");
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}
