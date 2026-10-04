package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.GroupMember;

public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    @Select("SELECT * FROM group_member WHERE group_id = #{groupId} AND user_id = #{userId} FOR UPDATE")
    GroupMember selectForUpdate(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
