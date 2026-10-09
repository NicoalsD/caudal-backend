package co.caudal.api.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the properties of the cookie and of the allowed origins. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CorsProperties.class, RefreshCookieProperties.class})
public class AuthWebConfig {}
