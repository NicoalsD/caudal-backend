package co.caudal.infrastructure.security;

import co.caudal.shared.FieldLimits;
import co.caudal.shared.error.BodyTooLargeException;
import co.caudal.shared.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the size of request bodies (docs/API.md, section 1.8): 64 KiB by default, 256 KiB for
 * device telemetry and 1 MiB for the imports of simulated history. A body that declares a larger
 * length is refused before it is read; a body without a declared length is cut when it goes over,
 * and the API answers {@code 413 PAYLOAD_TOO_LARGE} either way. It is created inside the security
 * configuration and is not a bean.
 */
final class RequestBodySizeFilter extends OncePerRequestFilter {

  private static final String IMPORTS = "/api/v1/imports/";
  private static final String TELEMETRY = "/api/v1/devices/telemetry";

  private final SecurityErrorWriter errors;

  RequestBodySizeFilter(SecurityErrorWriter errors) {
    this.errors = errors;
  }

  static long limitFor(String path) {
    if (path.startsWith(IMPORTS)) {
      return FieldLimits.IMPORT_BODY_MAX_BYTES;
    }
    if (path.equals(TELEMETRY)) {
      return FieldLimits.TELEMETRY_BODY_MAX_BYTES;
    }
    return FieldLimits.BODY_MAX_BYTES;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long limit = limitFor(request.getRequestURI());
    if (request.getContentLengthLong() > limit) {
      errors.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
      return;
    }
    chain.doFilter(new LimitedRequest(request, limit), response);
  }

  /** A request whose body stream stops at the limit. */
  private static final class LimitedRequest extends HttpServletRequestWrapper {

    private final long limit;

    LimitedRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return new LimitedStream(super.getInputStream(), limit);
    }
  }

  /** Counts the bytes read and fails past the limit. */
  private static final class LimitedStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long limit;
    private long read;

    LimitedStream(ServletInputStream delegate, long limit) {
      this.delegate = delegate;
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value >= 0) {
        count(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int n = delegate.read(buffer, offset, length);
      if (n > 0) {
        count(n);
      }
      return n;
    }

    private void count(int n) throws BodyTooLargeException {
      read += n;
      if (read > limit) {
        throw new BodyTooLargeException();
      }
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }
  }
}
