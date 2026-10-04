package top.zxylearn.chatserver.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public abstract class BaseEntity extends BaseIdEntity {

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
