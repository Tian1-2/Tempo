import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.tempo.app.database.*;
import com.tempo.app.model.*;
import java.util.*;

/** Categories table, V4→V5 upgrade adopting existing task categories, and in-use protection, on real SQLite. */
public final class CategoryCheck {
    private static int count;
    private static SQLiteDatabase db;
    public static void main(String[] args) {
        try (AppDatabaseHelper helper = new AppDatabaseHelper(new Context())) {
            db = helper.getWritableDatabase();
            CategoryDao dao = new CategoryDao(helper);
            List<Category> all = dao.getAll();
            check(all.size() == 3 && all.get(0).getName().equals("Study") && all.get(2).getName().equals("Life"), "new install has Study, Work, Life in order");
            check(dao.insert("Club", Category.PALETTE[3]) && !dao.insert("club", Category.PALETTE[4]), "names are unique ignoring case");
            check(dao.getAll().get(3).getName().equals("Club"), "new category goes last");
            check(dao.setColor("Club", Category.PALETTE[5]) == 1 && dao.getAll().get(3).getColor() == Category.PALETTE[5], "colour changed");
            Task task = new Task("Poster", "Club", 30, 2, 2, 0);
            task.setTaskId((int) new TaskDao(helper).insertTask(task));
            check(dao.countTasks("CLUB") == 1 && !dao.delete("Club"), "category in use cannot be removed");
            new TaskDao(helper).archiveTask(task.getTaskId());
            check(dao.countTasks("Club") == 0 && dao.delete("Club"), "after the task is deleted the category can be removed");

            db.execSQL("DROP TABLE categories");
            Task legacy = new Task("Old", "Volunteering", 30, 2, 2, 0);
            new TaskDao(helper).insertTask(legacy);
            helper.onUpgrade(db, 4, 5);
            List<String> names = new ArrayList<>();
            for (Category c : dao.getAll()) names.add(c.getName());
            check(names.subList(0, 3).equals(Arrays.asList("Study", "Work", "Life")), "V4 upgrade seeds the three defaults first");
            check(names.contains("Volunteering") && names.contains("Club"), "V4 upgrade adopts categories already used by tasks");
            helper.onUpgrade(db, 4, 5);
            check(number("SELECT COUNT(*) FROM categories") == names.size(), "category migration is idempotent");
        }
        System.out.println("PASS: " + count + " category assertions on real SQLite.");
    }
    private static long number(String sql) { try (Cursor c = db.rawQuery(sql, null)) { c.moveToFirst(); return c.getLong(0); } }
    private static void check(boolean ok, String message) { count++; if (!ok) throw new AssertionError(message); }
}
