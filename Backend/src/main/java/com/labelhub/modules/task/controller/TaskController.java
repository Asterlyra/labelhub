package com.labelhub.modules.task.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.result.R;
import com.labelhub.modules.task.dto.TaskCreateDTO;
import com.labelhub.modules.task.dto.TaskItemQueryDTO;
import com.labelhub.modules.task.dto.TaskMarketplaceQueryDTO;
import com.labelhub.modules.task.dto.TaskQueryDTO;
import com.labelhub.modules.task.dto.TaskUpdateDTO;
import com.labelhub.modules.task.service.TaskService;
import com.labelhub.modules.task.vo.FlowTimelineVO;
import com.labelhub.modules.task.vo.TaskDetailVO;
import com.labelhub.modules.task.vo.TaskItemVO;
import com.labelhub.modules.task.vo.TaskMarketplaceVO;
import com.labelhub.modules.task.vo.TaskProgressVO;
import com.labelhub.modules.task.vo.TaskRoleDetailVO;
import com.labelhub.modules.task.vo.TaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务管理接口。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * 创建任务草稿。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping
    public R<TaskDetailVO> create(@Valid @RequestBody TaskCreateDTO dto) {
        return R.ok(taskService.createTask(dto));
    }

    /**
     * 更新草稿任务，并返回包含审核员信息的最新任务详情。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PutMapping("/{id}")
    public R<TaskDetailVO> update(@PathVariable Long id, @Valid @RequestBody TaskUpdateDTO dto) {
        return R.ok(taskService.updateTask(id, dto));
    }

    /**
     * 分页查询任务列表。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping
    public R<Page<TaskVO>> list(TaskQueryDTO dto) {
        return R.ok(taskService.listTasks(dto));
    }

    /**
     * 查询当前管理员创建的任务列表。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/my")
    public R<Page<TaskVO>> listMine(TaskQueryDTO dto) {
        return R.ok(taskService.listMyTasks(dto));
    }

    /**
     * 查询任务广场列表。
     */
    @SaCheckRole("LABELER")
    @GetMapping("/marketplace")
    public R<Page<TaskMarketplaceVO>> marketplace(TaskMarketplaceQueryDTO dto) {
        return R.ok(taskService.listMarketplaceTasks(dto));
    }

    /**
     * 查询任务广场标签。
     */
    @SaCheckRole("LABELER")
    @GetMapping("/marketplace/tags")
    public R<List<String>> marketplaceTags() {
        return R.ok(taskService.listMarketplaceTags());
    }

    /**
     * 按当前用户角色查询统一任务详情。
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public R<TaskRoleDetailVO> detail(@PathVariable Long id) {
        return R.ok(taskService.getTaskDetailForCurrentUser(id));
    }

    /**
     * 删除草稿任务。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return R.ok();
    }

    /**
     * 发布任务。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/{id}/publish")
    public R<TaskDetailVO> publish(@PathVariable Long id) {
        return R.ok(taskService.publishTask(id));
    }

    /**
     * 暂停任务。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/{id}/pause")
    public R<Void> pause(@PathVariable Long id) {
        return handleVoidAction(() -> taskService.pauseTask(id));
    }

    /**
     * 恢复任务。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/{id}/resume")
    public R<Void> resume(@PathVariable Long id) {
        return handleVoidAction(() -> taskService.resumeTask(id));
    }

    /**
     * 结束任务。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/{id}/end")
    public R<Void> end(@PathVariable Long id) {
        return handleVoidAction(() -> taskService.endTask(id));
    }

    /**
     * 标注员领取任务数据项。
     */
    @SaCheckRole("LABELER")
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 300, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/{id}/claim")
    public R<Integer> claim(@PathVariable Long id) {
        return R.ok(taskService.claimTask(id));
    }

    /**
     * 查询任务进度。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/{id}/progress")
    public R<TaskProgressVO> progress(@PathVariable Long id) {
        return R.ok(taskService.getProgress(id));
    }

    /**
     * 管理员查询任务数据项列表。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/{id}/items")
    public R<Page<TaskItemVO>> items(@PathVariable Long id,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                     @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        TaskItemQueryDTO dto = new TaskItemQueryDTO();
        dto.setStatus(status);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        return R.ok(taskService.listTaskItems(id, dto));
    }

    /**
     * 查询任务项流转时间线。
     * 项目管理员、标注员本人和任务审核员均可访问。
     */
    @SaCheckLogin
    @GetMapping("/items/{taskItemId}/timeline")
    public R<List<FlowTimelineVO>> flowTimeline(@PathVariable Long taskItemId) {
        return R.ok(taskService.getFlowTimeline(taskItemId));
    }

    private R<Void> handleVoidAction(Runnable runnable) {
        runnable.run();
        return R.ok();
    }
}
