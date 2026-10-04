package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`friend`")
public class Friend extends BaseEntity {

    private Long userId;
    private Long friendId;
    private String remark;
}
