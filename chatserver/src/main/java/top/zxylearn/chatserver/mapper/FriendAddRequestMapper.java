package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.FriendAddRequest;

public interface FriendAddRequestMapper extends BaseMapper<FriendAddRequest> {

    @Select("SELECT * FROM friend_add_request WHERE id = #{id} FOR UPDATE")
    FriendAddRequest selectByIdForUpdate(@Param("id") Long id);
}
