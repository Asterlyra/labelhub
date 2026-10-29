package com.labelhub.modules.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.labelhub.modules.task.dto.TaskCreateDTO;
import com.labelhub.modules.task.dto.TaskItemQueryDTO;
import com.labelhub.modules.task.dto.TaskMarketplaceQueryDTO;
import com.labelhub.modules.task.dto.TaskQueryDTO;
import com.labelhub.modules.task.dto.TaskUpdateDTO;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.vo.LabelerTaskVO;
import com.labelhub.modules.task.vo.TaskDetailVO;
import com.labelhub.modules.task.vo.TaskItemVO;
import com.labelhub.modules.task.vo.FlowTimelineVO;
import com.labelhub.modules.task.vo.TaskMarketplaceVO;
import com.labelhub.modules.task.vo.TaskProgressVO;
import com.labelhub.modules.task.vo.TaskRoleDetailVO;
import com.labelhub.modules.task.vo.TaskVO;

import java.util.List;

/**
 * 任务管理服务接口。
 */
public interface TaskService extends IService<Task> {

    /**
     * 创建任务。
     */
    TaskDetailVO createTask(TaskCreateDTO dto);

    /**
     * 更新任务并返回最新任务详情。
     */
    TaskDetailVO updateTask(Long taskId, TaskUpdateDTO dto);

    /**
     * 分页查询任务列表。
     */
    Page<TaskVO> listTasks(TaskQueryDTO dto);

    /**
     * 分页查询当前管理员创建的任务列表。
     */
    Page<TaskVO> listMyTasks(TaskQueryDTO dto);

    /**
     * 查询任务详情。
     */
    TaskDetailVO getTaskDetail(Long taskId);

    /**
     * 按当前用户角色查询统一任务详情。
     */
    TaskRoleDetailVO getTaskDetailForCurrentUser(Long taskId);

    /**
     * 删除任务。
     */
    void deleteTask(Long taskId);

    /**
     * 发布任务。
     */
    TaskDetailVO publishTask(Long taskId);

    /**
     * 暂停任务。
     */
    void pauseTask(Long taskId);

    /**
     * 恢复任务。
     */
    void resumeTask(Long taskId);

    /**
     * 结束任务。
     */
    void endTask(Long taskId);

    /**
     * 领取任务数据项。
     */
    Integer claimTask(Long taskId);

    /**
     * 查询任务进度。
     */
    TaskProgressVO getProgress(Long taskId);

    /**
     * 管理员分页查询任务数据项。
     */
    Page<TaskItemVO> listTaskItems(Long taskId, TaskItemQueryDTO dto);

    /**
     * 查询任务广场列表。
     */
    Page<TaskMarketplaceVO> listMarketplaceTasks(TaskMarketplaceQueryDTO dto);

    /**
     * 查询当前标注员已领取的任务广场列表。
     */
    Page<LabelerTaskVO> listMyMarketplaceTasks(TaskMarketplaceQueryDTO dto);

    /**
     * 查询任务广场标签。
     */
    List<String> listMarketplaceTags();

    /**
     * 查询任务项流转时间线。
     */
    List<FlowTimelineVO> getFlowTimeline(Long taskItemId);
}
