package vn.phongkham.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRateLimitConfiguration {
    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> {
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            return reactor.core.publisher.Mono.just(remoteAddress == null || remoteAddress.getAddress() == null
                    ? "unknown" : remoteAddress.getAddress().getHostAddress());
        };
    }
}
