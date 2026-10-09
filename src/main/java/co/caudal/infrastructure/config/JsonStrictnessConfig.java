package co.caudal.infrastructure.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * No type coercion in request bodies (docs/API.md, section 1.8): a number, a boolean or a decimal
 * is never accepted where a text is expected, so {@code "password": 123456789012} is a validation
 * error instead of the text {@code "123456789012"}.
 */
@Configuration(proxyBeanMethods = false)
public class JsonStrictnessConfig {

  @Bean
  JsonMapperBuilderCustomizer noTextCoercionCustomizer() {
    return builder ->
        builder.withCoercionConfig(
            LogicalType.Textual,
            config ->
                config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
  }
}
