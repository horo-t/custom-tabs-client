package android.support.customtabs.trusted.webbundles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.net.Uri;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsSession;
import android.support.customtabs.TrustedWebUtils;
import android.support.customtabs.trusted.splashscreens.SplashImageTransferTask;
import android.support.v4.content.FileProvider;
import android.util.Log;
import java.io.InputStream;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

/**
 * Created by horo on 11/5/19.
 */

public class WebBundlesTransferTask {
    private final Context mContext;
    private final String mInitialWebBundles;
    private final String mAuthority;
    private final CustomTabsSession mSession;
    private final String mProviderPackage;

    private static final String TAG = "mContext";

    private static final String FOLDER_NAME = "wbn";
    private static final String FILE_NAME = "initial.wbn";
    private static final String PREFS_FILE = "webBundlesPrefs";
    private static final String PREF_LAST_UPDATE_TIME = "lastUpdateTime";

    @Nullable
    private Callback mCallback;

    /**
     * @param context {@link Context} to use.
     * @param initialWebBundle initialWebBundle.
     * @param authority {@link FileProvider} authority.
     * @param session {@link CustomTabsSession} to use for transferring the file.
     * @param providerPackage Package name of the Custom Tabs provider.
     */
    public WebBundlesTransferTask(Context context, String initialWebBundle, String authority,
                                   CustomTabsSession session, String providerPackage) {
        mContext = context.getApplicationContext();
        mInitialWebBundles = initialWebBundle;
        mAuthority = authority;
        mSession = session;
        mProviderPackage = providerPackage;
    }

    public void execute(Callback callback) {
        assert mAsyncTask.getStatus() == AsyncTask.Status.PENDING;
        mCallback = callback;
        mAsyncTask.execute();
    }
    public void cancel() {
        // mAsyncTask.cancel(true);
        mCallback = null;
    }

    @SuppressLint("StaticFieldLeak") // No leaking should happen
    private final AsyncTask<Void, Void, Uri> mAsyncTask = new AsyncTask<Void, Void, Uri>() {

        @Override
        protected Uri doInBackground(Void... args) {
            if (isCancelled()) return null;
            File dir = new File(mContext.getFilesDir(), FOLDER_NAME);
            if (!dir.exists()) {
                boolean mkDirSuccessful = dir.mkdir();
                if (!mkDirSuccessful) {
                    Log.w(TAG, "Failed to create a directory for storing a splash image");
                    return null;
                }
            }
            File file = new File(dir, FILE_NAME);
            SharedPreferences prefs =
                    mContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
            long lastUpdateTime = getLastAppUpdateTime();
            if (file.exists() && lastUpdateTime == prefs.getLong(PREF_LAST_UPDATE_TIME, 0)) {
                // Don't overwrite existing file, if it was saved later than the last time app was
                // updated
                return transferToCustomTabsProvider(file);
            }
            try(OutputStream os = new FileOutputStream(file)) {
                AssetManager assetManager = mContext.getResources().getAssets();
                InputStream is = assetManager.open(mInitialWebBundles);
                byte[] buffer = new byte[1024];
                int size = -1;
                while ((size = is.read(buffer)) > 0) {
                    os.write(buffer, 0, size);
                }
                os.flush();
                prefs.edit().putLong(PREF_LAST_UPDATE_TIME, lastUpdateTime).commit();
                if (isCancelled()) return null;
                return transferToCustomTabsProvider(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private Uri transferToCustomTabsProvider(File file) {
            Uri initialWebBundleUri = FileProvider.getUriForFile(mContext, mAuthority, file);
            mContext.grantUriPermission(mProviderPackage, initialWebBundleUri, FLAG_GRANT_READ_URI_PERMISSION);
            return initialWebBundleUri;
        }

        private long getLastAppUpdateTime() {
            try {
                return mContext.getPackageManager()
                        .getPackageInfo(mContext.getPackageName(), 0).lastUpdateTime;
            } catch (PackageManager.NameNotFoundException e) {
                // Should not happen
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onPostExecute(Uri initialWebBundleUri) {
            if (mCallback != null && !isCancelled()) {
                mCallback.onFinished(initialWebBundleUri);
            }
        }
    };

    /** Callback to be called when the file is saved and transferred to Custom Tabs provider. */
    public interface Callback {
        void onFinished(Uri successfully);
    }
}
