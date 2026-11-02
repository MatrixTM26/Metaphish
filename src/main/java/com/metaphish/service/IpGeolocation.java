package com.metaphish.service;

import com.metaphish.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;

public final class IpGeolocation {
    private static final String ApiUrl = "http://ip-api.com/json/%s?fields=status,country,regionName,city,timezone,isp,org,as,mobile,proxy,hosting,query";

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
            Result.put("IpAddress",   IpAddress != null ? IpAddress : "Unknown");
            Result.put("Country",     "Localhost");
            Result.put("City",        "Local");
            Result.put("Region",      "Local");
            Result.put("IpTimezone",  "Unknown");
            Result.put("ISP",         "Local Network");
            Result.put("Organization","Local Network");
            Result.put("AsNumber",    "Unknown");
            Result.put("IsMobile",    "false");
            Result.put("IsProxy",     "false");
            Result.put("IsHosting",   "false");
            return Result;
        }

        try {
            HttpRequest Request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(ApiUrl, IpAddress)))
                    .timeout(Duration.ofSeconds(TimeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> Response = Client.send(Request, HttpResponse.BodyHandlers.ofString());
            LinkedHashMap<String, String> Parsed = JsonUtil.ParseObject(Response.body());

            Result.put("IpAddress",    Parsed.getOrDefault("query",      IpAddress));
            Result.put("Country",      Parsed.getOrDefault("country",    "Unknown"));
            Result.put("City",         Parsed.getOrDefault("city",       "Unknown"));
            Result.put("Region",       Parsed.getOrDefault("regionName", "Unknown"));
            Result.put("IpTimezone",   Parsed.getOrDefault("timezone",   "Unknown"));
            Result.put("ISP",          Parsed.getOrDefault("isp",        "Unknown"));
            Result.put("Organization", Parsed.getOrDefault("org",        "Unknown"));
            Result.put("AsNumber",     Parsed.getOrDefault("as",         "Unknown"));
            Result.put("IsMobile",     Parsed.getOrDefault("mobile",     "false"));
            Result.put("IsProxy",      Parsed.getOrDefault("proxy",      "false"));
            Result.put("IsHosting",    Parsed.getOrDefault("hosting",    "false"));
        } catch (Exception Ignored) {
            Result.put("IpAddress",    IpAddress);
            Result.put("Country",      "Lookup Failed");
            Result.put("City",         "Unknown");
            Result.put("Region",       "Unknown");
            Result.put("IpTimezone",   "Unknown");
            Result.put("ISP",          "Unknown");
            Result.put("Organization", "Unknown");
            Result.put("AsNumber",     "Unknown");
            Result.put("IsMobile",     "Unknown");
            Result.put("IsProxy",      "Unknown");
            Result.put("IsHosting",    "Unknown");
        }

        return Result;
    }
}
