package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.User;

public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT id FROM user WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
