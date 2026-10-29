package com.labelhub.modules.review.enums;

import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核工作台展示状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ReviewDisplayStatusEnum {

    PENDING("PENDING", "待审核"),
    WAITING_PREVIOUS("WAITING_PREVIOUS", "待前序审核"),
    WAITING_NEXT("WAITING_NEXT", "待后续审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    private final String code;

    private final String desc;

    /**
     * 根据任务项状态和当前审核员顺序解析审核展示状态。
     */
    public static ReviewDisplayStatusEnum fromReviewContext(String status, Integer currentReviewOrder,
                                                            Integer myReviewOrder) {
        if (TaskItemStatusEnum.REVIEWED.getCode().equals(status)) {
            return APPROVED;
        }
        if (TaskItemStatusEnum.REJECTED.getCode().equals(status)) {
            return REJECTED;
        }
        if (TaskItemStatusEnum.REVIEWING.getCode().equals(status)) {
            if (currentReviewOrder == null || myReviewOrder == null) {
                return PENDING;
            }
            if (currentReviewOrder.equals(myReviewOrder)) {
                return PENDING;
            }
            return currentReviewOrder < myReviewOrder ? WAITING_PREVIOUS : WAITING_NEXT;
        }
        return PENDING;
    }
}
