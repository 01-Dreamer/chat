package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`red_packet_receive`")
public class RedPacketReceive extends BaseCreatedEntity {

    private Long redPacketId;
    private Long userId;
    private BigDecimal amount;
}
