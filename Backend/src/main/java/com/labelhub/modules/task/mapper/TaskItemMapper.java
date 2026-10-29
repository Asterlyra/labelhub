package com.labelhub.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.task.entity.TaskItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface TaskItemMapper extends BaseMapper<TaskItem> {

    /**
     * 锁定当前任务下最早的未领取数据项。
     * <p>
     * 这里使用普通 FOR UPDATE，不使用 SKIP LOCKED：
     * 1. 保持“先来先得”的顺序语义，后来的领取请求等待前一个事务完成；
     * 2. 避免低版本 MySQL 不支持 SKIP LOCKED 导致 Mapper 层 SQL 报错。
     */
    @Select({
            "<script>",
            "SELECT id FROM task_item",
            "WHERE task_id = #{taskId} AND status = 'UNASSIGNED'",
            "ORDER BY id ASC LIMIT #{claimCount}",
            "FOR UPDATE",
            "</script>"
    })
    List<Long> selectUnassignedForUpdate(@Param("taskId") Long taskId, @Param("claimCount") int claimCount);

    @Update({
            "<script>",
            "UPDATE task_item",
            "SET labeler_id = #{labelerId},",
            "    status = 'CLAIMED',",
            "    update_time = NOW()",
            "WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "AND status = 'UNASSIGNED'",
            "</script>"
    })
    int batchClaim(@Param("ids") List<Long> ids, @Param("labelerId") Long labelerId);

    /**
     * 批量插入任务数据项。
     */
    @Insert({
            "<script>",
            "INSERT INTO task_item (task_id, dataset_item_id, labeler_id, status) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.taskId}, #{item.datasetItemId}, #{item.labelerId}, #{item.status})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("items") List<TaskItem> items);

    /**
     * 按工作台展示状态统计当前标注员的任务数据项进度。
     */
    @Select({
            "SELECT",
            "    CASE",
            "        WHEN status = 'CLAIMED' THEN 'PENDING'",
            "        WHEN status = 'REVIEWING' THEN 'SUBMITTED'",
            "        ELSE status",
            "    END AS display_status,",
            "    COUNT(*) AS count",
            "FROM task_item",
            "WHERE task_id = #{taskId}",
            "  AND labeler_id = #{labelerId}",
            "GROUP BY",
            "    CASE",
            "        WHEN status = 'CLAIMED' THEN 'PENDING'",
            "        WHEN status = 'REVIEWING' THEN 'SUBMITTED'",
            "        ELSE status",
            "    END"
    })
    List<Map<String, Object>> selectLabelerDisplayStatusCounts(@Param("taskId") Long taskId,
                                                               @Param("labelerId") Long labelerId);

    /**
     * 批量统计当前标注员在多个任务下的领取总数和已标注数量。
     */
    @Select({
            "<script>",
            "SELECT",
            "    task_id,",
            "    COUNT(*) AS claimed_count,",
            "    SUM(CASE WHEN status IN ('SUBMITTED', 'REVIEWING', 'REVIEWED') THEN 1 ELSE 0 END) AS annotated_count,",
            "    SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count",
            "FROM task_item",
            "WHERE labeler_id = #{labelerId}",
            "  AND task_id IN",
            "<foreach collection='taskIds' item='taskId' open='(' separator=',' close=')'>",
            "    #{taskId}",
            "</foreach>",
            "GROUP BY task_id",
            "</script>"
    })
    List<Map<String, Object>> selectLabelerMarketplaceCounts(@Param("labelerId") Long labelerId,
                                                             @Param("taskIds") List<Long> taskIds);

    /**
     * 查询当前轮到指定审核员处理的任务 ID。
     */
    @Select("""
            SELECT DISTINCT ti.task_id
            FROM task_item ti
            INNER JOIN task_reviewer tr
                ON tr.task_id = ti.task_id
               AND tr.review_order = ti.current_review_order
            WHERE ti.status = 'REVIEWING'
              AND tr.reviewer_id = #{reviewerId}
            """)
    List<Long> selectCurrentReviewTaskIds(@Param("reviewerId") Long reviewerId);

    /**
     * 查询当前轮到指定审核员处理的任务项。
     */
    @Select("""
            SELECT ti.*
            FROM task_item ti
            INNER JOIN task_reviewer tr
                ON tr.task_id = ti.task_id
               AND tr.review_order = ti.current_review_order
            WHERE ti.task_id = #{taskId}
              AND ti.status = 'REVIEWING'
              AND tr.reviewer_id = #{reviewerId}
            ORDER BY ti.id ASC
            """)
    List<TaskItem> selectCurrentReviewItems(@Param("taskId") Long taskId,
                                            @Param("reviewerId") Long reviewerId);

    /**
     * 查询审核工作台导航所需的任务项字段。
     */
    @Select("""
            SELECT ti.id, ti.status
            FROM task_item ti
            INNER JOIN task_reviewer tr
                ON tr.task_id = ti.task_id
               AND tr.review_order = ti.current_review_order
            WHERE ti.task_id = #{taskId}
              AND ti.status = 'REVIEWING'
              AND tr.reviewer_id = #{reviewerId}
            ORDER BY ti.id ASC
            """)
    List<TaskItem> selectCurrentReviewNavigationItems(@Param("taskId") Long taskId,
                                                      @Param("reviewerId") Long reviewerId);
}
