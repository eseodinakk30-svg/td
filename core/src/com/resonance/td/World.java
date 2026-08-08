package com.resonance.td;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

import java.util.Random;

/**
 * Вся симуляция. Рендер сюда не заглядывает — только читает состояние,
 * поэтому мир гоняется и без графики (см. tools/SimTest.java).
 *
 * Главная идея: башни не стреляют. Каждая на своём такте выпускает
 * расширяющееся кольцо. Урон возникает ТОЛЬКО в точках пересечения колец
 * разных башен — «узлах резонанса». Совпали три кольца — узел пробивает броню.
 */
public class World {

    public static final int ST_PREP = 0;
    public static final int ST_FIGHT = 1;
    public static final int ST_LOST = 2;
    public static final int ST_WON = 3;

    public static final int MAX_NODES = 256;

    /** Неразрушаемые глыбы — задают характер карты. */
    private static final int[] ROCKS = {
            0, 7, 1, 7, 2, 7,
            8, 7, 9, 7, 10, 7,
            4, 4, 6, 4,
            5, 11
    };

    public final Grid grid = new Grid(Config.COLS, Config.ROWS);
    public final boolean[] rock = new boolean[Config.COLS * Config.ROWS];
    public final int[] towerAt = new int[Config.COLS * Config.ROWS];

    public final Array<Tower> towers = new Array<Tower>();
    public final Array<Enemy> enemies = new Array<Enemy>();
    public final Array<Pulse> pulses = new Array<Pulse>();
    public final Array<ResNode> nodes = new Array<ResNode>();
    public final Array<Particle> particles = new Array<Particle>();

    private final Array<Enemy> enemyPool = new Array<Enemy>();
    private final Array<Pulse> pulsePool = new Array<Pulse>();
    private final Array<Particle> particlePool = new Array<Particle>();

    public int state = ST_PREP;
    public int energy = Config.START_ENERGY;
    public int integrity = Config.START_INTEGRITY;
    public int wave = 0;              // номер уже начатой волны
    public float prepLeft = 6f;       // до первой волны меньше времени
    public float waveTime;
    public int spawned, waveCount;
    public int leaked, killed;
    public int gensBought;

    public float time;
    public int halfIndex;
    public float halfTimer;
    /** 0..1 внутри полутакта — для пульсации интерфейса. */
    public float beatPhase;

    public float shake;
    /** Счётчики событий для звука/эффектов, сбрасываются читателем. */
    public int evBeat, evKill, evLeak, evBuild, evHarmonic;
    public float dpsMeter;

    private final Random rnd = new Random(20240508L);
    private Array<Waves.Spawn> queue = new Array<Waves.Spawn>();
    private int nextTowerId = 1;
    /** Узлы переиспользуются: их до сотен в кадре, мусорить нельзя. */
    private final ResNode[] nodePool = new ResNode[MAX_NODES];

    public World() {
        for (int i = 0; i < nodePool.length; i++) nodePool[i] = new ResNode();
        for (int i = 0; i < towerAt.length; i++) towerAt[i] = -1;
        for (int i = 0; i < ROCKS.length; i += 2) {
            int gi = grid.idx(ROCKS[i], ROCKS[i + 1]);
            rock[gi] = true;
            grid.blocked[gi] = true;
        }
        grid.rebuild();
    }

    // ================================================================ ход игры

    public void update(float dt) {
        if (dt > 0.05f) dt = 0.05f;   // защита от «прыжков» после сворачивания
        time += dt;

        float half = Config.BEAT * 0.5f;
        halfTimer += dt;
        while (halfTimer >= half) {
            halfTimer -= half;
            halfIndex++;
            onHalfBeat();
        }
        beatPhase = halfTimer / half;

        if (state == ST_PREP) {
            prepLeft -= dt;
            if (prepLeft <= 0f) startWave();
        } else if (state == ST_FIGHT) {
            waveTime += dt;
            while (spawned < queue.size && queue.get(spawned).time <= waveTime) {
                Waves.Spawn s = queue.get(spawned);
                spawnEnemy(s.type, s.portal);
                spawned++;
            }
            if (spawned >= queue.size && enemies.size == 0) endWave();
        }

        updatePulses(dt);
        collectNodes();
        applyResonance(dt);
        updateEnemies(dt);
        updateParticles(dt);

        if (shake > 0f) shake = Math.max(0f, shake - dt * 2.6f);
    }

    private void onHalfBeat() {
        evBeat++;
        for (int i = 0; i < towers.size; i++) {
            Tower t = towers.get(i);
            if (t.firesAt(halfIndex)) {
                Pulse p = obtainPulse();
                p.init(t, t.id);
                pulses.add(p);
                t.flash = 1f;
            }
        }
    }

    private void startWave() {
        wave++;
        if (wave > Config.WAVES_TOTAL) wave = Config.WAVES_TOTAL;
        queue = Waves.build(wave, rnd);
        waveCount = queue.size;
        spawned = 0;
        waveTime = 0f;
        state = ST_FIGHT;
    }

    private void endWave() {
        if (wave >= Config.WAVES_TOTAL) {
            state = ST_WON;
            return;
        }
        energy += 12 + wave * 2;
        state = ST_PREP;
        prepLeft = Config.PREP_TIME;
    }

    /** Досрочный вызов волны — за это платят энергией. */
    public void callWaveNow() {
        if (state != ST_PREP) return;
        energy += (int) (prepLeft * Config.EARLY_BONUS);
        prepLeft = 0f;
        startWave();
    }

    // ============================================================== стройка

    public static final int BUILD_OK = 0;
    public static final int BUILD_OCCUPIED = 1;
    public static final int BUILD_MONEY = 2;
    public static final int BUILD_PATH = 3;
    public static final int BUILD_ENEMY = 4;
    public static final int BUILD_POWER = 5;

    /** Занятая мощность сети. */
    public int powerUsed() {
        int p = 0;
        for (int i = 0; i < towers.size; i++) p += towers.get(i).power();
        return p;
    }

    public int powerCap() {
        return Config.START_POWER + Config.POWER_PER_WAVE * wave + Config.POWER_PER_NODE * gensBought;
    }

    /** Цена следующего генератора — растёт, так что «купить всё» не выйдет. */
    public int genCost() {
        return (int) (Config.GEN_BASE_COST * Math.pow(Config.GEN_COST_GROWTH, gensBought));
    }

    /** Купить +мощность сети. Главный способ во что-то вложить лишнюю энергию. */
    public boolean buyGenerator() {
        int c = genCost();
        if (energy < c) return false;
        energy -= c;
        gensBought++;
        return true;
    }

    public int canBuild(int type, int gx, int gy) {
        if (!grid.in(gx, gy)) return BUILD_OCCUPIED;
        int gi = grid.idx(gx, gy);
        if (grid.blocked[gi]) return BUILD_OCCUPIED;
        if (gx == Config.CORE_X && gy == Config.CORE_Y) return BUILD_OCCUPIED;
        for (int i = 0; i < Config.PORTAL_X.length; i++) {
            if (Config.PORTAL_X[i] == gx && Config.PORTAL_Y[i] == gy) return BUILD_OCCUPIED;
        }
        if (energy < Config.T_COST[type]) return BUILD_MONEY;
        if (powerUsed() + Config.T_POWER[type] > powerCap()) return BUILD_POWER;
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if ((int) e.x == gx && (int) e.y == gy) return BUILD_ENEMY;
        }
        // проверяем, что маршрут не перекрыт полностью
        grid.blocked[gi] = true;
        grid.rebuild();
        boolean ok = pathsIntact();
        grid.blocked[gi] = false;
        grid.rebuild();
        return ok ? BUILD_OK : BUILD_PATH;
    }

    private boolean pathsIntact() {
        for (int i = 0; i < Config.PORTAL_X.length; i++) {
            if (!grid.reachable(Config.PORTAL_X[i], Config.PORTAL_Y[i])) return false;
        }
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (!grid.reachable((int) e.x, (int) e.y)) return false;
        }
        return true;
    }

    public Tower build(int type, int gx, int gy) {
        if (canBuild(type, gx, gy) != BUILD_OK) return null;
        Tower t = new Tower(type, gx, gy);
        t.id = nextTowerId++;
        towers.add(t);
        int gi = grid.idx(gx, gy);
        towerAt[gi] = towers.size - 1;
        grid.blocked[gi] = true;
        grid.rebuild();
        energy -= Config.T_COST[type];
        evBuild++;
        burst(gx + 0.5f, gy + 0.5f, Config.T_COLOR[type], 12, 2.6f);
        return t;
    }

    public Tower towerAt(int gx, int gy) {
        if (!grid.in(gx, gy)) return null;
        int i = towerAt[grid.idx(gx, gy)];
        return i < 0 ? null : towers.get(i);
    }

    public boolean upgrade(Tower t) {
        if (t == null || t.level >= Config.MAX_LEVEL || !t.emits()) return false;
        int cost = t.upgradeCost();
        if (energy < cost) return false;
        if (powerUsed() + Config.UPGRADE_POWER > powerCap()) return false;
        energy -= cost;
        t.invested += cost;
        t.level++;
        t.recompute();
        burst(t.gx + 0.5f, t.gy + 0.5f, Config.T_COLOR[t.type], 16, 3.4f);
        return true;
    }

    public boolean togglePhase(Tower t) {
        if (t == null || !t.emits()) return false;
        if (energy < Config.PHASE_COST) return false;
        energy -= Config.PHASE_COST;
        t.phase = 1 - t.phase;
        burst(t.gx + 0.5f, t.gy + 0.5f, Config.NODE_C, 10, 2.2f);
        return true;
    }

    public boolean sell(Tower t) {
        if (t == null) return false;
        int gi = grid.idx(t.gx, t.gy);
        energy += t.sellValue();
        towers.removeValue(t, true);
        // индексы в towerAt сдвинулись — пересобираем карту целиком
        for (int i = 0; i < towerAt.length; i++) towerAt[i] = -1;
        for (int i = 0; i < towers.size; i++) {
            Tower o = towers.get(i);
            towerAt[grid.idx(o.gx, o.gy)] = i;
        }
        grid.blocked[gi] = rock[gi];
        grid.rebuild();
        // фронты проданной башни гасим, чтобы не били «из ниоткуда»
        for (int i = pulses.size - 1; i >= 0; i--) {
            if (pulses.get(i).ownerId == t.id) freePulse(i);
        }
        burst(t.gx + 0.5f, t.gy + 0.5f, Config.TEXT_DIM, 10, 2.2f);
        return true;
    }

    // ============================================================== резонанс

    private void updatePulses(float dt) {
        for (int i = pulses.size - 1; i >= 0; i--) {
            Pulse p = pulses.get(i);
            p.r += p.speed * dt;
            if (p.r >= p.maxR) freePulse(i);
        }
        for (int i = 0; i < towers.size; i++) {
            Tower t = towers.get(i);
            if (t.flash > 0f) t.flash = Math.max(0f, t.flash - dt * 3.2f);
            t.spin += dt * (0.6f + 0.2f * t.level);
        }
    }

    /** Геометрия: пересечения всех пар фронтов от разных башен. */
    private void collectNodes() {
        nodes.clear();
        int n = pulses.size;
        for (int i = 0; i < n && nodes.size < MAX_NODES; i++) {
            Pulse a = pulses.get(i);
            for (int j = i + 1; j < n && nodes.size < MAX_NODES; j++) {
                Pulse b = pulses.get(j);
                if (a.ownerId == b.ownerId) continue;      // сам с собой не резонирует
                float dx = b.x - a.x, dy = b.y - a.y;
                float d2 = dx * dx + dy * dy;
                if (d2 < 1e-6f) continue;
                float rs = a.r + b.r;
                if (d2 > rs * rs) continue;              // кольца ещё не встретились
                float rd = a.r - b.r;
                if (d2 < rd * rd) continue;              // одно внутри другого
                float d = (float) Math.sqrt(d2);

                float t = (a.r * a.r - b.r * b.r + d2) / (2f * d);
                float h2 = a.r * a.r - t * t;
                if (h2 < 0f) continue;
                float h = (float) Math.sqrt(h2);
                float mx = a.x + t * dx / d;
                float my = a.y + t * dy / d;
                float ox = -dy * (h / d), oy = dx * (h / d);

                addNode(mx + ox, my + oy, a, b);
                if (h > 1e-4f) addNode(mx - ox, my - oy, a, b);
            }
        }
        // порядок узла: сколько узлов слиплось в одной точке
        float hr2 = Config.HARMONIC_RADIUS * Config.HARMONIC_RADIUS;
        for (int i = 0; i < nodes.size; i++) {
            ResNode a = nodes.get(i);
            a.order = 1;
        }
        for (int i = 0; i < nodes.size; i++) {
            ResNode a = nodes.get(i);
            for (int j = i + 1; j < nodes.size; j++) {
                ResNode b = nodes.get(j);
                float dx = a.x - b.x, dy = a.y - b.y;
                if (dx * dx + dy * dy <= hr2) {
                    a.order++;
                    b.order++;
                }
            }
        }
    }

    private void addNode(float x, float y, Pulse a, Pulse b) {
        if (nodes.size >= MAX_NODES) return;
        if (x < -1f || y < -1f || x > Config.COLS + 1 || y > Config.ROWS + 1) return;
        ResNode nd = nodePool[nodes.size];
        nd.x = x;
        nd.y = y;
        nd.energy = (a.energy + b.energy) * 0.5f;
        nd.typeA = a.type;
        nd.typeB = b.type;
        nodes.add(nd);
    }

    /**
     * Урон считаем от врага, а не от узла: берём самый сильный узел целиком,
     * остальные — с сильно убывающей отдачей. Иначе «завалить поле башнями»
     * давало бы квадратичный рост урона и убивало бы всю тактику.
     */
    private void applyResonance(float dt) {
        float dealt = 0f;
        float r2 = Config.NODE_RADIUS * Config.NODE_RADIUS;
        for (int j = enemies.size - 1; j >= 0; j--) {
            if (j >= enemies.size) continue;
            Enemy e = enemies.get(j);
            if (!e.alive || !e.vulnerable()) continue;

            float best = 0f, sum = 0f;
            for (int i = 0; i < nodes.size; i++) {
                ResNode nd = nodes.get(i);
                float dx = e.x - nd.x, dy = e.y - nd.y;
                if (dx * dx + dy * dy > r2) continue;
                float mult = 1f + Config.HARMONIC_BONUS * (nd.order - 1);
                float d = Config.NODE_DPS * nd.energy * mult;
                if (e.armored && nd.order < Config.HARMONIC_PIERCE) d *= Config.ARMOR_RESIST;
                sum += d;
                if (d > best) best = d;
            }
            if (best <= 0f) continue;

            float dmg = (best + Config.STACK_FALLOFF * (sum - best)) * dt;
            e.hp -= dmg;
            dealt += dmg;
            e.hitFlash = Math.min(1f, e.hitFlash + dmg * 0.02f);
            if (e.hp <= 0f) killEnemy(j);
        }
        for (int i = 0; i < nodes.size; i++) {
            if (nodes.get(i).order >= Config.HARMONIC_PIERCE) {
                evHarmonic++;
                break;
            }
        }
        dpsMeter = dpsMeter * 0.9f + (dt > 0 ? dealt / dt : 0f) * 0.1f;
    }

    // ================================================================ враги

    private void spawnEnemy(int type, int portal) {
        int px = Config.PORTAL_X[portal % Config.PORTAL_X.length];
        int py = Config.PORTAL_Y[portal % Config.PORTAL_Y.length];
        Enemy e = obtainEnemy();
        e.init(type, px + 0.5f, py + 0.5f, Waves.hpScale(wave), rnd.nextFloat(), rnd.nextFloat());
        enemies.add(e);
    }

    /** Отдельный вход для «искр» из роя (и для тестов). */
    public void spawnAt(int type, float x, float y) {
        Enemy e = obtainEnemy();
        e.init(type, x, y, Waves.hpScale(wave), rnd.nextFloat(), rnd.nextFloat());
        enemies.add(e);
    }

    private void updateEnemies(float dt) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (e.type == Config.E_PHANTOM) {
                e.phaseOut = (((halfIndex + e.parity) & 1) == 1) ? 1f : 0f;
            }
            if (e.hitFlash > 0f) e.hitFlash = Math.max(0f, e.hitFlash - dt * 2.5f);

            int cx = clampI((int) e.x, 0, Config.COLS - 1);
            int cy = clampI((int) e.y, 0, Config.ROWS - 1);
            int ci = grid.idx(cx, cy);

            if (cx == Config.CORE_X && cy == Config.CORE_Y) {
                integrity -= e.mass;
                leaked++;
                evLeak++;
                shake = Math.min(1.4f, shake + 0.5f);
                burst(e.x, e.y, Config.BAD_C, 18, 3.6f);
                freeEnemy(i);
                if (integrity <= 0) {
                    integrity = 0;
                    state = ST_LOST;
                }
                continue;
            }

            int nf = grid.flow[ci];
            float budget = e.speed * dt;
            if (nf < 0) {
                // некуда идти — топчемся в центре клетки
                e.x = approach(e.x, cx + 0.5f, budget);
                e.y = approach(e.y, cy + 0.5f, budget);
                continue;
            }
            int nx = nf % Config.COLS, ny = nf / Config.COLS;
            float goalX = nx + 0.5f + e.jx;
            float goalY = ny + 0.5f + e.jy;

            if (nx != cx) {
                // сначала выравниваемся по вертикали, потом идём вбок
                float alignY = cy + 0.5f + e.jy;
                float need = Math.abs(e.y - alignY);
                float use = Math.min(budget, need);
                e.y = approach(e.y, alignY, use);
                budget -= use;
                e.x = approach(e.x, goalX, budget);
            } else {
                float alignX = cx + 0.5f + e.jx;
                float need = Math.abs(e.x - alignX);
                float use = Math.min(budget, need);
                e.x = approach(e.x, alignX, use);
                budget -= use;
                e.y = approach(e.y, goalY, budget);
            }
        }
    }

    private void killEnemy(int index) {
        Enemy e = enemies.get(index);
        energy += e.bounty;
        killed++;
        evKill++;
        burst(e.x, e.y, Config.E_COLOR[e.type], e.type == Config.E_ARMOR ? 24 : 12, 3.2f);
        int type = e.type;
        float ex = e.x, ey = e.y;
        freeEnemy(index);
        if (type == Config.E_SWARM) {
            for (int k = 0; k < 3; k++) {
                spawnAt(Config.E_MOTE,
                        ex + (rnd.nextFloat() - 0.5f) * 0.4f,
                        ey + (rnd.nextFloat() - 0.5f) * 0.4f);
            }
        }
    }

    // ============================================================== частицы

    public void burst(float x, float y, Color c, int count, float speed) {
        for (int i = 0; i < count; i++) {
            float a = rnd.nextFloat() * 6.2831855f;
            float s = speed * (0.25f + rnd.nextFloat() * 0.75f);
            Particle p = obtainParticle();
            p.init(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    0.35f + rnd.nextFloat() * 0.5f, 0.05f + rnd.nextFloat() * 0.06f, c);
            particles.add(p);
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                p.alive = false;
                particlePool.add(p);
                particles.removeIndex(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 1f - 2.6f * dt;
            p.vy *= 1f - 2.6f * dt;
        }
    }

    // ================================================================= пулы

    private Enemy obtainEnemy() {
        return enemyPool.size > 0 ? enemyPool.pop() : new Enemy();
    }

    private void freeEnemy(int i) {
        Enemy e = enemies.removeIndex(i);
        e.alive = false;
        enemyPool.add(e);
    }

    private Pulse obtainPulse() {
        return pulsePool.size > 0 ? pulsePool.pop() : new Pulse();
    }

    private void freePulse(int i) {
        Pulse p = pulses.removeIndex(i);
        p.alive = false;
        pulsePool.add(p);
    }

    private Particle obtainParticle() {
        return particlePool.size > 0 ? particlePool.pop() : new Particle();
    }

    // ============================================================== мелочи

    public boolean isRock(int gx, int gy) {
        return grid.in(gx, gy) && rock[grid.idx(gx, gy)];
    }

    public int enemiesLeft() {
        return (waveCount - spawned) + enemies.size;
    }

    private static float approach(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }

    private static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
