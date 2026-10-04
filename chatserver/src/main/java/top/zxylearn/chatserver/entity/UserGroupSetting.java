package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user_group_setting`")
public class UserGroupSetting extends BaseEntity {

    private Long userId;
    private Long groupId;
    private String remark;
}
