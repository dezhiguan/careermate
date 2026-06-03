package com.careermate.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.CareerTaskMapper;
import com.careermate.model.entity.CareerTaskEntity;
import com.careermate.security.CurrentUserContext;
import com.careermate.task.dto.CareerTaskCreateRequest;
import com.careermate.task.dto.CareerTaskResponse;
import com.careermate.task.dto.CareerTaskUpdateRequest;
import com.careermate.task.dto.DashboardTaskItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class CareerTaskService {

    private static final int DASHBOARD_TODO_LIMIT = 5;

    private final CareerTaskMapper careerTaskMapper;

    public CareerTaskService(CareerTaskMapper careerTaskMapper) {
        this.careerTaskMapper = careerTaskMapper;
    }

    public List<CareerTaskResponse> listCurrentUserTasks() {
        return listTasksForUser(requireUserId());
    }

    public List<CareerTaskResponse> listTasksForUser(Long userId) {
        List<CareerTaskEntity> entities = careerTaskMapper.selectList(
                new LambdaQueryWrapper<CareerTaskEntity>()
                        .eq(CareerTaskEntity::getUserId, userId)
                        .isNull(CareerTaskEntity::getDeletedAt)
        );
        return entities.stream()
                .sorted(taskListComparator())
                .map(this::toResponse)
                .toList();
    }

    public List<DashboardTaskItemResponse> listDashboardTodoTasks(Long userId) {
        return careerTaskMapper.selectList(
                        new LambdaQueryWrapper<CareerTaskEntity>()
                                .eq(CareerTaskEntity::getUserId, userId)
                                .isNull(CareerTaskEntity::getDeletedAt)
                                .eq(CareerTaskEntity::getStatus, CareerTaskConstants.STATUS_TODO)
                ).stream()
                .sorted(taskListComparator())
                .limit(DASHBOARD_TODO_LIMIT)
                .map(this::toDashboardItem)
                .toList();
    }

    @Transactional
    public CareerTaskResponse createTask(CareerTaskCreateRequest request) {
        Long userId = requireUserId();
        validateCategory(request.getCategory());
        String priority = normalizePriority(request.getPriority());

        LocalDateTime now = LocalDateTime.now();
        CareerTaskEntity entity = new CareerTaskEntity();
        entity.setUserId(userId);
        entity.setTitle(trimRequired(request.getTitle(), 200, "任务标题不能为空"));
        entity.setDescription(trimOptional(request.getDescription(), 1000));
        entity.setCategory(request.getCategory().trim().toUpperCase());
        entity.setPriority(priority);
        entity.setStatus(CareerTaskConstants.STATUS_TODO);
        entity.setDueDate(request.getDueDate());
        entity.setSource(CareerTaskConstants.SOURCE_MANUAL);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        careerTaskMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public CareerTaskResponse updateTask(Long taskId, CareerTaskUpdateRequest request) {
        CareerTaskEntity entity = requireOwnedTask(taskId, requireUserId());
        if (request.getTitle() != null) {
            entity.setTitle(trimRequired(request.getTitle(), 200, "任务标题不能为空"));
        }
        if (request.getDescription() != null) {
            entity.setDescription(trimOptional(request.getDescription(), 1000));
        }
        if (request.getCategory() != null) {
            validateCategory(request.getCategory());
            entity.setCategory(request.getCategory().trim().toUpperCase());
        }
        if (request.getPriority() != null) {
            entity.setPriority(normalizePriority(request.getPriority()));
        }
        if (request.getDueDate() != null) {
            entity.setDueDate(request.getDueDate());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        careerTaskMapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional
    public CareerTaskResponse markDone(Long taskId) {
        return updateStatus(taskId, CareerTaskConstants.STATUS_DONE);
    }

    @Transactional
    public CareerTaskResponse markTodo(Long taskId) {
        return updateStatus(taskId, CareerTaskConstants.STATUS_TODO);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        CareerTaskEntity entity = requireOwnedTask(taskId, requireUserId());
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        careerTaskMapper.updateById(entity);
    }

    public List<Map<String, Object>> listAgentTodoTasksForUser(Long userId) {
        return careerTaskMapper.selectList(
                        new LambdaQueryWrapper<CareerTaskEntity>()
                                .eq(CareerTaskEntity::getUserId, userId)
                                .isNull(CareerTaskEntity::getDeletedAt)
                                .eq(CareerTaskEntity::getStatus, CareerTaskConstants.STATUS_TODO)
                ).stream()
                .sorted(taskListComparator())
                .limit(CareerTaskConstants.AGENT_TODO_LIMIT)
                .map(this::toAgentTaskItem)
                .toList();
    }

    @Transactional
    public CareerTaskResponse createTaskForAgent(Long userId, CareerTaskCreateRequest request) {
        String category = request.getCategory();
        if (category == null || category.isBlank()) {
            category = CareerTaskConstants.CATEGORY_GENERAL;
        }
        validateCategory(category);
        String priority = normalizePriority(request.getPriority());

        LocalDateTime now = LocalDateTime.now();
        CareerTaskEntity entity = new CareerTaskEntity();
        entity.setUserId(userId);
        entity.setTitle(trimRequired(request.getTitle(), 200, "任务标题不能为空"));
        entity.setDescription(trimOptional(request.getDescription(), 1000));
        entity.setCategory(category.trim().toUpperCase());
        entity.setPriority(priority);
        entity.setStatus(CareerTaskConstants.STATUS_TODO);
        entity.setDueDate(request.getDueDate());
        entity.setSource(CareerTaskConstants.SOURCE_AGENT);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        careerTaskMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public CareerTaskResponse markDoneForUser(Long userId, Long taskId) {
        return updateStatusForUser(userId, taskId, CareerTaskConstants.STATUS_DONE);
    }

    public CareerTaskEntity findTodoTaskForUser(Long userId, Long taskId) {
        if (taskId == null) {
            return null;
        }
        return careerTaskMapper.selectOne(
                new LambdaQueryWrapper<CareerTaskEntity>()
                        .eq(CareerTaskEntity::getId, taskId)
                        .eq(CareerTaskEntity::getUserId, userId)
                        .isNull(CareerTaskEntity::getDeletedAt)
                        .eq(CareerTaskEntity::getStatus, CareerTaskConstants.STATUS_TODO)
                        .last("LIMIT 1")
        );
    }

    public CareerTaskEntity findTodoTaskByTitleKeyword(Long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        return careerTaskMapper.selectList(
                        new LambdaQueryWrapper<CareerTaskEntity>()
                                .eq(CareerTaskEntity::getUserId, userId)
                                .isNull(CareerTaskEntity::getDeletedAt)
                                .eq(CareerTaskEntity::getStatus, CareerTaskConstants.STATUS_TODO)
                ).stream()
                .filter(t -> t.getTitle() != null && t.getTitle().contains(normalized))
                .sorted(taskListComparator())
                .findFirst()
                .orElse(null);
    }

    private CareerTaskResponse updateStatusForUser(Long userId, Long taskId, String status) {
        if (!CareerTaskConstants.STATUSES.contains(status)) {
            throw new BizException(400, "无效的任务状态");
        }
        CareerTaskEntity entity = requireOwnedTask(taskId, userId);
        entity.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        if (CareerTaskConstants.STATUS_DONE.equals(status)) {
            entity.setCompletedAt(now);
        } else {
            entity.setCompletedAt(null);
        }
        careerTaskMapper.updateById(entity);
        return toResponse(entity);
    }

    private Map<String, Object> toAgentTaskItem(CareerTaskEntity entity) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("taskId", entity.getId());
        item.put("title", entity.getTitle());
        item.put("category", entity.getCategory());
        item.put("priority", entity.getPriority());
        item.put("status", entity.getStatus());
        if (entity.getDueDate() != null) {
            item.put("dueDate", entity.getDueDate().toString());
        }
        return item;
    }

    private CareerTaskResponse updateStatus(Long taskId, String status) {
        if (!CareerTaskConstants.STATUSES.contains(status)) {
            throw new BizException(400, "无效的任务状态");
        }
        CareerTaskEntity entity = requireOwnedTask(taskId, requireUserId());
        entity.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        if (CareerTaskConstants.STATUS_DONE.equals(status)) {
            entity.setCompletedAt(now);
        } else {
            entity.setCompletedAt(null);
        }
        careerTaskMapper.updateById(entity);
        return toResponse(entity);
    }

    private CareerTaskEntity requireOwnedTask(Long taskId, Long userId) {
        CareerTaskEntity entity = careerTaskMapper.selectOne(
                new LambdaQueryWrapper<CareerTaskEntity>()
                        .eq(CareerTaskEntity::getId, taskId)
                        .eq(CareerTaskEntity::getUserId, userId)
                        .isNull(CareerTaskEntity::getDeletedAt)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new BizException(404, "任务不存在");
        }
        return entity;
    }

    private Comparator<CareerTaskEntity> taskListComparator() {
        return Comparator
                .comparingInt((CareerTaskEntity t) -> statusOrder(t.getStatus()))
                .thenComparingInt((CareerTaskEntity t) -> priorityOrder(t.getPriority()))
                .thenComparing(CareerTaskEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int statusOrder(String status) {
        if (CareerTaskConstants.STATUS_TODO.equals(status)) {
            return 0;
        }
        if (CareerTaskConstants.STATUS_DONE.equals(status)) {
            return 1;
        }
        return 2;
    }

    private int priorityOrder(String priority) {
        if (CareerTaskConstants.PRIORITY_HIGH.equals(priority)) {
            return 0;
        }
        if (CareerTaskConstants.PRIORITY_MEDIUM.equals(priority)) {
            return 1;
        }
        if (CareerTaskConstants.PRIORITY_LOW.equals(priority)) {
            return 2;
        }
        return 3;
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BizException(400, "任务分类不能为空");
        }
        String normalized = category.trim().toUpperCase();
        if (!CareerTaskConstants.CATEGORIES.contains(normalized)) {
            throw new BizException(400, "无效的任务分类");
        }
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return CareerTaskConstants.PRIORITY_MEDIUM;
        }
        String normalized = priority.trim().toUpperCase();
        if (!CareerTaskConstants.PRIORITIES.contains(normalized)) {
            throw new BizException(400, "无效的任务优先级");
        }
        return normalized;
    }

    private String trimRequired(String value, int maxLen, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(400, message);
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private String trimOptional(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private CareerTaskResponse toResponse(CareerTaskEntity entity) {
        return CareerTaskResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .dueDate(entity.getDueDate())
                .source(entity.getSource())
                .relatedType(entity.getRelatedType())
                .relatedId(entity.getRelatedId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    private DashboardTaskItemResponse toDashboardItem(CareerTaskEntity entity) {
        return DashboardTaskItemResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .category(entity.getCategory())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .dueDate(entity.getDueDate())
                .build();
    }

    private Long requireUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
