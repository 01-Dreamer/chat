package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`red_packet`")
public class RedPacket extends BaseEntity {

    private Long senderId;
    private Integer chatType;
    private Long targetId;
    private Integer packetType;
    private BigDecimal totalAmount;
    private Integer totalCount;
    private BigDecimal remainAmount;
    private Integer remainCount;
    private String message;
    private Integer status;
    private LocalDateTime expireTime;
}
