package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.CallRecord;

import java.time.LocalDateTime;

public interface CallRecordMapper extends BaseMapper<CallRecord> {
    @Select("SELECT * FROM call_record WHERE id = #{id} FOR UPDATE")
    CallRecord selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM call_record
            WHERE callee_id = #{calleeId}
              AND status = 0
              AND start_time IS NULL
              AND end_time IS NULL
              AND created_time >= #{notBefore}
            ORDER BY created_time DESC
            LIMIT 1
            """)
    CallRecord selectPendingIncoming(
            @Param("calleeId") Long calleeId,
            @Param("notBefore") LocalDateTime notBefore);
}
