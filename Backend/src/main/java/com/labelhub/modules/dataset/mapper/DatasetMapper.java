package com.labelhub.modules.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.dataset.entity.Dataset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据集 Mapper。
 */
@Mapper
public interface DatasetMapper extends BaseMapper<Dataset> {
}
