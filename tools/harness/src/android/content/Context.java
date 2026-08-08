package android.content;

import java.util.HashMap;

public class Context {
    public static final int MODE_PRIVATE = 0;
    private final HashMap<String, SharedPreferences> prefs =
            new HashMap<String, SharedPreferences>();

    public SharedPreferences getSharedPreferences(String name, int mode) {
        SharedPreferences p = prefs.get(name);
        if (p == null) { p = new SharedPreferences(); prefs.put(name, p); }
        return p;
    }
}
