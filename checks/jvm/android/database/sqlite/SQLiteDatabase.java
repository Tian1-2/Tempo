package android.database.sqlite;
import android.content.ContentValues;
import android.database.Cursor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only Android API calls used by the production store/DAOs; SQL is executed by real SQLite. */
public final class SQLiteDatabase implements AutoCloseable {
    public static final int CONFLICT_IGNORE=4;
    private final Process process;private final BufferedWriter in;private final BufferedReader out;private final Deque<Boolean> committed = new ArrayDeque<>();
    private boolean rollback;
    public SQLiteDatabase(){try{
        process=new ProcessBuilder(System.getProperty("python","python3"),"-u",System.getProperty("sqlite.bridge")).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        in=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
        out=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
    }catch(IOException e){throw new RuntimeException(e);}}
    private static String b64(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private static String enc(Object o){if(o==null)return "n";if(o instanceof Number)return "i"+o;return "s"+b64(o.toString());}
    private static Object dec(String s){if(s.equals("n"))return null;if(s.startsWith("s"))return new String(Base64.getDecoder().decode(s.substring(1)),StandardCharsets.UTF_8);return s.substring(1);}
    private String[] send(String mode,String sql,List<?> args){try{
        in.write(mode+"\t"+b64(sql));for(Object value:args)in.write("\t"+enc(value));in.newLine();in.flush();
        String line=out.readLine();if(line==null)throw new IOException("SQLite bridge closed");String[] parts=line.split("\t");
        if(parts[0].equals("ERR"))throw new IllegalStateException(new String(Base64.getDecoder().decode(parts[1]),StandardCharsets.UTF_8));return parts;
    }catch(IOException e){throw new RuntimeException(e);}}
    private static List<String> args(String[] values){return values==null?Collections.emptyList():Arrays.asList(values);}
    public Cursor rawQuery(String sql,String[] values){String[] head=send("q",sql,args(values));try{
        int n=Integer.parseInt(head[2]);List<String> cols=new ArrayList<>();for(String col:out.readLine().split("\t"))cols.add((String)dec(col));
        List<List<Object>> rows=new ArrayList<>();for(int i=0;i<n;i++){List<Object> row=new ArrayList<>();for(String value:out.readLine().split("\t",-1))row.add(dec(value));rows.add(row);}return new Cursor(cols,rows);
    }catch(IOException e){throw new RuntimeException(e);}}
    public Cursor query(String table,String[] cols,String where,String[] values,String group,String having,String order){return query(table,cols,where,values,group,having,order,null);}
    public Cursor query(String table,String[] cols,String where,String[] values,String group,String having,String order,String limit){
        return rawQuery("SELECT "+(cols==null?"*":String.join(",",cols))+" FROM "+table+(where==null?"":" WHERE "+where)+(group==null?"":" GROUP BY "+group)+(having==null?"":" HAVING "+having)+(order==null?"":" ORDER BY "+order)+(limit==null?"":" LIMIT "+limit),values);
    }
    public long insert(String table,String nullColumn,ContentValues values){return insertOrThrow(table,nullColumn,values);}
    public long insertOrThrow(String table,String nullColumn,ContentValues values){return insertWithOnConflict(table,nullColumn,values,0);}
    public long insertWithOnConflict(String table,String nullColumn,ContentValues values,int conflict){
        String sql="INSERT "+(conflict==CONFLICT_IGNORE?"OR IGNORE ":"")+"INTO "+table+" ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")";
        String[] result=send("e",sql,new ArrayList<>(values.values()));return Long.parseLong(result[1])==0?-1:Long.parseLong(result[2]);
    }
    public int update(String table,ContentValues values,String where,String[] selection){
        List<String> sets=new ArrayList<>();for(String key:values.keySet())sets.add(key+"=?");List<Object> all=new ArrayList<>(values.values());all.addAll(args(selection));
        return Integer.parseInt(send("e","UPDATE "+table+" SET "+String.join(",",sets)+(where==null?"":" WHERE "+where),all)[1]);
    }
    public int delete(String table,String where,String[] values){return Integer.parseInt(send("e","DELETE FROM "+table+(where==null?"":" WHERE "+where),args(values))[1]);}
    public void execSQL(String sql){send("e",sql,Collections.emptyList());}
    public void setForeignKeyConstraintsEnabled(boolean enabled){execSQL("PRAGMA foreign_keys="+(enabled?"ON":"OFF"));}
    public void beginTransaction(){
        if (committed.isEmpty()) { execSQL("BEGIN"); rollback=false; }
        committed.push(false);
    }
    public void beginTransactionNonExclusive(){beginTransaction();}
    public void setTransactionSuccessful(){committed.pop(); committed.push(true);}
    public void endTransaction(){
        if (!committed.pop()) rollback=true;
        if (committed.isEmpty()) execSQL(rollback ? "ROLLBACK" : "COMMIT");
    }
    public void close(){try{in.close();process.destroy();}catch(IOException e){throw new RuntimeException(e);}}
}
