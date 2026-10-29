package com.labelhub.modules.task.convert;

import com.labelhub.modules.task.dto.TaskCreateDTO;
import com.labelhub.modules.task.dto.TaskUpdateDTO;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.vo.TaskDetailVO;
import com.labelhub.modules.task.vo.TaskItemVO;
import com.labelhub.modules.task.vo.TaskVO;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 任务模块对象转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskConvert {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    Task toEntity(TaskCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    Task toEntity(TaskUpdateDTO dto);

    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "progress", ignore = true)
    @Named("toTaskVO")
    TaskVO toVO(Task task);

    @IterableMapping(qualifiedByName = "toTaskVO")
    List<TaskVO> toVOList(List<Task> tasks);

    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "progress", ignore = true)
    @Mapping(target = "reviewerIds", ignore = true)
    @Mapping(target = "distributionConfig", ignore = true)
    TaskDetailVO toDetailVO(Task task);

    @Mapping(target = "annotationData", ignore = true)
    TaskItemVO toItemVO(TaskItem taskItem);

    List<TaskItemVO> toItemVOList(List<TaskItem> taskItems);
}
