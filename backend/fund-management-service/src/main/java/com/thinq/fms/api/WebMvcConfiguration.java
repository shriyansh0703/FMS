package com.thinq.fms.api;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the rate limit across the whole API surface (Stage 11, HIGH-1). */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final PerAccountRateLimit limits;
    private final ObjectMapper json;

    public WebMvcConfiguration(PerAccountRateLimit limits, ObjectMapper json) {
        this.limits = limits;
        this.json = json;
    }

    /**
     * The shipped budgets.
     *
     * <p>Overridden with {@code @Primary} rather than a condition: {@code @ConditionalOnMissingBean}
     * on a scanned {@code @Configuration} is evaluated in bean-definition order and would not see a
     * replacement declared elsewhere — the ordering trap the Stage 11 review recorded.
     */
    @Bean
    public static PerAccountRateLimit perAccountRateLimit() {
        return new PerAccountRateLimit();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Everything under /api, including paths that do not exist yet.
        registry.addInterceptor(new RateLimitInterceptor(this.limits, this.json))
                .addPathPatterns("/api/**");
    }
}
