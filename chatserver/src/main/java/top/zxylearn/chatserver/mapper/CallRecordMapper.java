package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.CallRecord;

public interface CallRecordMapper extends BaseMapper<CallRecord> {
    @Select("SELECT * FROM call_record WHERE id = #{id} FOR UPDATE")
    CallRecord selectByIdForUpdate(@Param("id") Long id);
}
