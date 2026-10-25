package com.printguard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise caching layer supporting Spring Data Redis with automatic,
 * zero-crash in-memory ConcurrentMapCacheManager fallback for local development.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    @Primary
    public CacheManager cacheManager(@Autowired(required = false) RedisConnectionFactory connectionFactory) {
        if (connectionFactory != null) {
            try {
                // Probe connection eagerly to verify Redis is actually reachable
                connectionFactory.getConnection().ping();

                log.info("Redis cache connection verified on {}:{}. Initializing RedisCacheManager.", redisHost, redisPort);

                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .disableCachingNullValues()
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

                Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
                cacheConfigs.put("dashboardStats", defaultConfig.entryTtl(Duration.ofSeconds(30)));
                cacheConfigs.put("modelMetadata", defaultConfig.entryTtl(Duration.ofMinutes(5)));
                cacheConfigs.put("userSettings", defaultConfig.entryTtl(Duration.ofMinutes(10)));

                return RedisCacheManager.builder(connectionFactory)
                        .cacheDefaults(defaultConfig)
                        .withInitialCacheConfigurations(cacheConfigs)
                        .build();

            } catch (Exception e) {
                log.warn("Redis is not accessible on {}:{} ({}). Falling back gracefully to in-memory ConcurrentMapCacheManager.",
                        redisHost, redisPort, e.getMessage());
            }
        } else {
            log.info("RedisConnectionFactory not present. Using in-memory ConcurrentMapCacheManager.");
        }

        ConcurrentMapCacheManager inMemoryManager = new ConcurrentMapCacheManager("dashboardStats", "modelMetadata", "userSettings");
        return inMemoryManager;
    }
}
