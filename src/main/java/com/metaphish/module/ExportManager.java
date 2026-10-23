package com.metaphish.module;

import com.metaphish.util.JsonUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExportManager {
    private final DataCapture DeviceCapture;
    private final FormCapture CredentialCapture;
    private final String ReportDirectory;
    private final DateTimeFormatter TimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public ExportManager(DataCapture DeviceCapture, FormCapture CredentialCapture, String ReportDirectory) {
        this.DeviceCapture = DeviceCapture;
        this.CredentialCapture = CredentialCapture;
        this.ReportDirectory = ReportDirectory;
        new File(ReportDirectory).mkdirs();
    }

    public String ExportToJson() throws IOException {
        String Timestamp = LocalDateTime.now().format(TimestampFormatter);
        String OutputPath = ReportDirectory + "/Metaphish_Export_" + Timestamp + ".json";

        List<Map<String, String>> Devices = DeviceCapture.LoadAll();
        List<Map<String, String>> Forms = CredentialCapture.LoadAll();

        LinkedHashMap<String, Object> Root = new LinkedHashMap<>();
        Root.put("ExportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        Root.put("DeviceCaptureCount", String.valueOf(Devices.size()));
        Root.put("FormCaptureCount", String.valueOf(Forms.size()));

        StringBuilder Builder = new StringBuilder();
        Builder.append("{\n");
        Builder.append("  \"ExportedAt\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        Builder.append("  \"DeviceCaptureCount\": ").append(Devices.size()).append(",\n");
        Builder.append("  \"FormCaptureCount\": ").append(Forms.size()).append(",\n");
        Builder.append("  \"FormCaptures\": ").append(JsonUtil.ToJsonArray(Forms)).append(",\n");
        Builder.append("  \"DeviceCaptures\": ").append(JsonUtil.ToJsonArray(Devices)).append("\n");
        Builder.append("}");

        try (FileWriter Writer = new FileWriter(OutputPath)) {
            Writer.write(Builder.toString());
        }
        return OutputPath;
    }

    public String ExportToTxt() throws IOException {
        String Timestamp = LocalDateTime.now().format(TimestampFormatter);
        String OutputPath = ReportDirectory + "/Metaphish_Export_" + Timestamp + ".txt";

        List<Map<String, String>> Devices = DeviceCapture.LoadAll();
        List<Map<String, String>> Forms = CredentialCapture.LoadAll();

        StringBuilder Builder = new StringBuilder();
        Builder.append("=".repeat(64)).append("\n");
        Builder.append("  METAPHISH - Capture Export Report\n");
        Builder.append("  Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        Builder.append("=".repeat(64)).append("\n\n");

        Builder.append("[CREDENTIALS CAPTURED]\n");
        Builder.append("-".repeat(64)).append("\n");
        if (Forms.isEmpty()) {
            Builder.append("  No credentials captured.\n");
        } else {
            int FormIndex = 1;
            for (Map<String, String> Form : Forms) {
                Builder.append("  #").append(FormIndex++).append("\n");
                for (Map.Entry<String, String> Entry : Form.entrySet()) {
                    Builder.append("    ").append(padRight(Entry.getKey(), 24)).append(": ").append(Entry.getValue()).append("\n");
                }
                Builder.append("\n");
            }
        }

        Builder.append("\n[DEVICE CAPTURES]\n");
        Builder.append("-".repeat(64)).append("\n");
        if (Devices.isEmpty()) {
            Builder.append("  No device captures.\n");
        } else {
            int DeviceIndex = 1;
            for (Map<String, String> Device : Devices) {
                Builder.append("  Capture #").append(DeviceIndex++).append("\n");
                for (Map.Entry<String, String> Entry : Device.entrySet()) {
                    Builder.append("    ").append(padRight(Entry.getKey(), 28)).append(": ").append(Entry.getValue()).append("\n");
                }
                Builder.append("\n");
            }
        }

        try (FileWriter Writer = new FileWriter(OutputPath)) {
            Writer.write(Builder.toString());
        }
        return OutputPath;
    }

    private static String padRight(String Text, int Width) {
        if (Text.length() >= Width) return Text;
        return Text + " ".repeat(Width - Text.length());
    }
}
