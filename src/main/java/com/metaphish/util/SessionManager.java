package com.metaphish.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SessionManager {
    private static final String CookieName = "METAPHISH_SESSION";
    private static final long SessionTimeoutMillis = 8L * 60 * 60 * 1000;
    private static final ConcurrentHashMap<String, Long> ActiveSessions = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CleanupScheduler = Executors.newSingleThreadScheduledExecutor(Thread -> {
        Thread DaemonThread = new Thread(Thread);
        DaemonThread.setDaemon(true);
        DaemonThread.setName("SessionCleanup");
        return DaemonThread;
    });

    static {
        CleanupScheduler.scheduleAtFixedRate(SessionManager::CleanExpired, 30, 30, TimeUnit.MINUTES);
    }

    private SessionManager() {}

    public static String CreateSession() {
        String Token = UUID.randomUUID().toString();
        ActiveSessions.put(Token, System.currentTimeMillis());
        return Token;
    }

    public static boolean IsValid(String Token) {
        if (Token == null || Token.isBlank()) return false;
        Long CreationTime = ActiveSessions.get(Token);
        if (CreationTime == null) return false;
        if (System.currentTimeMillis() - CreationTime > SessionTimeoutMillis) {
            ActiveSessions.remove(Token);
            return false;
        }
        return true;
    }

    public static void Invalidate(String Token) {
        if (Token != null) ActiveSessions.remove(Token);
    }

    public static String BuildSetCookieHeader(String Token) {
        return CookieName + "=" + Token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + (8 * 3600);
    }

    public static String BuildClearCookieHeader() {
        return CookieName + "=; Path=/; HttpOnly; Max-Age=0";
    }

    public static String ExtractFromCookieHeader(String CookieHeader) {
        if (CookieHeader == null || CookieHeader.isBlank()) return null;
        for (String Part : CookieHeader.split(";")) {
            String Trimmed = Part.trim();
            if (Trimmed.startsWith(CookieName + "=")) {
                return Trimmed.substring(CookieName.length() + 1).trim();
            }
        }
        return null;
    }

    private static void CleanExpired() {
        long Now = System.currentTimeMillis();
        ActiveSessions.entrySet().removeIf(Entry -> Now - Entry.getValue() > SessionTimeoutMillis);
    }
}
