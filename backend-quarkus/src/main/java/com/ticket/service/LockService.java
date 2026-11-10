package com.ticket.service;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

@ApplicationScoped
public class LockService {

    private static final Logger LOG = Logger.getLogger(LockService.class);
    public static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(10); // 10 minutes TTL

    private final ReactiveValueCommands<String, String> valueCommands;

    @Inject
    public LockService(ReactiveRedisDataSource redisDataSource) {
        this.valueCommands = redisDataSource.value(String.class);
    }

    // Default constructor for testing/mocking
    public LockService() {
        this.valueCommands = null;
    }

    protected String buildLockKey(String ticketId) {
        return "ticket:" + ticketId + ":lock";
    }

    /**
     * Atomically acquires a distributed lock on the ticket via Redis SETNX with TTL.
     * Returns Uni<Boolean>: true if lock acquired, false if already locked by another user.
     */
    public Uni<Boolean> acquireLock(String ticketId, String userId, Duration duration) {
        String key = buildLockKey(ticketId);
        Duration ttl = duration != null ? duration : DEFAULT_HOLD_DURATION;

        if (valueCommands == null) {
            return Uni.createFrom().item(true);
        }

        SetArgs args = new SetArgs().nx().ex(ttl);
        return valueCommands.set(key, userId, args)
            .map(res -> {
                boolean acquired = res != null;
                LOG.infof("Lock attempt for key=%s by user=%s -> acquired=%s (ttl=%ds)",
                    key, userId, acquired, ttl.toSeconds());
                return acquired;
            })
            .onFailure().recoverWithItem(err -> {
                LOG.errorf("Error acquiring lock in Redis for key=%s: %s", key, err.getMessage());
                return false;
            });
    }

    /**
     * Releases lock if the calling user is the lock owner.
     */
    public Uni<Boolean> releaseLock(String ticketId, String userId) {
        String key = buildLockKey(ticketId);
        if (valueCommands == null) {
            return Uni.createFrom().item(true);
        }

        return valueCommands.get(key)
            .flatMap(owner -> {
                if (owner != null && (owner.equals(userId) || "SYSTEM".equals(userId))) {
                    return valueCommands.getdel(key).map(v -> true);
                }
                return Uni.createFrom().item(false);
            })
            .onFailure().recoverWithItem(false);
    }

    /**
     * Gets current lock holder if any.
     */
    public Uni<String> getLockHolder(String ticketId) {
        String key = buildLockKey(ticketId);
        if (valueCommands == null) {
            return Uni.createFrom().nullItem();
        }
        return valueCommands.get(key);
    }
}
