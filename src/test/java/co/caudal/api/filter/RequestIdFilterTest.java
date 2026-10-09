package co.caudal.api.filter;

import static org.assertj.core.api.Assertions.assertThat;

import co.caudal.shared.FieldLimits;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void reusesValidClientRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "client-id-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-id-123");
  }

  @Test
  void replacesInvalidClientRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER, "bad id\r\ninjected: header");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(RequestIdFilter.HEADER))
        .matches(FieldLimits.REQUEST_ID_PATTERN)
        .doesNotContain("injected");
  }

  @Test
  void replacesTooLongClientRequestId() {
    String tooLong = "a".repeat(FieldLimits.REQUEST_ID_MAX + 1);

    assertThat(RequestIdFilter.resolve(tooLong)).isNotEqualTo(tooLong);
  }

  @Test
  void exposesIdInMdcDuringRequestAndClearsItAfter() throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        new MockHttpServletRequest(),
        response,
        (req, res) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));

    assertThat(seen.get()).isEqualTo(response.getHeader(RequestIdFilter.HEADER));
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }
}
