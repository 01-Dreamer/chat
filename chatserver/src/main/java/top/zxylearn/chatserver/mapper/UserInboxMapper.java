package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import top.zxylearn.chatserver.entity.UserInbox;
import top.zxylearn.chatserver.mapper.projection.UserInboxSequence;

import java.util.List;

public interface UserInboxMapper extends BaseMapper<UserInbox> {

    @Select({
            "<script>",
            "SELECT user_id AS userId, MAX(sequence) AS sequence FROM user_inbox WHERE user_id IN",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>#{userId}</foreach>",
            "GROUP BY user_id",
            "</script>"
    })
    List<UserInboxSequence> selectMaxSequences(@Param("userIds") List<Long> userIds);

    @Insert({
            "<script>",
            "INSERT INTO user_inbox (id, user_id, event_id, sequence, created_time, updated_time) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.id}, #{item.userId}, #{item.eventId}, #{item.sequence}, #{item.createdTime}, #{item.updatedTime})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("items") List<UserInbox> items);
}
