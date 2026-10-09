package co.caudal.domain.session;

/** A token closed for a reason other than rotation. It never becomes usable again. */
final class RevokedRefreshTokenState implements RefreshTokenState {

  private final RevocationReason reason;

  RevokedRefreshTokenState(RevocationReason reason) {
    this.reason = reason;
  }

  @Override
  public String code() {
    return "REVOKED";
  }

  RevocationReason reason() {
    return reason;
  }

  @Override
  public void ensureRotatable() {
    throw new SessionRevokedException();
  }

  @Override
  public RefreshTokenState rotate() {
    throw new SessionRevokedException();
  }

  @Override
  public RefreshTokenState revoke(RevocationReason newReason) {
    return this;
  }
}
