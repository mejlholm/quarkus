package io.quarkus.redis.client.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.PreDestroy;

import io.quarkus.redis.client.RedisClient;
import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;

class RedisAPIProducer {
    private static Map<String, RedisAPIContainer> REDIS_APIS = new ConcurrentHashMap<>();

    private final Vertx vertx;
    private final RedisConfig redisRuntimeConfig;

    public RedisAPIProducer(RedisConfig redisConfig, Vertx vertx) {
        this.redisRuntimeConfig = redisConfig;
        this.vertx = vertx;
    }

    public RedisAPIContainer getRedisAPIContainer(String name) {
        return REDIS_APIS.computeIfAbsent(name, new Function<String, RedisAPIContainer>() {
            @Override
            public RedisAPIContainer apply(String s) {
                long timeout = 10;
                RedisConfig.RedisConfiguration redisConfig = RedisClientUtil.getConfiguration(redisRuntimeConfig, name);
                if (redisConfig.timeout.isPresent()) {
                    timeout = redisConfig.timeout.get().getSeconds();
                }
                RedisOptions options = RedisClientUtil.buildOptions(redisConfig, name);
                Redis redis = Redis.createClient(vertx, options);
                RedisAPI redisAPI = RedisAPI.api(redis);
                MutinyRedis mutinyRedis = new MutinyRedis(redis);
                MutinyRedisAPI mutinyRedisAPI = new MutinyRedisAPI(redisAPI);
                RedisClient redisClient = new RedisClientImpl(mutinyRedisAPI, timeout);
                ReactiveRedisClient reactiveClient = new ReactiveRedisClientImpl(mutinyRedisAPI);
                return new RedisAPIContainer(redis, redisAPI, redisClient, reactiveClient, mutinyRedis, mutinyRedisAPI);
            }
        });
    }

    @PreDestroy
    public void close() {
        for (RedisAPIContainer container : REDIS_APIS.values()) {
            container.close();
        }
    }

}
