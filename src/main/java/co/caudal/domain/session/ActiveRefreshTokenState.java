package co.caudal.domain.session;

/** A usable token. */
final class ActiveRefreshTokenState implements RefreshTokenState {

  static final ActiveRefreshTokenState INSTANCE = new ActiveRefreshTokenState();

  private ActiveRefreshTokenState() {}

  @Override
  public String code() {
    return "ACTIVE";
  }

  @Override
  public void ensureRotatable() {
    // An active token can always be exchanged; expiry is checked by the token itself.
  }

  @Override
  public RefreshTokenState rotate() {
    return RotatedRefreshTokenState.INSTANCE;
  }

  @Override
  public RefreshTokenState revoke(RevocationReason reason) {
    return reason == RevocationReason.ROTATED
        ? RotatedRefreshTokenState.INSTANCE
        : new RevokedRefreshTokenState(reason);
  }
}
