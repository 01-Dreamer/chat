package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`group_leave_record`")
public class GroupLeaveRecord extends BaseEntity {

    private Long groupId;
    private Long userId;
    private Long operatorId;
}
