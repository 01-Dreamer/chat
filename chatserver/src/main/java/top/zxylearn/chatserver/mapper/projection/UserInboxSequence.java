package top.zxylearn.chatserver.mapper.projection;

import lombok.Data;

@Data
public class UserInboxSequence {

    private Long userId;
    private Long sequence;
}
