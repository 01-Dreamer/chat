package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`account_transaction`")
public class AccountTransaction extends BaseEntity {
    private String clientTransactionId;
    private Long fromUserId;
    private Long toUserId;
    private Integer transactionType;
    private BigDecimal amount;
    private Long referenceId;
    private Integer status;
}
