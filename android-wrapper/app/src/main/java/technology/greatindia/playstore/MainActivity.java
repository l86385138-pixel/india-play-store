package technology.greatindia.playstore;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String HOME = "https://play.greatindia.technology/";
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
