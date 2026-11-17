package android.content;
import java.util.LinkedHashMap;
public class ContentValues extends LinkedHashMap<String,Object> {
    public void putNull(String key) {put(key,null);}
}
