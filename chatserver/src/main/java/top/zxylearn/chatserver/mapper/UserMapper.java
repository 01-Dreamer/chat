package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.User;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    @Select({
            "<script>",
            "SELECT id FROM user WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "ORDER BY id FOR UPDATE",
            "</script>"
    })
    List<Long> lockByIds(@Param("ids") List<Long> ids);
}
