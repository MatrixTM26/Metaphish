package com.metaphish.util;

public final class AnsiColor {
    public static final String Reset      = "\u001B[0m";
    public static final String Bold       = "\u001B[1m";
    public static final String Dim        = "\u001B[2m";
    public static final String Underline  = "\u001B[4m";

    public static final String Black      = "\u001B[30m";
    public static final String Red        = "\u001B[31m";
    public static final String Green      = "\u001B[32m";
    public static final String Yellow     = "\u001B[33m";
    public static final String Blue       = "\u001B[34m";
    public static final String Magenta    = "\u001B[35m";
    public static final String Cyan       = "\u001B[36m";
    public static final String White      = "\u001B[37m";
    public static final String Orange     = "\u001B[38;5;214m";

    public static final String BrightBlack   = "\u001B[90m";
    public static final String BrightRed     = "\u001B[91m";
    public static final String BrightGreen   = "\u001B[92m";
    public static final String BrightYellow  = "\u001B[93m";
    public static final String BrightBlue    = "\u001B[94m";
    public static final String BrightMagenta = "\u001B[95m";
    public static final String BrightCyan    = "\u001B[96m";
    public static final String BrightWhite   = "\u001B[97m";

    private AnsiColor() {}

    public static String Colorize(String Text, String... Codes) {
        if (Codes == null || Codes.length == 0) return Text;
        StringBuilder Builder = new StringBuilder();
        for (String Code : Codes) Builder.append(Code);
        Builder.append(Text).append(Reset);
        return Builder.toString();
    }
}
