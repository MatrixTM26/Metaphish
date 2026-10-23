package com.metaphish.service;

import com.metaphish.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;

public final class IpGeolocation {
    private final HttpClient Client;
    private final int TimeoutSeconds;

    public IpGeolocation(int TimeoutSeconds) {
        this.TimeoutSeconds = TimeoutSeconds;
        this.Client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TimeoutSeconds))
                .build();
    }

    public LinkedHashMap<String, String> Lookup(String IpAddress) {
        LinkedHashMap<String, String> Result = new LinkedHashMap<>();

        if (IpAddress == null || IpAddress.isBlank()
                || IpAddress.equals("127.0.0.1")
                || IpAddress.equals("0:0:0:0:0:0:0:1")
                || IpAddress.startsWith("192.168.")
                || IpAddress.startsWith("10.")
                || IpAddress.startsWith("172.")) {
            Result.put("IpAddress", IpAddress != null ? IpAddress : "Unknown");
            Result.put("IpCountry", "Localhost");
            Result.put("IpCity", "Local");
            Result.put("IpRegion", "Local");
            Result.put("IpTimezone", "Unknown");
            Result.put("IpIsp", "Local Network");
            return Result;
        }

        try {
            HttpRequest Request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + IpAddress + "?fields=status,country,regionName,city,timezone,isp,query"))
                    .timeout(Duration.ofSeconds(TimeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> Response = Client.send(Request, HttpResponse.BodyHandlers.ofString());
            LinkedHashMap<String, String> Parsed = JsonUtil.ParseObject(Response.body());

            Result.put("IpAddress", Parsed.getOrDefault("query", IpAddress));
            Result.put("IpCountry", Parsed.getOrDefault("country", "Unknown"));
            Result.put("IpCity", Parsed.getOrDefault("city", "Unknown"));
            Result.put("IpRegion", Parsed.getOrDefault("regionName", "Unknown"));
            Result.put("IpTimezone", Parsed.getOrDefault("timezone", "Unknown"));
            Result.put("IpIsp", Parsed.getOrDefault("isp", "Unknown"));
        } catch (Exception Ignored) {
            Result.put("IpAddress", IpAddress);
            Result.put("IpCountry", "Lookup Failed");
            Result.put("IpCity", "Unknown");
            Result.put("IpRegion", "Unknown");
            Result.put("IpTimezone", "Unknown");
            Result.put("IpIsp", "Unknown");
        }

        return Result;
    }
}
