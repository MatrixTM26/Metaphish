package com.metaphish.server;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.KeyStore;
import java.util.Enumeration;

public final class SslManager {
    private final String KeystorePath;
    private final String KeystorePassword;

    public SslManager(String KeystorePath, String KeystorePassword) {
        this.KeystorePath = KeystorePath;
        this.KeystorePassword = KeystorePassword;
    }

    public SSLContext BuildContext() throws Exception {
        EnsureKeystoreExists();
        KeyStore Store = KeyStore.getInstance("PKCS12");
        try (FileInputStream Stream = new FileInputStream(KeystorePath)) {
            Store.load(Stream, KeystorePassword.toCharArray());
        }
        KeyManagerFactory Factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        Factory.init(Store, KeystorePassword.toCharArray());
        SSLContext Context = SSLContext.getInstance("TLS");
        Context.init(Factory.getKeyManagers(), null, null);
        return Context;
    }

    private void EnsureKeystoreExists() throws IOException, InterruptedException {
        File KeystoreFile = new File(KeystorePath);
        if (KeystoreFile.exists()) return;

        File Parent = KeystoreFile.getParentFile();
        if (Parent != null) Parent.mkdirs();

        String JavaHome = System.getProperty("java.home");
        String KeytoolPath = JavaHome + "/bin/keytool";

        String LanIp = DetectLanIp();
        String San = "san=ip:127.0.0.1,ip:::1,dns:localhost" + (LanIp != null ? ",ip:" + LanIp : "");

        ProcessBuilder Builder = new ProcessBuilder(
                KeytoolPath, "-genkeypair",
                "-alias", "metaphish",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "3650",
                "-storetype", "PKCS12",
                "-keystore", KeystorePath,
                "-storepass", KeystorePassword,
                "-keypass", KeystorePassword,
                "-dname", "CN=metaphish,OU=Metaphish,O=Metaphish,L=Local,S=Local,C=US",
                "-ext", San
        );
        Builder.redirectErrorStream(true);
        Process KeytoolProcess = Builder.start();
        KeytoolProcess.waitFor();
    }

    private String DetectLanIp() {
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
