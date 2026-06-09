package com.finpulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import java.time.Duration;


@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ANALYTICS_TOP_AMOUNT = "analytics-top-amount";
    public static final String ANALYTICS_TOP_TXN = "analytics-top-txn";
    public static final String ANALYTICS_STATUS = "analytics-status";
    public static final String FRAUD_HIGH_COUNT = "fraud-high-count";
    public static final String FRAUD_DUPLICATES = "fraud-duplicates";
    public static final String FRAUD_HIGH_RECEIVING = "fraud-high-receiving";
    public static final String FRAUD_HIGH_AMOUNT = "fraud-high-amount";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory){
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisSerializationContext.SerializationPair<Object> serializerPair =
                RedisSerializationContext
                        .SerializationPair
                        .fromSerializer(serializer);

        RedisCacheConfiguration configuration=
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .disableCachingNullValues()
                        .serializeValuesWith(serializerPair);

        return RedisCacheManager.builder(factory)
                .cacheDefaults(configuration)
                .build();

    }
}
