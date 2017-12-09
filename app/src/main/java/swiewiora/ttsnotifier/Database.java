package swiewiora.ttsnotifier;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;

/**
 * Created by Seba on 2017-12-09.
 */

class Database extends SQLiteOpenHelper {
    private static final String TAG = Database.class.getSimpleName();
    private final Context context;
    private static Database database;
    private static final int DB_VERSION = 1;
    private static final String
            OLD_FILE = "ignored_apps",
            DB_NAME = "apps.db",
            TABLE_NAME = "apps",
            COLUMN_PACKAGE = "package",
            COLUMN_LABEL = "name",
            COLUMN_ENABLED = "is_enabled",
            CREATE_TBL_APPS = "create table if not exists " + TABLE_NAME + "(" + BaseColumns._ID
                    + " integer primary key autoincrement, " + COLUMN_PACKAGE + " text not null, "
                    + COLUMN_LABEL + " text not null, " + COLUMN_ENABLED + " integer);";

    private Database(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
        try {
            if (!context.getDatabasePath(DB_NAME).exists()
                    && new File(context.getFilesDir().toString() + File.separatorChar + OLD_FILE).exists()) {
                upgradeOldIgnores();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking for old ignores to be transferred to database.");
            e.printStackTrace();
        }
    }

    /**
     * Initializes database if not already initialized.
     */
    static void init(Context context) {
        if (database == null) {
            database = new Database(context);
        }
    }

    /** Copies ignores from old file to database and deletes old file. */
    @SuppressWarnings("unchecked")
    private void upgradeOldIgnores() {
        ArrayList<String> oldList = new ArrayList<>();
        FileInputStream file = null;
        try {
            file = context.openFileInput(OLD_FILE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            ObjectInputStream in = new ObjectInputStream(file);
            try {
                oldList = (ArrayList<String>)in.readObject();
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Error: Failed to read ignored_apps - Data appears corrupt");
                e.printStackTrace();
            }
            in.close();
        } catch (IOException e) {
            Log.e(TAG, "Error: Failed to read ignored_apps");
            e.printStackTrace();
        }
        ArrayList<App> newList = new ArrayList<>();
        PackageManager packMan = context.getPackageManager();
        ApplicationInfo appInfo;
        for (String s : oldList) {
            try {
                appInfo = packMan.getApplicationInfo(s, PackageManager.GET_UNINSTALLED_PACKAGES);
                newList.add(new App(appInfo.packageName, String.valueOf(appInfo.loadLabel(packMan)), false));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        setApps(newList);
        context.deleteFile(OLD_FILE);
    }

    /** @return A new ArrayList containing all apps from the database. */
    static synchronized ArrayList<App> getApps() {
        SQLiteDatabase db = database.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, COLUMN_LABEL + " COLLATE NOCASE");
        ArrayList<App> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            list.add(new App(
                    cursor.getString(cursor.getColumnIndex(COLUMN_PACKAGE)),
                    cursor.getString(cursor.getColumnIndex(COLUMN_LABEL)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_ENABLED)) == 1
            ));
        }
        cursor.close();
        db.close();
        return list;
    }

    /**
     * Clears and sets all apps in database.
     * @param list The list of apps to add in the database.
     */
    static synchronized void setApps(ArrayList<App> list) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        ContentValues values;
        for (App app : list) {
            values = new ContentValues();
            values.put(COLUMN_PACKAGE,  app.getPackage());
            values.put(COLUMN_LABEL,  app.getLabel());
            values.put(COLUMN_ENABLED,  app.getEnabled() ? 1 : 0);
            db.insert(TABLE_NAME, null, values);
        }
        db.close();
    }

    /**
     * Updates app in database matching package name or adds if no match found.
     * @param app The app to add or update in the database.
     */
    static synchronized void addOrUpdateApp(App app) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_PACKAGE,  app.getPackage());
        values.put(COLUMN_LABEL,  app.getLabel());
        values.put(COLUMN_ENABLED,  app.getEnabled() ? 1 : 0);
        SQLiteDatabase db = database.getWritableDatabase();
        if (db.update(TABLE_NAME, values, COLUMN_PACKAGE + " = ?", new String[] {app.getPackage()}) == 0) {
            db.insert(TABLE_NAME, null, values);
        }
        db.close();
    }

    /**
     * Updates enabled value of app in database matching package name.
     * @param app The app to update in the database.
     */
    static synchronized void updateAppEnable(App app) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ENABLED,  app.getEnabled() ? 1 : 0);
        SQLiteDatabase db = database.getWritableDatabase();
        db.update(TABLE_NAME, values, COLUMN_PACKAGE + " = ?", new String[] {app.getPackage()});
        db.close();
    }

    /**
     * Removes app from database matching package name.
     * @param app The app to remove from the database.
     */
    static synchronized void removeApp(App app) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_PACKAGE + " = ?", new String[] {app.getPackage()});
        db.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TBL_APPS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
