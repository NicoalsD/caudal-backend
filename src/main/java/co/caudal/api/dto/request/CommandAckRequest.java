package co.caudal.api.dto.request;

/**
 * The result of a valve command as the device sends it (docs/API.md, section 16.3). The body is
 * checked by the adapter of the protocol, after the signature; this record documents it in Swagger.
 *
 * @param result ACKED or FAILED
 * @param detail what the device says about it, up to 200 characters, or null
 */
public record CommandAckRequest(String result, String detail) {}
