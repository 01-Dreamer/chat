package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user`")
public class User extends BaseEntity {

    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private Integer status;
}
