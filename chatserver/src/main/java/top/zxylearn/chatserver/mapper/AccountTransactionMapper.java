package top.zxylearn.chatserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.zxylearn.chatserver.entity.AccountTransaction;

public interface AccountTransactionMapper extends BaseMapper<AccountTransaction> {
    @Select("SELECT * FROM account_transaction WHERE from_user_id = #{userId} AND client_transaction_id = #{clientId} LIMIT 1")
    AccountTransaction selectByClientId(@Param("userId") Long userId, @Param("clientId") String clientId);
}
