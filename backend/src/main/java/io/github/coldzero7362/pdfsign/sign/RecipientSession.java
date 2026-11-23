package io.github.coldzero7362.pdfsign.sign;

import java.io.Serializable;
import java.time.Instant;

/** OTP 확인을 마친 수신자. HTTP 세션에 저장한다. */
public record RecipientSession(Long recipientId, Long requestId, Instant verifiedAt) implements Serializable {

    public static final String ATTR = "pdfsign.recipient";
}
