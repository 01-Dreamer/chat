package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.Message;

public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT * FROM `message` WHERE sender_id = #{senderId} AND client_message_id = #{clientMessageId} LIMIT 1")
    Message selectByClientMessageId(@Param("senderId") Long senderId, @Param("clientMessageId") String clientMessageId);

    @Select("SELECT * FROM `message` WHERE id = #{id} FOR UPDATE")
    Message selectByIdForUpdate(@Param("id") Long id);
}
