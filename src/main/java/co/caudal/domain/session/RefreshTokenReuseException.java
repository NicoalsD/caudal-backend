package co.caudal.domain.session;

import java.io.Serial;

/**
 * A refresh token that was already rotated was presented again. It means the secret was copied: the
 * caller must revoke the whole family and record {@code TOKEN_REUSE_DETECTED}.
 */
public class RefreshTokenReuseException extends SessionRevokedException {

  @Serial private static final long serialVersionUID = 1L;
}
