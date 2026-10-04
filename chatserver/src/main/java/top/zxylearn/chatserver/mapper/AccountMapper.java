package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.Account;

public interface AccountMapper extends BaseMapper<Account> {

    @Select("SELECT * FROM account WHERE user_id = #{userId} FOR UPDATE")
    Account selectByUserIdForUpdate(@Param("userId") Long userId);
}
