package android.database;
import java.util.List;
public final class Cursor implements AutoCloseable {
    private final List<String> columns;private final List<List<Object>> rows;private int index=-1;
    public Cursor(List<String> columns,List<List<Object>> rows){this.columns=columns;this.rows=rows;}
    public boolean moveToFirst(){index=0;return !rows.isEmpty();}
    public boolean moveToNext(){return ++index<rows.size();}
    public int getColumnIndexOrThrow(String name){int result=columns.indexOf(name);if(result<0)throw new IllegalArgumentException(name);return result;}
    public boolean isNull(int i){return rows.get(index).get(i)==null;}
    public String getString(int i){Object o=rows.get(index).get(i);return o==null?null:o.toString();}
    public int getInt(int i){return (int)getLong(i);}
    public long getLong(int i){return isNull(i)?0:Long.parseLong(getString(i));}
    public double getDouble(int i){return isNull(i)?0:Double.parseDouble(getString(i));}
    public void close() { }
}
