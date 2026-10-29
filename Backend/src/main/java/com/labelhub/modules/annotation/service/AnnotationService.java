package com.labelhub.modules.annotation.service;

import com.labelhub.modules.annotation.dto.AnnotationSaveDTO;
import com.labelhub.modules.annotation.vo.AnnotationInitVO;
import com.labelhub.modules.annotation.vo.AnnotationSuggestionVO;
import com.labelhub.modules.annotation.vo.ItemDetailVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 标注工作台服务。
 */
public interface AnnotationService {

    /**
     * 初始化标注工作台。
     */
    AnnotationInitVO initWorkspace(Long taskId);

    /**
     * 查询单题详情。
     */
    ItemDetailVO getItemDetail(Long taskItemId);

    /**
     * 创建 AI 辅助标注建议。
     */
    AnnotationSuggestionVO createAiSuggestion(Long taskItemId);

    /**
     * 查询 AI 辅助标注建议。
     */
    AnnotationSuggestionVO queryAiSuggestion(Long taskItemId, String suggestionId);

    /**
     * 保存草稿。
     */
    void saveDraft(Long taskItemId, AnnotationSaveDTO dto);

    /**
     * 保存草稿，并处理图片/文件上传物料。
     */
    void saveDraft(Long taskItemId, AnnotationSaveDTO dto, Map<String, MultipartFile> componentFiles);

    /**
     * 提交标注。
     */
    void submit(Long taskItemId, AnnotationSaveDTO dto);

    /**
     * 提交标注，并处理图片/文件上传物料。
     */
    void submit(Long taskItemId, AnnotationSaveDTO dto, Map<String, MultipartFile> componentFiles);
}
