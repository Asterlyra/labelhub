package com.labelhub.modules.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.dataset.entity.DatasetItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据项 Mapper。
 */
@Mapper
public interface DatasetItemMapper extends BaseMapper<DatasetItem> {

    /**
     * 批量插入数据集解析项。
     */
    @Insert({
            "<script>",
            "INSERT INTO dataset_item",
            "(dataset_id, item_key, row_index, data_type, content_json, parse_status, error_message)",
            "VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(",
            "#{item.datasetId},",
            "#{item.itemKey},",
            "#{item.rowIndex},",
            "#{item.dataType},",
            "#{item.contentJson},",
            "#{item.parseStatus},",
            "#{item.errorMessage}",
            ")",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("items") List<DatasetItem> items);
}
