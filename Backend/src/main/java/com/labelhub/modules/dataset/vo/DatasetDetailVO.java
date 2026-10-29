package com.labelhub.modules.dataset.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 数据集详情响应对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DatasetDetailVO extends DatasetVO {

    private List<DatasetFileVO> files;
}
