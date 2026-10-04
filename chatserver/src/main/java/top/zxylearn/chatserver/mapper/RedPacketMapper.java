package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.RedPacket;

public interface RedPacketMapper extends BaseMapper<RedPacket> {

    @Select("SELECT * FROM red_packet WHERE id = #{id} FOR UPDATE")
    RedPacket selectByIdForUpdate(@Param("id") Long id);
}
