package com.labelhub.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.task.entity.TaskProgress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskProgressMapper extends BaseMapper<TaskProgress> {

    /**
     * 初始化或重建任务进度统计。
     */
    @Insert("""
            INSERT INTO task_progress (
                task_id,
                total_count,
                unassigned_count,
                claimed_count,
                submitted_count,
                rejected_count,
                reviewed_count
            ) VALUES (
                #{progress.taskId},
                #{progress.totalCount},
                #{progress.unassignedCount},
                #{progress.claimedCount},
                #{progress.submittedCount},
                #{progress.rejectedCount},
                #{progress.reviewedCount}
            )
            ON DUPLICATE KEY UPDATE
                total_count = VALUES(total_count),
                unassigned_count = VALUES(unassigned_count),
                claimed_count = VALUES(claimed_count),
                submitted_count = VALUES(submitted_count),
                rejected_count = VALUES(rejected_count),
                reviewed_count = VALUES(reviewed_count),
                update_time = NOW()
            """)
    int upsert(@Param("progress") TaskProgress progress);

    /**
     * 按状态流转增量更新任务进度统计。
     */
    @Update("""
            UPDATE task_progress
            SET unassigned_count = GREATEST(unassigned_count + #{unassignedDelta}, 0),
                claimed_count = GREATEST(claimed_count + #{claimedDelta}, 0),
                submitted_count = GREATEST(submitted_count + #{submittedDelta}, 0),
                rejected_count = GREATEST(rejected_count + #{rejectedDelta}, 0),
                reviewed_count = GREATEST(reviewed_count + #{reviewedDelta}, 0),
                update_time = NOW()
            WHERE task_id = #{taskId}
            """)
    int updateCounters(@Param("taskId") Long taskId,
                       @Param("unassignedDelta") long unassignedDelta,
                       @Param("claimedDelta") long claimedDelta,
                       @Param("submittedDelta") long submittedDelta,
                       @Param("rejectedDelta") long rejectedDelta,
                       @Param("reviewedDelta") long reviewedDelta);
}
