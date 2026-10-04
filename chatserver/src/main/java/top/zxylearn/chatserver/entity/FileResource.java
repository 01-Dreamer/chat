package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`file_resource`")
public class FileResource extends BaseEntity {

    private Long uploaderId;
    private Integer resourceType;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String fileHash;
    private String fileUrl;
    private Integer duration;
}
