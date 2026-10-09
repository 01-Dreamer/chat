package top.zxylearn.chatserver.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallManagementServiceTest {

    @Test
    void formatsCallDurationForPrompt() {
        assertEquals("00:00", CallManagementService.formatCallDuration(null));
        assertEquals("01:05", CallManagementService.formatCallDuration(65));
        assertEquals("01:02:03", CallManagementService.formatCallDuration(3_723));
    }
}
