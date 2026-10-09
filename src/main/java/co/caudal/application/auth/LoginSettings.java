package co.caudal.application.auth;

import java.time.Duration;
import java.util.function.Function;

/**
 * Settings of the login use case that come from the environment.
 *
 * @param window time window of the consecutive failures of an account
 * @param ipMaxAttempts failed attempts from one IP that block it
 * @param ipWindow time window of the IP limit
 * @param refreshTtl lifetime of the refresh token for a role
 */
public record LoginSettings(
    Duration window, int ipMaxAttempts, Duration ipWindow, Function<String, Duration> refreshTtl) {}
