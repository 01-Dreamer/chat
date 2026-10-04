package top.zxylearn.chatserver.dto.call;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateCallStatusRequest(
        @NotBlank @Pattern(regexp = "accept|complete|reject|missed|cancel") String action) {
}
