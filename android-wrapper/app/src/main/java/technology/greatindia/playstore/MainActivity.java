package technology.greatindia.playstore;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "https://play.greatindia.technology/";
    private static final int PERMISSION_REQUEST = 7001;
    private DownloadManager dm;
    private BroadcastReceiver receiver;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        WebView w = new WebView(this);
        setContentView(w);
        w.getSettings().setJavaScriptEnabled(true);
        w.getSettings().setDomStorageEnabled(true);
        w.getSettings().setAllowFileAccess(false);
        w.setWebViewClient(new WebViewClient());
        w.setDownloadListener((url, userAgent, contentDisposition, mime, length) -> startDownload(url, contentDisposition));
        w.loadUrl(HOME);
        requestAppPermissions();
    }

    private void requestAppPermissions() {
        ArrayList<String> p = new ArrayList<>();
        addIfNeeded(p, Manifest.permission.CAMERA);
        addIfNeeded(p, Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT <= 32) {
            addIfNeeded(p, Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            addIfNeeded(p, Manifest.permission.READ_MEDIA_IMAGES);
            addIfNeeded(p, Manifest.permission.READ_MEDIA_VIDEO);
            addIfNeeded(p, Manifest.permission.READ_MEDIA_AUDIO);
            addIfNeeded(p, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private void addIfNeeded(ArrayList<String> p, String permission) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) p.add(permission);
    }

    private void startDownload(String url, String disposition) {
        try {
            String name = URLUtil.guessFileName(url, disposition, "application/vnd.android.package-archive");
            if (!name.toLowerCase().endsWith(".apk")) name += ".apk";
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle(name);
            r.setDescription("Downloading APK");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            dm = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            long id = dm.enqueue(r);
            receiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) {
                long done = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (done != id) return;
                unregisterReceiverSafe();
                Uri apk = dm.getUriForDownloadedFile(done);
                if (apk != null) openInstaller(apk);
            }};
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } catch (Exception e) { Toast.makeText(this, "APK download failed", Toast.LENGTH_LONG).show(); }
    }

    private void openInstaller(Uri apk) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(s);
            Toast.makeText(this, "Allow installation, then open the APK from Downloads", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(apk, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "Open the downloaded APK from Downloads", Toast.LENGTH_LONG).show(); }
    }

    private void unregisterReceiverSafe() { if (receiver != null) { try { unregisterReceiver(receiver); } catch (Exception ignored) {} receiver = null; } }
    @Override protected void onDestroy() { unregisterReceiverSafe(); super.onDestroy(); }
}
