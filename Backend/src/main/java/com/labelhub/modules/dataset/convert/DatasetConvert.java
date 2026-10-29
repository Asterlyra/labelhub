package com.labelhub.modules.dataset.convert;

import com.labelhub.modules.dataset.dto.DatasetUploadDTO;
import com.labelhub.modules.dataset.entity.Dataset;
import com.labelhub.modules.dataset.entity.DatasetFile;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.vo.DatasetDetailVO;
import com.labelhub.modules.dataset.vo.DatasetFileVO;
import com.labelhub.modules.dataset.vo.DatasetItemVO;
import com.labelhub.modules.dataset.vo.DatasetVO;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 数据集模块对象转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DatasetConvert {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sourceFormat", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "totalCount", ignore = true)
    @Mapping(target = "successCount", ignore = true)
    @Mapping(target = "failedCount", ignore = true)
    @Mapping(target = "schemaJson", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    Dataset toEntity(DatasetUploadDTO dto);

    @Mapping(target = "schema", ignore = true)
    @Named("toDatasetVO")
    DatasetVO toVO(Dataset dataset);

    @IterableMapping(qualifiedByName = "toDatasetVO")
    List<DatasetVO> toVOList(List<Dataset> datasets);

    @Mapping(target = "schema", ignore = true)
    @Mapping(target = "files", ignore = true)
    DatasetDetailVO toDetailVO(Dataset dataset);

    DatasetFileVO toFileVO(DatasetFile datasetFile);

    List<DatasetFileVO> toFileVOList(List<DatasetFile> datasetFiles);

    @Mapping(target = "content", ignore = true)
    DatasetItemVO toItemVO(DatasetItem datasetItem);

    List<DatasetItemVO> toItemVOList(List<DatasetItem> datasetItems);
}
