package top.zxylearn.chatserver.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public abstract class BaseCreatedEntity extends BaseIdEntity {

    private LocalDateTime createdTime;
}
