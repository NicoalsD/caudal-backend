package co.caudal.domain.session;

/** A token that was exchanged for its successor. Presenting it again means the secret leaked. */
final class RotatedRefreshTokenState implements RefreshTokenState {

  static final RotatedRefreshTokenState INSTANCE = new RotatedRefreshTokenState();

  private RotatedRefreshTokenState() {}

  @Override
  public String code() {
    return "ROTATED";
  }

  @Override
  public void ensureRotatable() {
    throw new RefreshTokenReuseException();
  }

  @Override
  public RefreshTokenState rotate() {
    throw new RefreshTokenReuseException();
  }

  @Override
  public RefreshTokenState revoke(RevocationReason reason) {
    return this;
  }
}
