package com.labelhub.modules.task.convert;

import com.labelhub.modules.task.dto.TaskAIReviewConfigDTO;
import com.labelhub.modules.task.entity.TaskAIReviewConfig;
import com.labelhub.modules.task.vo.TaskAIReviewConfigVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 任务AI预审配置对象转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskAIReviewConfigConvert {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "taskId", ignore = true)
    @Mapping(target = "dimensions", ignore = true)
    TaskAIReviewConfig toEntity(TaskAIReviewConfigDTO dto);

    @Mapping(target = "dimensions", ignore = true)
    TaskAIReviewConfigVO toVO(TaskAIReviewConfig config);
}
