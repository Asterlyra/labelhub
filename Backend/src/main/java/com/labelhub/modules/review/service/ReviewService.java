package com.labelhub.modules.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.labelhub.modules.review.dto.ReviewModifyDTO;
import com.labelhub.modules.review.dto.ReviewRejectDTO;
import com.labelhub.modules.review.dto.ReviewTaskQueryDTO;
import com.labelhub.modules.review.vo.ReviewItemDetailVO;
import com.labelhub.modules.review.vo.ReviewTaskVO;
import com.labelhub.modules.review.vo.ReviewWorkspaceInitVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 人工审核服务。
 */
public interface ReviewService {

    /**
     * 分页查询当前审核员的审核任务。
     */
    Page<ReviewTaskVO> listMyTasks(ReviewTaskQueryDTO dto);

    /**
     * 初始化审核工作台。
     */
    ReviewWorkspaceInitVO initWorkspace(Long taskId);

    /**
     * 查询审核项详情。
     */
    ReviewItemDetailVO getItemDetail(Long taskItemId);

    /**
     * 查询任务项 AI 预审结果。
     */
    JsonNode getAiAuditResult(Long taskItemId);

    /**
     * 查询任务 AI 预审结果列表。
     */
    JsonNode listAiAuditResults(Long taskId);

    /**
     * 审核通过。
     */
    void approve(Long taskItemId);

    /**
     * 审核驳回。
     */
    void reject(Long taskItemId, ReviewRejectDTO dto);

    /**
     * 修改标注数据并审核通过。
     */
    void modify(Long taskItemId, ReviewModifyDTO dto);

    /**
     * 修改标注数据并审核通过，同时处理图片/文件上传物料。
     */
    void modify(Long taskItemId, ReviewModifyDTO dto, Map<String, MultipartFile> componentFiles);
}
