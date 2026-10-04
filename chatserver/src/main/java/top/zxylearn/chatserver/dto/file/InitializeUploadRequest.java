package top.zxylearn.chatserver.dto.file;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InitializeUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotNull @Min(1) Long fileSize,
        @NotBlank @Size(max = 128) String mimeType,
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "文件哈希必须是 SHA-256") String fileHash,
        @NotNull @Min(0) @Max(3) Integer resourceType,
        @Min(0) Integer duration) {
}
