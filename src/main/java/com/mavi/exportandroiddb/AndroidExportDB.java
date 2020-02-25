package com.mavi.exportandroiddb;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class AndroidExportDB extends AsyncTask<IAndroidExportDB.Callback, Void, Void> implements IAndroidExportDB {

    private static final String TAG = "AndroidExportDB";

    // app context wrapped into a {WeakReference}
    private WeakReference<Context> contextWeakReference;

    // app db path
    private String dbPath;

    // backup db name
    private String dbBackupName;

    // backup db path
    private String dbBackupPath;

    // progress dialog
    private ProgressDialog progressDialog;

    private AndroidExportDB(Builder builder) {
        this.contextWeakReference = builder.context;
        this.dbPath = builder.dbPath;

        this.dbBackupName = builder.dbBackupName;
        this.dbBackupPath = builder.dbBackupPath;

        this.progressDialog = builder.progressDialog;

        if (progressDialog != null) {
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setMessage(contextWeakReference.get().getString(R.string.exporting_database));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
        }
    }

    @Override
    public void export(final Callback callback) {
        if (progressDialog != null)
            progressDialog.show();
        Log.d(TAG, "export: Export started...");
        this.execute(callback);
    }

    @Override
    protected Void doInBackground(final Callback... callbacks) {
        List<String> dbNames = new ArrayList<>(resolveDBNames());

        // iterate db's files
        File flagFile = null;
        for (String dbName : dbNames) {
            if ((flagFile = processExport(dbName, callbacks[0])) == null) {
                break;
            }
        }

        // if there's an null file into the iterator the export is not successful
        final File finalFlagFile = flagFile;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (finalFlagFile != null)
                    callbacks[0].onExported();
                Log.d(TAG, "run: Backup created successful in " + dbBackupPath);
            }
        });

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (progressDialog != null)
            progressDialog.dismiss();
    }

    private List<String> resolveDBNames() {
        List<String> fileNames = new ArrayList<>();
        File dbPathFile = new File(dbPath);

        File[] children;
        if ((children = dbPathFile.listFiles()) != null) {
            for (File file : children) {
                if (!file.getName().contains("journal"))
                    fileNames.add(file.getName());
            }
        }

        return fileNames;
    }

    private File processExport(String dbName, Callback callback) {
        File dbFile = new File(dbPath + dbName);
        final File dbBackupFile = new File(dbBackupPath, dbBackupName.concat("_").concat(dbName));
        File dbBackupFolder = new File(dbBackupPath);

        // check if exist db file in storage
        if (!dbFile.exists()) {
            Throwable t = new FileNotFoundException(contextWeakReference.get().getString(R.string.db_file_not_exist));
            Log.e(TAG, "doInBackground: " + dbFile.getAbsolutePath() + " found!", t);
            callback.onFailure(t);
            return null;
        }

        // create backup folder
        if (!dbBackupFolder.exists()) {
            if (!dbBackupFolder.mkdirs()) {
                Throwable t = new IllegalAccessException(contextWeakReference.get().getString(R.string.cant_create_folder_in_storage));
                Log.e(TAG, "doInBackground: Backup couldn't be created", t);
                callback.onFailure(t);
                return null;
            }
        }

        // delete an existing backup file
        if (dbBackupFile.exists()) {
            Log.d(TAG, "doInBackground: BackupFile " + dbBackupFile.getName() + " already exists!");
            if (!dbBackupFile.delete()) {
                Throwable t = new IllegalAccessException(contextWeakReference.get().getString(R.string.db_file_exits_not_deleted));
                Log.e(TAG, "doInBackground: File didn't delete.", t);
                callback.onFailure(t);
                return null;
            }
        }

        // export db
        try {
            FileChannel fileChannelDB = new FileInputStream(dbFile).getChannel();
            FileChannel fileChannelBackup = new FileOutputStream(dbBackupFile).getChannel();

            fileChannelBackup.transferFrom(fileChannelDB, 0, fileChannelDB.size());

            fileChannelDB.close();
            fileChannelBackup.close();

            return dbBackupFile;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "doInBackground: FileNotFoundException", e);
            callback.onFailure(e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "doInBackground: IOException", e);
            callback.onFailure(e);
            return null;
        }
    }

    /**
     * Builder pattern implementation
     */
    public static class Builder {

        private WeakReference<Context> context;

        private ProgressDialog progressDialog;

        private String dbPath;

        private String dbBackupName;
        private String dbBackupPath;

        public Builder(Context context, boolean showProgress) {

            this.context = new WeakReference<>(context);

            this.dbPath = Environment.getDataDirectory().getAbsolutePath().concat("/data/")
                    .concat(context.getPackageName()).concat("/databases/");

            this.dbBackupName = context.getString(R.string.db).concat("-").concat(context.getString(R.string.app_name)).replace(" ", "");

            File defaultDir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                    (defaultDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)) != null) {
                this.dbBackupPath = defaultDir.getAbsolutePath().concat("/").concat(context.getString(R.string.app_name)).replace(" ", "").concat("/");
            } else {
                this.dbBackupPath = Environment.getExternalStorageDirectory().getAbsolutePath().concat("/").concat(context.getString(R.string.app_name)).replace(" ", "").concat("/");
            }

            if (showProgress)
                this.progressDialog = new ProgressDialog(context);
        }

        public Builder setDbPath(String dbPath) {
            this.dbPath = dbPath;
            return this;
        }

        public Builder setDbBackupName(String dbBackupName) {
            this.dbBackupName = dbBackupName;
            return this;
        }

        public Builder setDbBackupPath(String dbBackupPath) {
            this.dbBackupPath = dbBackupPath;
            return this;
        }

        private AndroidExportDB build() {
            return new AndroidExportDB(this);
        }

    }

    public static void export(Builder builder, Callback callback) {
        builder.build().export(callback);
    }

}
