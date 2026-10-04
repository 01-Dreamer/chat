package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.GroupJoinRequest;

public interface GroupJoinRequestMapper extends BaseMapper<GroupJoinRequest> {

    @Select("SELECT * FROM group_join_request WHERE id = #{id} FOR UPDATE")
    GroupJoinRequest selectByIdForUpdate(@Param("id") Long id);
}
