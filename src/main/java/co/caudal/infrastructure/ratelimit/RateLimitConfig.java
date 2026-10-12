package co.caudal.infrastructure.ratelimit;

import co.caudal.application.port.out.RateLimiterPort;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Rate limits over PostgreSQL. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfig {

  @Bean
  RateLimiterPort rateLimiter(DataSource dataSource) {
    return new Bucket4jRateLimiterAdapter(
        Bucket4jPostgreSQL.advisoryLockBasedBuilder(dataSource)
            .primaryKeyMapper(PrimaryKeyMapper.STRING)
            .table("iam.rate_limit_buckets")
            .idColumn("id")
            .stateColumn("state")
            .build());
  }
}
