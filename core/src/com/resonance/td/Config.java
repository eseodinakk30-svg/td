package com.resonance.td;

import com.badlogic.gdx.graphics.Color;

/**
 * Все числа игры в одном месте: размеры поля, тайминги, баланс, палитра.
 * Класс намеренно не зависит ни от чего, кроме Color, — чтобы симуляцию
 * можно было гонять headless.
 */
public final class Config {

    private Config() {
    }

    // ------------------------------------------------------------------ поле
    public static final int COLS = 11;
    public static final int ROWS = 15;

    /** Ядро — снизу по центру. */
    public static final int CORE_X = 5;
    public static final int CORE_Y = 1;

    /** Порталы сверху; сколько из них активно — зависит от волны. */
    public static final int[] PORTAL_X = {5, 1, 9};
    public static final int[] PORTAL_Y = {ROWS - 1, ROWS - 1, ROWS - 1};

    // ----------------------------------------------------------------- ритм
    /** Длительность такта в секундах (≈83 BPM). */
    public static final float BEAT = 0.72f;

    // --------------------------------------------------------------- башни
    public static final int T_PULSAR = 0;
    public static final int T_PHASER = 1;
    public static final int T_RESONATOR = 2;
    public static final int T_WALL = 3;
    public static final int T_COUNT = 4;

    public static final String[] T_NAME = {"ПУЛЬСАР", "ФАЗЕР", "РЕЗОНАТОР", "БАРЬЕР"};
    public static final String[] T_DESC = {
            "Фронт каждый такт. Основа решётки.",
            "Тот же фронт, но со сдвигом на полтакта.",
            "Медленный широкий фронт раз в два такта.",
            "Не излучает. Гнёт маршрут врага."
    };
    public static final int[] T_COST = {40, 55, 95, 10};

    /** Период излучения в тактах. */
    public static final int[] T_INTERVAL = {1, 1, 2, 0};
    /** Стартовый сдвиг фазы в полутактах (0 или 1). */
    public static final int[] T_PHASE = {0, 1, 0, 0};
    /** Максимальный радиус фронта в клетках. */
    public static final float[] T_RADIUS = {3.3f, 3.3f, 6.2f, 0f};
    /** Сколько секунд фронт идёт до максимума. */
    public static final float[] T_TRAVEL = {1.8f * BEAT, 1.8f * BEAT, 3.2f * BEAT, 0f};
    /** Энергия фронта — вклад в урон узла. */
    public static final float[] T_ENERGY = {1.0f, 1.0f, 1.7f, 0f};

    /**
     * Нагрузка на сеть. Мощность ограничена, поэтому башни нельзя просто
     * наставить везде — приходится выбирать, где именно строить решётку.
     */
    public static final int[] T_POWER = {2, 2, 4, 0};
    public static final int UPGRADE_POWER = 1;
    public static final int START_POWER = 10;
    public static final int POWER_PER_WAVE = 2;
    /** Сколько мощности даёт один купленный генератор. */
    public static final int POWER_PER_NODE = 3;
    public static final int GEN_BASE_COST = 110;
    public static final float GEN_COST_GROWTH = 1.42f;

    /** Прибавки за уровень (уровни 1..3). */
    public static final float UPGRADE_ENERGY = 0.55f;
    public static final float UPGRADE_RADIUS = 0.55f;
    public static final int MAX_LEVEL = 3;
    /** Цена улучшения = базовая цена * этот коэффициент * уровень. */
    public static final float UPGRADE_COST_K = 0.8f;
    /** Цена переключения фазы. */
    public static final int PHASE_COST = 15;
    /** Доля возврата при продаже. */
    public static final float SELL_REFUND = 0.6f;

    // ------------------------------------------------------------- резонанс
    /** Радиус поражения узла, в клетках. */
    public static final float NODE_RADIUS = 0.46f;
    /** На каком расстоянии два узла считаются одним (гармоника), в клетках. */
    public static final float HARMONIC_RADIUS = 0.5f;
    /** Базовый урон узла первого порядка, в секунду. */
    public static final float NODE_DPS = 140f;
    /** Прибавка за каждый порядок выше первого (в долях). */
    public static final float HARMONIC_BONUS = 0.85f;
    /** Порядок, начиная с которого узел пробивает броню. */
    public static final int HARMONIC_PIERCE = 3;
    /** Насколько считаются все узлы, кроме самого сильного (убывающая отдача). */
    public static final float STACK_FALLOFF = 0.35f;
    /** Множитель урона по броне от негармонического узла. */
    public static final float ARMOR_RESIST = 0.12f;

    // --------------------------------------------------------------- враги
    public static final int E_DRONE = 0;
    public static final int E_SWIFT = 1;
    public static final int E_ARMOR = 2;
    public static final int E_SWARM = 3;
    public static final int E_MOTE = 4;
    public static final int E_PHANTOM = 5;
    public static final int E_COUNT = 6;

    public static final String[] E_NAME = {"ДРОН", "СТРИЖ", "ПАНЦИРЬ", "РОЙ", "ИСКРА", "ФАНТОМ"};
    public static final float[] E_HP = {32f, 20f, 105f, 46f, 11f, 62f};
    public static final float[] E_SPEED = {1.55f, 3.1f, 1.05f, 1.7f, 2.7f, 1.65f};
    public static final int[] E_BOUNTY = {4, 3, 10, 6, 1, 7};
    public static final int[] E_MASS = {1, 1, 3, 2, 1, 2};

    // ------------------------------------------------------------- экономика
    public static final int START_ENERGY = 220;
    public static final int START_INTEGRITY = 20;
    public static final int WAVES_TOTAL = 20;
    /** Пауза перед волной, сек. */
    public static final float PREP_TIME = 22f;
    /** Бонус за досрочный вызов волны: за каждую сэкономленную секунду. */
    public static final float EARLY_BONUS = 2.2f;

    // --------------------------------------------------------------- палитра
    public static final Color BG_TOP = new Color(0.055f, 0.075f, 0.145f, 1f);
    public static final Color BG_BOTTOM = new Color(0.015f, 0.02f, 0.05f, 1f);
    public static final Color GRID = new Color(0.30f, 0.52f, 0.72f, 0.16f);
    public static final Color CORE_C = new Color(0.55f, 1f, 0.85f, 1f);
    public static final Color PORTAL_C = new Color(1f, 0.42f, 0.35f, 1f);
    public static final Color NODE_C = new Color(1f, 0.97f, 0.80f, 1f);
    public static final Color TEXT = new Color(0.85f, 0.93f, 1f, 1f);
    public static final Color TEXT_DIM = new Color(0.55f, 0.65f, 0.80f, 1f);
    public static final Color PANEL = new Color(0.07f, 0.10f, 0.18f, 0.92f);
    public static final Color OK_C = new Color(0.45f, 1f, 0.72f, 1f);
    public static final Color BAD_C = new Color(1f, 0.38f, 0.42f, 1f);

    public static final Color[] T_COLOR = {
            new Color(0.30f, 0.90f, 1.00f, 1f),   // пульсар — циан
            new Color(1.00f, 0.35f, 0.85f, 1f),   // фазер — маджента
            new Color(1.00f, 0.75f, 0.25f, 1f),   // резонатор — янтарь
            new Color(0.55f, 0.62f, 0.72f, 1f)    // барьер — сталь
    };

    public static final Color[] E_COLOR = {
            new Color(0.95f, 0.55f, 0.45f, 1f),
            new Color(1.00f, 0.85f, 0.40f, 1f),
            new Color(0.70f, 0.60f, 1.00f, 1f),
            new Color(0.60f, 1.00f, 0.55f, 1f),
            new Color(0.80f, 1.00f, 0.70f, 1f),
            new Color(0.55f, 0.85f, 1.00f, 1f)
    };
}
