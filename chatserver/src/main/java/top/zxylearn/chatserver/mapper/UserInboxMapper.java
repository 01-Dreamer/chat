package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.UserInbox;

public interface UserInboxMapper extends BaseMapper<UserInbox> {

    @Select("SELECT COALESCE(MAX(sequence), 0) FROM user_inbox WHERE user_id = #{userId}")
    Long selectMaxSequence(@Param("userId") Long userId);
}
