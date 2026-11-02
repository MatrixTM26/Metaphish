package com.metaphish.service;

import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeviceProfiler {
    private static final Pattern AndroidVersion  = Pattern.compile("Android ([\\d.]+)");
    private static final Pattern IosVersion      = Pattern.compile("OS ([\\d_]+) like Mac OS X");
    private static final Pattern WindowsVersion  = Pattern.compile("Windows NT ([\\d.]+)");
    private static final Pattern MacVersion      = Pattern.compile("Mac OS X ([\\d_]+)");
    private static final Pattern ChromeOsVersion = Pattern.compile("CrOS [^\\s]+ ([\\d.]+)");
    private static final Pattern SamsungBrowser  = Pattern.compile("SamsungBrowser/([\\d.]+)");
    private static final Pattern EdgeBrowser     = Pattern.compile("Edg(?:e)?/([\\d.]+)");
    private static final Pattern OperaBrowser    = Pattern.compile("OPR/([\\d.]+)");
    private static final Pattern VivaldiBrowser  = Pattern.compile("Vivaldi/([\\d.]+)");
    private static final Pattern YandexBrowser   = Pattern.compile("YaBrowser/([\\d.]+)");
    private static final Pattern UcBrowser       = Pattern.compile("UCBrowser/([\\d.]+)");
    private static final Pattern DuckDuckBrowser = Pattern.compile("DuckDuckGo/([\\d.]+)");
    private static final Pattern FirefoxBrowser  = Pattern.compile("Firefox/([\\d.]+)");
    private static final Pattern ChromeBrowser   = Pattern.compile("Chrome/([\\d.]+)");
    private static final Pattern SafariBrowser   = Pattern.compile("Version/([\\d.]+).*Safari");
    private static final Pattern DeviceModelTag  = Pattern.compile("; ([A-Za-z0-9 _\\-]+) Build/");

    private DeviceProfiler() {}

    public static LinkedHashMap<String, String> Profile(String UserAgent) {
        LinkedHashMap<String, String> Result = new LinkedHashMap<>();

        if (UserAgent == null || UserAgent.isBlank()) {
            Result.put("DeviceType",   "Unknown");
            Result.put("DeviceBrand",  "Unknown");
            Result.put("DeviceModel",  "Unknown");
            Result.put("OsName",       "Unknown");
            Result.put("OsVersion",    "Unknown");
            Result.put("BrowserName",  "Unknown");
            Result.put("BrowserVersion", "Unknown");
            Result.put("UserAgentRaw", "");
            return Result;
        }

        String LowerAgent = UserAgent.toLowerCase();

        String DeviceType;
        String DeviceBrand;
        String DeviceModelValue  = "Unknown";
        String OsName;
        String OsVersion         = "Unknown";
        String BrowserName;
        String BrowserVersion    = "Unknown";

        if (LowerAgent.contains("cros")) {
            DeviceType  = "Chromebook";
            DeviceBrand = "Google";
            OsName      = "ChromeOS";
        } else if (LowerAgent.contains("ipad")) {
            DeviceType  = "Tablet";
            DeviceBrand = "Apple";
            OsName      = "iPadOS";
        } else if (LowerAgent.contains("iphone")) {
            DeviceType  = "Mobile";
            DeviceBrand = "Apple";
            OsName      = "iOS";
        } else if (LowerAgent.contains("android") && (LowerAgent.contains("mobile") || LowerAgent.contains("phone"))) {
            DeviceType  = "Mobile";
            DeviceBrand = DetectAndroidBrand(UserAgent);
            OsName      = "Android";
        } else if (LowerAgent.contains("android")) {
            DeviceType  = "Tablet";
            DeviceBrand = DetectAndroidBrand(UserAgent);
            OsName      = "Android";
        } else if (LowerAgent.contains("windows phone") || LowerAgent.contains("windows mobile")) {
            DeviceType  = "Mobile";
            DeviceBrand = "Microsoft";
            OsName      = "Windows Phone";
        } else if (LowerAgent.contains("windows")) {
            DeviceType  = "Desktop";
            DeviceBrand = "Microsoft";
            OsName      = "Windows";
        } else if (LowerAgent.contains("macintosh") || LowerAgent.contains("mac os x")) {
            DeviceType  = "Desktop";
            DeviceBrand = "Apple";
            OsName      = "macOS";
        } else if (LowerAgent.contains("linux")) {
            DeviceType  = "Desktop";
            DeviceBrand = DetectLinuxDistro(UserAgent);
            OsName      = "Linux";
        } else {
            DeviceType  = "Unknown";
            DeviceBrand = "Unknown";
            OsName      = "Unknown";
        }

        Matcher ChromeOsMatch = ChromeOsVersion.matcher(UserAgent);
        if (ChromeOsMatch.find()) OsVersion = ChromeOsMatch.group(1);

        Matcher AndroidMatch = AndroidVersion.matcher(UserAgent);
        if (AndroidMatch.find()) OsVersion = AndroidMatch.group(1);

        Matcher IosMatch = IosVersion.matcher(UserAgent);
        if (IosMatch.find()) OsVersion = IosMatch.group(1).replace("_", ".");

        Matcher WindowsMatch = WindowsVersion.matcher(UserAgent);
        if (WindowsMatch.find()) OsVersion = MapWindowsVersion(WindowsMatch.group(1));

        Matcher MacMatch = MacVersion.matcher(UserAgent);
        if (MacMatch.find()) OsVersion = MacMatch.group(1).replace("_", ".");

        Matcher ModelMatch = DeviceModelTag.matcher(UserAgent);
        if (ModelMatch.find()) DeviceModelValue = ModelMatch.group(1).trim();

        Matcher SamsungMatch  = SamsungBrowser.matcher(UserAgent);
        Matcher EdgeMatch     = EdgeBrowser.matcher(UserAgent);
        Matcher OperaMatch    = OperaBrowser.matcher(UserAgent);
        Matcher VivaldiMatch  = VivaldiBrowser.matcher(UserAgent);
        Matcher YandexMatch   = YandexBrowser.matcher(UserAgent);
        Matcher UcMatch       = UcBrowser.matcher(UserAgent);
        Matcher DuckDuckMatch = DuckDuckBrowser.matcher(UserAgent);
        Matcher FirefoxMatch  = FirefoxBrowser.matcher(UserAgent);
        Matcher ChromeMatch   = ChromeBrowser.matcher(UserAgent);
        Matcher SafariMatch   = SafariBrowser.matcher(UserAgent);

        if (SamsungMatch.find()) {
            BrowserName    = "Samsung Internet";
            BrowserVersion = SamsungMatch.group(1);
        } else if (EdgeMatch.find()) {
            BrowserName    = "Microsoft Edge";
            BrowserVersion = EdgeMatch.group(1);
        } else if (OperaMatch.find()) {
            BrowserName    = "Opera";
            BrowserVersion = OperaMatch.group(1);
        } else if (VivaldiMatch.find()) {
            BrowserName    = "Vivaldi";
            BrowserVersion = VivaldiMatch.group(1);
        } else if (YandexMatch.find()) {
            BrowserName    = "Yandex Browser";
            BrowserVersion = YandexMatch.group(1);
        } else if (UcMatch.find()) {
            BrowserName    = "UC Browser";
            BrowserVersion = UcMatch.group(1);
        } else if (DuckDuckMatch.find()) {
            BrowserName    = "DuckDuckGo";
            BrowserVersion = DuckDuckMatch.group(1);
        } else if (FirefoxMatch.find()) {
            BrowserName    = "Firefox";
            BrowserVersion = FirefoxMatch.group(1);
        } else if (ChromeMatch.find()) {
            BrowserName    = LowerAgent.contains("mobile") ? "Chrome Mobile" : "Chrome";
            BrowserVersion = ChromeMatch.group(1);
        } else if (SafariMatch.find()) {
            BrowserName    = LowerAgent.contains("mobile") ? "Mobile Safari" : "Safari";
            BrowserVersion = SafariMatch.group(1);
        } else {
            BrowserName = "Unknown";
        }

        Result.put("DeviceType",     DeviceType);
        Result.put("DeviceBrand",    DeviceBrand);
        Result.put("DeviceModel",    DeviceModelValue);
        Result.put("OsName",         OsName);
        Result.put("OsVersion",      OsVersion);
        Result.put("BrowserName",    BrowserName);
        Result.put("BrowserVersion", BrowserVersion);
        Result.put("UserAgentRaw",   UserAgent);

        return Result;
    }

    private static String DetectAndroidBrand(String UserAgent) {
        String Lower = UserAgent.toLowerCase();
        if (Lower.contains("samsung"))                              return "Samsung";
        if (Lower.contains("xiaomi") || Lower.contains("miui")
                || Lower.contains("redmi") || Lower.contains("poco")) return "Xiaomi";
        if (Lower.contains("huawei") || Lower.contains("honor"))   return "Huawei";
        if (Lower.contains("oppo"))                                 return "Oppo";
        if (Lower.contains("vivo"))                                 return "Vivo";
        if (Lower.contains("realme"))                               return "Realme";
        if (Lower.contains("oneplus"))                              return "OnePlus";
        if (Lower.contains("motorola") || Lower.contains("moto"))  return "Motorola";
        if (Lower.contains("nokia"))                                return "Nokia";
        if (Lower.contains("lg"))                                   return "LG";
        if (Lower.contains("sony") || Lower.contains("xperia"))    return "Sony";
        if (Lower.contains("asus"))                                 return "Asus";
        if (Lower.contains("pixel") || Lower.contains("nexus"))    return "Google";
        if (Lower.contains("infinix"))                              return "Infinix";
        if (Lower.contains("tecno"))                                return "Tecno";
        if (Lower.contains("itel"))                                 return "Itel";
        if (Lower.contains("lenovo"))                               return "Lenovo";
        if (Lower.contains("zte"))                                  return "ZTE";
        if (Lower.contains("wiko"))                                 return "Wiko";
        if (Lower.contains("meizu"))                                return "Meizu";
        if (Lower.contains("blackberry") || Lower.contains("bb10")) return "BlackBerry";
        if (Lower.contains("htc"))                                  return "HTC";
        if (Lower.contains("alcatel"))                              return "Alcatel";
        if (Lower.contains("micromax"))                             return "Micromax";
        if (Lower.contains("sharp"))                                return "Sharp";
        if (Lower.contains("fujitsu"))                              return "Fujitsu";
        if (Lower.contains("panasonic"))                            return "Panasonic";
        return "Generic Android";
    }

    private static String DetectLinuxDistro(String UserAgent) {
        String Lower = UserAgent.toLowerCase();
        if (Lower.contains("ubuntu"))  return "Ubuntu";
        if (Lower.contains("fedora"))  return "Fedora";
        if (Lower.contains("debian"))  return "Debian";
        if (Lower.contains("arch"))    return "Arch Linux";
        if (Lower.contains("mint"))    return "Linux Mint";
        if (Lower.contains("manjaro")) return "Manjaro";
        if (Lower.contains("centos"))  return "CentOS";
        if (Lower.contains("rhel") || Lower.contains("red hat")) return "Red Hat";
        if (Lower.contains("opensuse") || Lower.contains("suse")) return "openSUSE";
        if (Lower.contains("kali"))    return "Kali Linux";
        return "Generic Linux";
    }

    private static String MapWindowsVersion(String NtVersion) {
        return switch (NtVersion) {
            case "10.0" -> "10 / 11";
            case "6.3"  -> "8.1";
            case "6.2"  -> "8";
            case "6.1"  -> "7";
            case "6.0"  -> "Vista";
            case "5.2"  -> "Server 2003";
            case "5.1"  -> "XP";
            default     -> NtVersion;
        };
    }
}
