package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`account`")
public class Account extends BaseEntity {

    private Long userId;
    private BigDecimal balance;
    private String payPasswordHash;
    private Integer status;
}
