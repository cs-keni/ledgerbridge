package com.ledgerbridge.risk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerbridge.risk.dto.RiskAlertResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseAlertService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        heartbeatScheduler.shutdown();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(-1L);
        emitters.add(emitter);
        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onTimeout(cleanup);
        emitter.onCompletion(cleanup);
        emitter.onError(ex -> cleanup.run());
        return emitter;
    }

    public void broadcast(RiskAlertResponse alert) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("alert")
                        .data(objectMapper.writeValueAsString(alert)));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    private void sendHeartbeat() {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
