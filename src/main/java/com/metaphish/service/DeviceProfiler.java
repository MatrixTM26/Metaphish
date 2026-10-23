package com.metaphish.service;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeviceProfiler {
    private static final Pattern AndroidVersion   = Pattern.compile("Android ([\\d.]+)");
    private static final Pattern IosVersion       = Pattern.compile("OS ([\\d_]+) like Mac OS X");
    private static final Pattern WindowsVersion   = Pattern.compile("Windows NT ([\\d.]+)");
    private static final Pattern MacVersion       = Pattern.compile("Mac OS X ([\\d_]+)");
    private static final Pattern SamsungVersion   = Pattern.compile("SamsungBrowser/([\\d.]+)");
    private static final Pattern EdgeVersion      = Pattern.compile("Edg(?:e)?/([\\d.]+)");
    private static final Pattern OperaVersion     = Pattern.compile("OPR/([\\d.]+)");
    private static final Pattern FirefoxVersion   = Pattern.compile("Firefox/([\\d.]+)");
    private static final Pattern ChromeVersion    = Pattern.compile("Chrome/([\\d.]+)");
    private static final Pattern SafariVersion    = Pattern.compile("Version/([\\d.]+).*Safari");
    private static final Pattern DeviceModel      = Pattern.compile("; ([A-Za-z0-9 _\\-]+) Build/");

    private DeviceProfiler() {}

    public static LinkedHashMap<String, String> Profile(String UserAgent) {
        LinkedHashMap<String, String> Result = new LinkedHashMap<>();

        if (UserAgent == null || UserAgent.isBlank()) {
            Result.put("DeviceType", "Unknown");
            Result.put("DeviceBrand", "Unknown");
            Result.put("DeviceModel", "Unknown");
            Result.put("OperatingSystem", "Unknown");
            Result.put("OperatingSystemVersion", "Unknown");
            Result.put("Browser", "Unknown");
            Result.put("BrowserVersion", "Unknown");
            Result.put("UserAgentRaw", "");
            return Result;
        }

        String LowerAgent = UserAgent.toLowerCase();

        String DeviceType;
        String DeviceBrand;
        String DeviceModelValue = "Unknown";
        String OperatingSystem;
        String OperatingSystemVersion = "Unknown";
        String Browser;
        String BrowserVersion = "Unknown";

        if (LowerAgent.contains("ipad")) {
            DeviceType = "Tablet";
            DeviceBrand = "Apple";
            OperatingSystem = "iPadOS";
        } else if (LowerAgent.contains("iphone")) {
            DeviceType = "Mobile";
            DeviceBrand = "Apple";
            OperatingSystem = "iOS";
        } else if (LowerAgent.contains("android") && (LowerAgent.contains("mobile") || LowerAgent.contains("phone"))) {
            DeviceType = "Mobile";
            DeviceBrand = DetectAndroidBrand(UserAgent);
            OperatingSystem = "Android";
        } else if (LowerAgent.contains("android")) {
            DeviceType = "Tablet";
            DeviceBrand = DetectAndroidBrand(UserAgent);
            OperatingSystem = "Android";
        } else if (LowerAgent.contains("windows")) {
            DeviceType = "Desktop";
            DeviceBrand = "Microsoft";
            OperatingSystem = "Windows";
        } else if (LowerAgent.contains("macintosh") || LowerAgent.contains("mac os x")) {
            DeviceType = "Desktop";
            DeviceBrand = "Apple";
            OperatingSystem = "macOS";
        } else if (LowerAgent.contains("linux")) {
            DeviceType = "Desktop";
            DeviceBrand = "Generic_Linux";
            OperatingSystem = "Linux";
        } else {
            DeviceType = "Unknown";
            DeviceBrand = "Unknown";
            OperatingSystem = "Unknown";
        }

        Matcher AndroidMatch = AndroidVersion.matcher(UserAgent);
        if (AndroidMatch.find()) OperatingSystemVersion = AndroidMatch.group(1);

        Matcher IosMatch = IosVersion.matcher(UserAgent);
        if (IosMatch.find()) OperatingSystemVersion = IosMatch.group(1).replace("_", ".");

        Matcher WindowsMatch = WindowsVersion.matcher(UserAgent);
        if (WindowsMatch.find()) OperatingSystemVersion = WindowsMatch.group(1);

        Matcher MacMatch = MacVersion.matcher(UserAgent);
        if (MacMatch.find()) OperatingSystemVersion = MacMatch.group(1).replace("_", ".");

        Matcher ModelMatch = DeviceModel.matcher(UserAgent);
        if (ModelMatch.find()) DeviceModelValue = ModelMatch.group(1).trim();

        Matcher SamsungMatch = SamsungVersion.matcher(UserAgent);
        Matcher EdgeMatch = EdgeVersion.matcher(UserAgent);
        Matcher OperaMatch = OperaVersion.matcher(UserAgent);
        Matcher FirefoxMatch = FirefoxVersion.matcher(UserAgent);
        Matcher ChromeMatch = ChromeVersion.matcher(UserAgent);
        Matcher SafariMatch = SafariVersion.matcher(UserAgent);

        if (SamsungMatch.find()) {
            Browser = "Samsung Internet";
            BrowserVersion = SamsungMatch.group(1);
        } else if (EdgeMatch.find()) {
            Browser = "Microsoft Edge";
            BrowserVersion = EdgeMatch.group(1);
        } else if (OperaMatch.find()) {
            Browser = "Opera";
            BrowserVersion = OperaMatch.group(1);
        } else if (FirefoxMatch.find()) {
            Browser = "Firefox";
            BrowserVersion = FirefoxMatch.group(1);
        } else if (ChromeMatch.find()) {
            Browser = LowerAgent.contains("mobile") ? "Chrome Mobile" : "Chrome";
            BrowserVersion = ChromeMatch.group(1);
        } else if (SafariMatch.find()) {
            Browser = LowerAgent.contains("mobile") ? "Mobile Safari" : "Safari";
            BrowserVersion = SafariMatch.group(1);
        } else {
            Browser = "Unknown";
        }

        Result.put("DeviceType", DeviceType);
        Result.put("DeviceBrand", DeviceBrand);
        Result.put("DeviceModel", DeviceModelValue);
        Result.put("OperatingSystem", OperatingSystem);
        Result.put("OperatingSystemVersion", OperatingSystemVersion);
        Result.put("Browser", Browser);
        Result.put("BrowserVersion", BrowserVersion);
        Result.put("UserAgentRaw", UserAgent);

        return Result;
    }

    private static String DetectAndroidBrand(String UserAgent) {
        String Lower = UserAgent.toLowerCase();
        if (Lower.contains("samsung")) return "Samsung";
        if (Lower.contains("xiaomi") || Lower.contains("miui") || Lower.contains("redmi")) return "Xiaomi";
        if (Lower.contains("huawei") || Lower.contains("honor")) return "Huawei";
        if (Lower.contains("oppo")) return "Oppo";
        if (Lower.contains("vivo")) return "Vivo";
        if (Lower.contains("realme")) return "Realme";
        if (Lower.contains("oneplus")) return "OnePlus";
        if (Lower.contains("motorola") || Lower.contains("moto")) return "Motorola";
        if (Lower.contains("nokia")) return "Nokia";
        if (Lower.contains("lg")) return "LG";
        if (Lower.contains("sony") || Lower.contains("xperia")) return "Sony";
        if (Lower.contains("asus")) return "Asus";
        if (Lower.contains("pixel") || Lower.contains("nexus")) return "Google";
        return "Generic_Android";
    }
}
