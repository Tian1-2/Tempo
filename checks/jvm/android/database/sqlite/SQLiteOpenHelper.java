package android.database.sqlite;
import android.content.Context;
public abstract class SQLiteOpenHelper implements AutoCloseable {
    private SQLiteDatabase db;
    public SQLiteOpenHelper(Context context,String name,Object factory,int version) { }
    public SQLiteDatabase getWritableDatabase(){if(db==null){db=new SQLiteDatabase();onConfigure(db);onCreate(db);}return db;}
    public SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
    public void onConfigure(SQLiteDatabase db) { }
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion);
    public void close(){if(db!=null)db.close();}
}
