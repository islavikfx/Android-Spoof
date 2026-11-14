package com.islavikfx.spoof.utils;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import com.topjohnwu.superuser.Shell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class AppInfoResolver {

    public static final class AppInfo {
        private final String packageName;
        private final String label;
        private final Drawable icon;
        private final String primaryApk;
        private volatile List<String> permissions;
        private volatile boolean permissionsLoaded;

        public AppInfo(@NonNull String packageName, @NonNull String label,
         @NonNull Drawable icon, @NonNull String primaryApk) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.primaryApk = primaryApk;
        }

        @NonNull  public String  getPackageName() {
            return packageName; }
        @NonNull  public String  getLabel() {
            return label; }
        @NonNull  public Drawable getIcon() {
            return icon; }
        @NonNull  public String  getPrimaryApk() {
            return primaryApk; }

        @NonNull
        public List<String> getPermissions() {
            List<String> p = permissions;
            return p != null ? p : Collections.emptyList();
        }

        public boolean arePermissionsLoaded() {
            return permissionsLoaded; }

        void setPermissions(@NonNull List<String> perms) {
            this.permissions = Collections.unmodifiableList(perms);
            this.permissionsLoaded = true;
        }
    }

    private final Context appContext;
    private final PackageManager pm;
    private final Drawable fallbackIcon;
    private final Map<String, AppInfo> cache = new ConcurrentHashMap<>();

    public AppInfoResolver(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.pm = appContext.getPackageManager();
        this.fallbackIcon = pm.getDefaultActivityIcon();
    }

    @NonNull
    public AppInfo resolve(@NonNull String packageName) {
        AppInfo cached = cache.get(packageName);
        if (cached != null) return cached;

        AppInfo built = buildInfo(packageName);
        AppInfo prev = cache.putIfAbsent(packageName, built);
        return prev != null ? prev : built;
    }

    @NonNull
    public String resolveLabel(@NonNull String packageName) {
        return resolve(packageName).getLabel();
    }

    @NonNull
    public Drawable resolveIcon(@NonNull String packageName) {
        return resolve(packageName).getIcon();
    }

    @NonNull
    public List<String> resolvePermissions(@NonNull String packageName) {
        AppInfo info = resolve(packageName);
        if (info.arePermissionsLoaded()) return info.getPermissions();

        synchronized (info) {
            if (info.arePermissionsLoaded()) return info.getPermissions();
            List<String> perms = loadPermissions(packageName, info.getPrimaryApk());
            info.setPermissions(perms);
            return perms;
        }
    }

    @NonNull
    public List<AppInfo> listAll(boolean skipSystem) {
        List<AppInfo> out = new ArrayList<>();

        List<String> packages = listPackageNames();
        for (String pkg : packages) {
            if (pkg.isEmpty() || !pkg.contains(".")) continue;

            if (skipSystem) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }

            out.add(resolve(pkg));
        }

        Collections.sort(out, new Comparator<AppInfo>() {
            @Override public int compare(AppInfo a, AppInfo b) {
                return a.getLabel().compareToIgnoreCase(b.getLabel());
            }
        });
        return out;
    }

    @NonNull
    public List<AppInfo> listAll() {
        return listAll(false);
    }


    public void invalidate(@NonNull String packageName) {
        cache.remove(packageName);
    }

    public void invalidateAll() {
        cache.clear();
    }

    @NonNull
    private AppInfo buildInfo(@NonNull String packageName) {
        String label = packageName.substring(packageName.lastIndexOf('.') + 1);
        Drawable icon = fallbackIcon;
        String primaryApk = "";

        boolean pmResolved = false;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) label = l.toString();
            Drawable d = ai.loadIcon(pm);
            if (d != null) icon = d;
            pmResolved = true;
        } catch (PackageManager.NameNotFoundException ignored) { }

        primaryApk = resolvePrimaryApk(packageName);

        if (!primaryApk.isEmpty() && !pmResolved) {
            try {
                PackageInfo archive = pm.getPackageArchiveInfo(
                        primaryApk, PackageManager.GET_PERMISSIONS);
                if (archive != null && archive.applicationInfo != null) {
                    ApplicationInfo aai = archive.applicationInfo;
                    aai.sourceDir = primaryApk;
                    aai.publicSourceDir = primaryApk;
                    try {
                        CharSequence l = pm.getApplicationLabel(aai);
                        if (l != null && l.length() > 0) label = l.toString();
                    } catch (Exception ignored) {}
                    try {
                        Drawable d = aai.loadIcon(pm);
                        if (d != null) icon = d;
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }

        return new AppInfo(packageName, label, icon, primaryApk);
    }

    @NonNull
    private List<String> loadPermissions(@NonNull String packageName, @NonNull String primaryApk) {
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            if (pi != null && pi.requestedPermissions != null) {
                return copyOf(pi.requestedPermissions);
            }
            return Collections.emptyList();
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        if (!primaryApk.isEmpty()) {
            try {
                PackageInfo archive = pm.getPackageArchiveInfo(
                        primaryApk, PackageManager.GET_PERMISSIONS);
                if (archive != null && archive.requestedPermissions != null) {
                    return copyOf(archive.requestedPermissions);
                }
            } catch (Exception ignored) {}
        }
        return Collections.emptyList();
    }

    @NonNull
    private String resolvePrimaryApk(@NonNull String packageName) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            if (ai.sourceDir != null && !ai.sourceDir.isEmpty()) {
                return ai.sourceDir;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        try {
            Shell.Result r = Shell.cmd("pm path " + packageName).exec();
            if (!r.isSuccess()) return "";
            String first = "";
            for (String line : r.getOut()) {
                String p = line.startsWith("package:") ? line.substring(8).trim() : line.trim();
                if (p.isEmpty()) continue;
                if (p.contains("split_config")) continue;
                if (p.contains("/base.apk")) return p;
                if (first.isEmpty()) first = p;
            }
            return first;
        } catch (Exception e) {
            return "";
        }
    }

    @NonNull
    private List<String> listPackageNames() {
        try {
            Shell.Result r = Shell.cmd("pm list packages").exec();
            if (!r.isSuccess()) return Collections.emptyList();

            List<String> out = new ArrayList<>(r.getOut().size());
            for (String line : r.getOut()) {
                String pkg = line.startsWith("package:")
                        ? line.substring(8).trim()
                        : line.trim();
                if (pkg.isEmpty() || !pkg.contains(".")) continue;
                out.add(pkg);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @NonNull
    private static List<String> copyOf(@NonNull String[] arr) {
        List<String> list = new ArrayList<>(arr.length);
        Collections.addAll(list, arr);
        return Collections.unmodifiableList(list);
    }
}
