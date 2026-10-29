package com.labelhub.modules.annotation.vo;

import lombok.Data;

/**
 * 标注工作台题目导航项。
 */
@Data
public class ItemNavigationVO {

    private Integer index;

    private Long taskItemId;

    private String status;

    private String displayStatus;
}
