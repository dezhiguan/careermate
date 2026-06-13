package com.careermate.task.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.task.dto.CareerTaskCreateRequest;
import com.careermate.task.dto.CareerTaskResponse;
import com.careermate.task.dto.CareerTaskUpdateRequest;
import com.careermate.task.service.CareerTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class CareerTaskController {

    private final CareerTaskService careerTaskService;

    public CareerTaskController(CareerTaskService careerTaskService) {
        this.careerTaskService = careerTaskService;
    }

    @GetMapping
    public ApiResponse<List<CareerTaskResponse>> listTasks() {
        return ApiResponse.success(careerTaskService.listCurrentUserTasks());
    }

    @PostMapping
    public ApiResponse<CareerTaskResponse> createTask(@RequestBody @Valid CareerTaskCreateRequest request) {
        return ApiResponse.success(careerTaskService.createTask(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CareerTaskResponse> updateTask(
            @PathVariable Long id,
            @RequestBody @Valid CareerTaskUpdateRequest request
    ) {
        return ApiResponse.success(careerTaskService.updateTask(id, request));
    }

    @PutMapping("/{id}/done")
    public ApiResponse<CareerTaskResponse> markDone(@PathVariable Long id) {
        return ApiResponse.success(careerTaskService.markDone(id));
    }

    @PutMapping("/{id}/todo")
    public ApiResponse<CareerTaskResponse> markTodo(@PathVariable Long id) {
        return ApiResponse.success(careerTaskService.markTodo(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        careerTaskService.deleteTask(id);
        return ApiResponse.success();
    }
}
