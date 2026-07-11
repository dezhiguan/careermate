package com.careermate.auth.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalAccountPurgerTest {

    private UserMapper userMapper;
    private ResumeMapper resumeMapper;
    private ResumeVersionMapper resumeVersionMapper;
    private AgentSessionMapper agentSessionMapper;
    private AgentMessageMapper agentMessageMapper;
    private InterviewSessionMapper interviewSessionMapper;
    private InterviewQuestionMapper interviewQuestionMapper;
    private JobMatchMapper jobMatchMapper;
    private CareerProfileMapper careerProfileMapper;
    private UserProfileMapper userProfileMapper;
    private LocalAccountPurger purger;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        resumeMapper = mock(ResumeMapper.class);
        resumeVersionMapper = mock(ResumeVersionMapper.class);
        agentSessionMapper = mock(AgentSessionMapper.class);
        agentMessageMapper = mock(AgentMessageMapper.class);
        interviewSessionMapper = mock(InterviewSessionMapper.class);
        interviewQuestionMapper = mock(InterviewQuestionMapper.class);
        jobMatchMapper = mock(JobMatchMapper.class);
        careerProfileMapper = mock(CareerProfileMapper.class);
        userProfileMapper = mock(UserProfileMapper.class);
        purger = new LocalAccountPurger(userMapper, resumeMapper, resumeVersionMapper, agentSessionMapper,
                agentMessageMapper, interviewSessionMapper, interviewQuestionMapper, jobMatchMapper,
                careerProfileMapper, userProfileMapper);
    }

    private UserEntity activeUser() {
        UserEntity u = new UserEntity();
        u.setId(100L);
        u.setAuthUserId(7L);
        u.setPhone("15800000000");
        u.setEmail("a@b.com");
        u.setUsername("alice");
        u.setDisplayName("Alice");
        u.setAvatarUrl("data:...");
        u.setPasswordHash("hash");
        u.setStatus("CANCELLING");
        return u;
    }

    @Test
    void purge_userFound_deletesContentAndAnonymizes() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());

        boolean result = purger.purgeByAuthUserId(7L);

        assertTrue(result);
        // 内容删除全部调用
        verify(resumeMapper).delete(any());
        verify(resumeVersionMapper).delete(any());
        verify(agentSessionMapper).delete(any());
        verify(agentMessageMapper).delete(any());
        verify(interviewSessionMapper).delete(any());
        verify(interviewQuestionMapper).delete(any());
        verify(jobMatchMapper).delete(any());
        verify(careerProfileMapper).delete(any());
        verify(userProfileMapper).delete(any());
        // 主记录匿名化 + DELETED
        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).updateById((UserEntity) cap.capture());
        UserEntity saved = cap.getValue();
        assertEquals("DELETED", saved.getStatus());
        assertNull(saved.getPhone());
        assertNull(saved.getEmail());
        assertNull(saved.getPasswordHash());
        assertEquals("deleted_100", saved.getUsername());
        assertEquals("已注销用户", saved.getDisplayName());
        assertNull(saved.getDeletionScheduledAt());
    }

    @Test
    void purge_userNotFound_returnsFalseAndDoesNothing() {
        when(userMapper.selectOne(any())).thenReturn(null);

        boolean result = purger.purgeByAuthUserId(7L);

        assertFalse(result);
        verify(userMapper, never()).updateById(any(UserEntity.class));
        verify(resumeMapper, never()).delete(any());
    }

    @Test
    void purge_alreadyDeleted_isIdempotent() {
        UserEntity u = activeUser();
        u.setStatus("DELETED");
        when(userMapper.selectOne(any())).thenReturn(u);

        boolean result = purger.purgeByAuthUserId(7L);

        assertFalse(result);
        verify(userMapper, never()).updateById(any(UserEntity.class));
        verify(resumeMapper, never()).delete(any());
    }
}
