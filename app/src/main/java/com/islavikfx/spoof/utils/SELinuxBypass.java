package com.islavikfx.spoof.utils;
import android.content.Context;
import androidx.annotation.NonNull;
import com.topjohnwu.superuser.Shell;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;


public class SELinuxBypass {

    private static final String BYPASS = "SELinuxBypass";
    private static String binaryPath;

    public static void init(Context context) {
        binaryPath = context.getFilesDir().getAbsolutePath() + "/" + BYPASS;
        extractBinary(context);
        Shell.cmd("chmod 777 " + binaryPath).exec();
    }

    private static void extractBinary(Context context) {
        File binaryFile = new File(binaryPath);
        if (binaryFile.exists()) return;

        try (InputStream input = context.getAssets().open("bin/" + BYPASS);
             FileOutputStream output = new FileOutputStream(binaryFile)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean executeWithBypass(String command) {
        try {
            Shell.Result result = Shell.cmd(binaryPath + " bypass \"" + command + "\"").exec();
            String jsonStr = String.join("\n", result.getOut());
            JSONObject json = new JSONObject(jsonStr);
            return json.optBoolean("success", false);
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    public static String getSelinuxStatus() {
        try {
            Shell.Result result = Shell.cmd(binaryPath + " status").exec();
            String jsonStr = String.join("\n", result.getOut());
            JSONObject json = new JSONObject(jsonStr);
            return json.optString("selinux_status", "Unknown");
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public static boolean isEnforcing() {
        try {
            Shell.Result result = Shell.cmd(binaryPath + " status").exec();
            String jsonStr = String.join("\n", result.getOut());
            JSONObject json = new JSONObject(jsonStr);
            return json.optBoolean("enforcing", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean installPolicy() {
        try {
            Shell.Result result = Shell.cmd(binaryPath + " install_policy").exec();
            String jsonStr = String.join("\n", result.getOut());
            JSONObject json = new JSONObject(jsonStr);
            return json.optBoolean("success", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static Pair<Integer, String> executeCommand(String command) {
        try {
            Shell.Result result = Shell.cmd(binaryPath + " execute \"" + command + "\"").exec();
            String jsonStr = String.join("\n", result.getOut());
            JSONObject json = new JSONObject(jsonStr);
            return new Pair<>(json.optInt("exit_code", -1), json.optString("output", ""));
        } catch (Exception e) {
            return new Pair<>(-1, "");
        }
    }

    public record Pair<F, S>(F first, S second) {
    }

}