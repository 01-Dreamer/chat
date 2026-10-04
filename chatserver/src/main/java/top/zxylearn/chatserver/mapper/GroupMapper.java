package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.Group;

public interface GroupMapper extends BaseMapper<Group> {

    @Select("SELECT * FROM `group` WHERE id = #{id} FOR UPDATE")
    Group selectByIdForUpdate(@Param("id") Long id);
}
