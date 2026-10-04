package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`group`")
public class Group extends BaseEntity {

    private String name;
    private String avatarUrl;
    private Long ownerId;
    private Integer status;
}
