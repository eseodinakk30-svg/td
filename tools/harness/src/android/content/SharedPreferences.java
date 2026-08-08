package android.content;

import java.util.HashMap;

public class SharedPreferences {
    private final HashMap<String, Integer> map = new HashMap<String, Integer>();

    public int getInt(String k, int def) {
        Integer v = map.get(k);
        return v == null ? def : v.intValue();
    }

    public Editor edit() { return new Editor(); }

    public class Editor {
        public Editor putInt(String k, int v) { map.put(k, Integer.valueOf(v)); return this; }
        public void apply() { }
    }
}
