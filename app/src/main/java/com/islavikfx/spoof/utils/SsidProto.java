package com.islavikfx.spoof.utils;
import android.util.Base64;
import androidx.annotation.NonNull;
import com.topjohnwu.superuser.Shell;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SsidProto {

    private SsidProto() { }

    public static final class PackageSsid {
        private final String packageName;
        private final String ssid;
        private final String defaultValue;
        private final boolean defaultSysSet;

        public PackageSsid(@NonNull String packageName, @NonNull String ssid, @NonNull String defaultValue, boolean defaultSysSet) {
            this.packageName = packageName;
            this.ssid = ssid;
            this.defaultValue = defaultValue;
            this.defaultSysSet = defaultSysSet;
        }

        public String getPackageName() {
            return packageName; }
        public String getSsid() {
            return ssid; }
        public String getDefaultValue() {
            return defaultValue; }
        public boolean getDefaultSysSet() {
            return defaultSysSet; }

        @NonNull @Override public String toString() {
            return "PackageSsid{" + packageName + "=" + ssid + "}";
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PackageSsid)) return false;
            PackageSsid that = (PackageSsid) o;
            return defaultSysSet == that.defaultSysSet
                    && packageName.equals(that.packageName)
                    && ssid.equals(that.ssid)
                    && defaultValue.equals(that.defaultValue);
        }

        @Override public int hashCode() {
            int h = packageName.hashCode();
            h = 31 * h + ssid.hashCode();
            h = 31 * h + defaultValue.hashCode();
            h = 31 * h + (defaultSysSet ? 1 : 0);
            return h;
        }
    }


    @NonNull
    public static String generateRandomId(boolean isAndroidPackage) {
        String chars = isAndroidPackage ? "0123456789ABCDEF" : "0123456789abcdef";
        int length = isAndroidPackage ? 64 : 17;
        StringBuilder sb = new StringBuilder(length);
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }


    @NonNull
    public static List<PackageSsid> parseFile(@NonNull String filePath) {
        List<PackageSsid> result = new ArrayList<>();
        try {
            String xmlContent = readFileContent(filePath);
            if (!xmlContent.isEmpty()) {
                if (isAbxFile(filePath)) {
                    String decoded = decodeAbxToXml(filePath);
                    if (!decoded.isEmpty()) parseXmlContent(decoded, result);
                } else {
                    parseXmlContent(xmlContent, result);
                }
            }
        } catch (Exception ignored) {}

        if (result.isEmpty()) {
            tryParseBinaryRaw(filePath, result);
        }
        return result;
    }

    private static boolean isAbxFile(@NonNull String filePath) {
        try {
            Shell.Result r = Shell.cmd("head -c 3 " + filePath + " 2>/dev/null").exec();
            return r.isSuccess() && !r.getOut().isEmpty() && r.getOut().get(0).startsWith("ABX");
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    private static String readFileContent(@NonNull String filePath) {
        try {
            Shell.Result r = Shell.cmd("cat " + filePath + " 2>/dev/null").exec();
            if (r.isSuccess() && !r.getOut().isEmpty()) {
                return String.join("\n", r.getOut());
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    @NonNull
    private static String decodeAbxToXml(@NonNull String filePath) {
        final String tmp = "/data/local/tmp/ssid_temp.xml";
        try {
            Shell.cmd("rm -f " + tmp).exec();
            Shell.Result r = Shell.cmd("abx2xml " + filePath + " " + tmp + " 2>/dev/null && cat " + tmp + " 2>/dev/null").exec();
            Shell.cmd("rm -f " + tmp).exec();
            if (r.isSuccess() && !r.getOut().isEmpty()) {
                return String.join("\n", r.getOut());
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void parseXmlContent(@NonNull String content, @NonNull List<PackageSsid> result) {
        try {
            Pattern p = Pattern.compile("<setting\\s+[^>]*package=\"([^\"]+)\"[^>]*value=\"([^\"]+)\"[^>]*(?:defaultValue=\"([^\"]*)\")?[^>]*(?:defaultSysSet=\"([^\"]*)\")?[^>]*>");
            Matcher m = p.matcher(content);
            while (m.find()) {
                String pkg = m.group(1);
                String value = m.group(2);
                String def = m.group(3);
                boolean sysSet = "true".equals(m.group(4));
                if (pkg != null && !pkg.isEmpty()
                        && value != null && !value.isEmpty()) {
                    result.add(new PackageSsid(pkg, value,
                            (def == null || def.isEmpty()) ? value : def, sysSet));
                }
            }
        } catch (Exception ignored) { }
    }

    private static void tryParseBinaryRaw(@NonNull String filePath, @NonNull List<PackageSsid> result) {
        try {
            Shell.Result r = Shell.cmd("cat " + filePath + " | base64").exec();
            if (!r.isSuccess() || r.getOut().isEmpty()) return;

            byte[] raw = Base64.decode(String.join("", r.getOut()), Base64.DEFAULT);
            String content = new String(raw, StandardCharsets.ISO_8859_1);

            int userKeyIdx = content.indexOf("userkey");
            if (userKeyIdx != -1) {
                String after = content.substring(userKeyIdx);
                Matcher hex64 = Pattern.compile("[A-Fa-f0-9]{64}").matcher(after);
                if (hex64.find()) {
                    String v = hex64.group();
                    result.add(new PackageSsid("android", v, v, true));
                }
            }

            Pattern pkgPattern = Pattern.compile("([a-z][a-z0-9_]+\\.[a-z][a-z0-9_.]+)");
            Pattern hexPattern = Pattern.compile("[A-Fa-f0-9]{16,17}");
            Set<String> found = new HashSet<>();
            found.add("android");

            Matcher hexM = hexPattern.matcher(content);
            while (hexM.find()) {
                String hexId = hexM.group();
                int idx = hexM.start();
                int start = Math.max(0, idx - 200);
                int end = Math.min(content.length(), idx + 200);
                Matcher pkgM = pkgPattern.matcher(content.substring(start, end));
                if (pkgM.find()) {
                    String pkg = pkgM.group(1);
                    if (!found.contains(pkg) && pkg.contains(".") && !pkg.endsWith(".")) {
                        found.add(pkg);
                        result.add(new PackageSsid(pkg, hexId, hexId, false));
                    }
                }
            }
        } catch (Exception ignored) { }
    }


    public static boolean updateFile(@NonNull String filePath, @NonNull String packageName, @NonNull String newValue) {
        try {
            if (updateViaAbx(filePath, packageName, newValue)) return true;
            return updateBinaryDirect(filePath, packageName, newValue);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean updateViaAbx(@NonNull String filePath, @NonNull String packageName, @NonNull String newValue) {
        final String work = "/data/local/tmp/ssid_work.xml";
        final String workNew = "/data/local/tmp/ssid_work_new.xml";
        try {
            Shell.cmd("rm -f " + work + " " + workNew).exec();

            if (isAbxFile(filePath)) {
                Shell.Result r = Shell.cmd("abx2xml " + filePath + " " + work + " 2>/dev/null && cat " + work).exec();
                if (!r.isSuccess() || r.getOut().isEmpty()) return false;
            } else {
                Shell.Result r = Shell.cmd("cp " + filePath + " " + work + " 2>/dev/null && cat " + work).exec();
                if (!r.isSuccess() || r.getOut().isEmpty()) return false;
            }

            String sedExpr = "/package=\"" + packageName + "\"/ { " + "s/value=\"[^\"]*\"/value=\"" + newValue + "\"/; " + "s/defaultValue=\"[^\"]*\"/defaultValue=\"" + newValue + "\"/; " + "}";
            Shell.cmd("sed -i '" + sedExpr + "' " + work).exec();
            if (isAbxFile(filePath)) {
                Shell.cmd("xml2abx " + work + " " + filePath).exec();
            } else {
                Shell.cmd("cp " + work + " " + filePath).exec();
            }

            String fallback = filePath + ".fallback";
            Shell.cmd("cp " + filePath + " " + fallback).exec();
            fixOwnership(filePath);
            fixOwnership(fallback);

            Shell.cmd("rm -f " + work + " " + workNew).exec();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private static boolean updateBinaryDirect(@NonNull String filePath, @NonNull String packageName, @NonNull String newValue) {
        try {
            Shell.Result r = Shell.cmd("cat " + filePath + " | base64").exec();
            if (!r.isSuccess() || r.getOut().isEmpty()) return false;

            byte[] raw = Base64.decode(String.join("", r.getOut()), Base64.DEFAULT);

            List<PackageSsid> data = parseFile(filePath);
            PackageSsid item = null;
            for (PackageSsid it : data) {
                if (it.getPackageName().equals(packageName)) {item = it; break; }
            }
            if (item == null) return false;

            byte[] newBytes = newValue.getBytes(StandardCharsets.ISO_8859_1);
            byte[] modified = replaceInBytes(raw,
                    item.getSsid().getBytes(StandardCharsets.ISO_8859_1), newBytes);

            if (!item.getDefaultValue().isEmpty()
                    && !item.getDefaultValue().equals(item.getSsid())) {
                modified = replaceInBytes(modified,
                        item.getDefaultValue().getBytes(StandardCharsets.ISO_8859_1), newBytes);
            }

            String encoded = Base64.encodeToString(modified, Base64.DEFAULT);
            Shell.cmd("echo '" + encoded + "' | base64 -d > " + filePath).exec();
            String fallback = filePath + ".fallback";
            Shell.cmd("cp " + filePath + " " + fallback).exec();
            fixOwnership(filePath);
            fixOwnership(fallback);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void fixOwnership(@NonNull String path) {
        Shell.cmd("chown system:system " + path).exec();
        Shell.cmd("chmod 660 " + path).exec();
        Shell.cmd("restorecon " + path + " 2>/dev/null || true").exec();
        Shell.cmd("sync").exec();
    }

    private static byte[] replaceInBytes(@NonNull byte[] data, @NonNull byte[] oldBytes, @NonNull byte[] newBytes) {
        if (oldBytes.length == 0) return data;

        byte[] paddedNew = new byte[oldBytes.length];
        System.arraycopy(newBytes, 0, paddedNew, 0, Math.min(newBytes.length, oldBytes.length));

        byte[] out = data.clone();
        int limit = out.length - oldBytes.length;

        outer:
        for (int pos = 0; pos <= limit; pos++) {
            for (int j = 0; j < oldBytes.length; j++) {
                if (out[pos + j] != oldBytes[j]) continue outer;
            }
            System.arraycopy(paddedNew, 0, out, pos, paddedNew.length);
            return out;
        }
        return data;
    }
}
