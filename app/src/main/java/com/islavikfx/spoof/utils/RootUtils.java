package com.islavikfx.spoof.utils;
import android.annotation.SuppressLint;
import android.content.Context;
import com.topjohnwu.superuser.Shell;


public class RootUtils {

    private static boolean initialized = false;
    @SuppressLint("StaticFieldLeak")
    private static RootUtils instance;
    private Context context;

    private RootUtils() {}

    public static void init(Context context) {
        if (initialized) return;
        SELinuxBypass.init(context);
        initialized = true;
    }

    public static boolean isRootAvailable() {
        try {
            return Shell.getShell().isRoot();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean fileExists(String path) {
        try {
            Shell.Result result = Shell.cmd("test -e " + path + " && echo 1 || echo 0").exec();
            result.getOut();
            return !result.getOut().isEmpty() && result.getOut().get(0).equals("1");
        } catch (Exception e) {
            return false;
        }
    }

}