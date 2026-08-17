extends Node3D

# Сцена целиком собирается кодом: арена, свет, камера и интерфейс.
# Бой считает Battle, здесь только показ.

const BG := Color("#080a14")
const PANEL := Color(0.06, 0.07, 0.12, 0.86)
const TEXT := Color("#eef1f8")
const DIM := Color("#98a1bb")
const FAINT := Color("#5d6580")
const HP_GOOD := Color("#54d17b")
const HP_MID := Color("#e8c44a")
const HP_LOW := Color("#e5534a")

enum Screen { SELECT, BATTLE, RESULT }

var _screen: Screen = Screen.SELECT
var _roster: Array = []
var _index: int = 0

var _camera: Camera3D
var _ui_root: Control
var _stage: Node3D              # сюда попадают фигуры
var _key_light: OmniLight3D
var _fill_light: OmniLight3D

var _preview: Figure
var _battle: Battle
var _figs: Array = [null, null]   # 0 — игрок, 1 — противник
var _busy: bool = false

# Ссылки на элементы боевого интерфейса
var _hp_fill: Array = [null, null]
var _hp_text: Array = [null, null]
var _energy_dots: Array = []
var _log_label: Label
var _ability_buttons: Array = []
var _flash: ColorRect


func _ready() -> void:
	_roster = Roster.all()
	_build_world()
	_build_ui_layer()
	_show_select()


# ------------------------------------------------------------------
# Мир
# ------------------------------------------------------------------

func _build_world() -> void:
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = BG
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color("#2b3155")
	env.ambient_light_energy = 0.75
	env.fog_enabled = true
	env.fog_light_color = Color("#0c1024")
	env.fog_density = 0.018
	env.glow_enabled = true
	env.glow_intensity = 0.7
	env.glow_bloom = 0.15
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)

	_camera = Camera3D.new()
	_camera.fov = 58.0
	add_child(_camera)

	var sun := DirectionalLight3D.new()
	sun.light_energy = 0.75
	sun.light_color = Color("#cdd7ff")
	sun.rotation_degrees = Vector3(-52, 38, 0)
	add_child(sun)

	_key_light = OmniLight3D.new()
	_key_light.light_energy = 2.4
	_key_light.omni_range = 9.0
	_key_light.position = Vector3(-2.6, 2.4, 1.6)
	add_child(_key_light)

	_fill_light = OmniLight3D.new()
	_fill_light.light_energy = 2.4
	_fill_light.omni_range = 9.0
	_fill_light.position = Vector3(2.6, 2.4, 1.6)
	add_child(_fill_light)

	_stage = Node3D.new()
	add_child(_stage)

	_build_arena()


func _build_arena() -> void:
	var floor_mi := MeshInstance3D.new()
	var disc := CylinderMesh.new()
	disc.top_radius = 4.6
	disc.bottom_radius = 4.9
	disc.height = 0.35
	floor_mi.mesh = disc
	var fm := StandardMaterial3D.new()
	fm.albedo_color = Color("#171c30")
	fm.metallic = 0.35
	fm.roughness = 0.55
	floor_mi.material_override = fm
	floor_mi.position = Vector3(0, -0.175, 0)
	add_child(floor_mi)

	var ring := MeshInstance3D.new()
	var torus := TorusMesh.new()
	torus.inner_radius = 4.45
	torus.outer_radius = 4.62
	ring.mesh = torus
	var rm := StandardMaterial3D.new()
	rm.albedo_color = Color("#6ac4ff")
	rm.emission_enabled = true
	rm.emission = Color("#6ac4ff")
	rm.emission_energy_multiplier = 2.2
	ring.material_override = rm
	ring.position = Vector3(0, 0.02, 0)
	add_child(ring)

	# Колонны по кругу — дают ощущение объёма и масштаба
	for i in 8:
		var a: float = TAU * float(i) / 8.0
		var pillar := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.45, 3.4, 0.45)
		pillar.mesh = bm
		var pm := StandardMaterial3D.new()
		pm.albedo_color = Color("#10142a")
		pm.metallic = 0.5
		pm.roughness = 0.45
		pillar.material_override = pm
		pillar.position = Vector3(cos(a) * 6.4, 1.7, sin(a) * 6.4)
		pillar.rotation_degrees = Vector3(0, rad_to_deg(-a), 0)
		add_child(pillar)


func _tint_lights(left: Color, right: Color) -> void:
	_key_light.light_color = left
	_fill_light.light_color = right


var _cam_look: Vector3 = Vector3.ZERO


func _cam_to(p: Vector3) -> void:
	_camera.position = p
	if not p.is_equal_approx(_cam_look):
		_camera.look_at(_cam_look, Vector3.UP)


func _move_camera(pos: Vector3, look_at_point: Vector3, time: float = 0.7) -> void:
	_cam_look = look_at_point
	if time <= 0.0:
		_cam_to(pos)
		return
	var tw := create_tween()
	tw.tween_method(_cam_to, _camera.position, pos, time) \
		.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN_OUT)


func _clear_figures() -> void:
	for c in _stage.get_children():
		c.queue_free()
	_preview = null
	_figs = [null, null]


# ------------------------------------------------------------------
# Интерфейс: общие детали
# ------------------------------------------------------------------

func _build_ui_layer() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	_ui_root = Control.new()
	_ui_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	_ui_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(_ui_root)

	_flash = ColorRect.new()
	_flash.set_anchors_preset(Control.PRESET_FULL_RECT)
	_flash.color = Color(1, 1, 1, 0)
	_flash.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(_flash)


func _clear_ui() -> void:
	for c in _ui_root.get_children():
		c.queue_free()
	_hp_fill = [null, null]
	_hp_text = [null, null]
	_energy_dots = []
	_ability_buttons = []
	_log_label = null


# Ставит контрол так, чтобы его левый верхний угол был в точке
# anchor (доля экрана) плюс offset в пикселях.
func _place(c: Control, anchor: Vector2, offset: Vector2, size: Vector2) -> void:
	c.anchor_left = anchor.x
	c.anchor_top = anchor.y
	c.anchor_right = anchor.x
	c.anchor_bottom = anchor.y
	c.offset_left = offset.x
	c.offset_top = offset.y
	c.offset_right = offset.x + size.x
	c.offset_bottom = offset.y + size.y


func _label(parent: Control, text: String, size: int, color: Color,
		align: int = HORIZONTAL_ALIGNMENT_LEFT) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	l.horizontal_alignment = align
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(l)
	return l


func _rect(parent: Control, color: Color) -> ColorRect:
	var r := ColorRect.new()
	r.color = color
	r.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(r)
	return r


func _panel_style(bg: Color, border: Color, width: int = 2) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = bg
	sb.border_color = border
	sb.border_width_left = width
	sb.border_width_top = width
	sb.border_width_right = width
	sb.border_width_bottom = width
	sb.corner_radius_top_left = 10
	sb.corner_radius_top_right = 10
	sb.corner_radius_bottom_left = 10
	sb.corner_radius_bottom_right = 10
	sb.content_margin_left = 12
	sb.content_margin_right = 12
	sb.content_margin_top = 8
	sb.content_margin_bottom = 8
	return sb


func _panel(parent: Control, accent: Color) -> Panel:
	var p := Panel.new()
	p.add_theme_stylebox_override("panel", _panel_style(PANEL, accent))
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(p)
	return p


func _button(parent: Control, text: String, accent: Color, on_press: Callable,
		font_size: int = 22) -> Button:
	var b := Button.new()
	b.text = text
	b.add_theme_font_size_override("font_size", font_size)
	b.add_theme_color_override("font_color", TEXT)
	b.add_theme_color_override("font_hover_color", Color.WHITE)
	b.add_theme_color_override("font_disabled_color", FAINT)
	b.add_theme_stylebox_override("normal", _panel_style(Color(0.10, 0.12, 0.19, 0.95), accent))
	b.add_theme_stylebox_override("hover", _panel_style(accent.darkened(0.55), accent, 3))
	b.add_theme_stylebox_override("pressed", _panel_style(accent.darkened(0.35), accent, 3))
	b.add_theme_stylebox_override("disabled", _panel_style(Color(0.08, 0.09, 0.13, 0.8), Color(0.2, 0.22, 0.3)))
	b.pressed.connect(on_press)
	parent.add_child(b)
	return b


func _flash_screen(color: Color, peak: float, time: float) -> void:
	_flash.color = Color(color.r, color.g, color.b, 0.0)
	var tw := create_tween()
	tw.tween_property(_flash, "color:a", peak, time * 0.25)
	tw.tween_property(_flash, "color:a", 0.0, time * 0.75)


# ------------------------------------------------------------------
# Экран выбора
# ------------------------------------------------------------------

func _show_select() -> void:
	_screen = Screen.SELECT
	_clear_ui()
	_clear_figures()
	_move_camera(Vector3(0, 1.85, 4.3), Vector3(0, 1.15, 0), 0.0)
	_spawn_preview()

	var def: Roster.CharDef = _roster[_index]

	var title := _label(_ui_root, "ВЫБЕРИ БОЙЦА", 34, TEXT, HORIZONTAL_ALIGNMENT_CENTER)
	_place(title, Vector2(0.5, 0.0), Vector2(-300, 16), Vector2(600, 44))
	var sub := _label(_ui_root, "%d из %d" % [_index + 1, _roster.size()], 18, FAINT, HORIZONTAL_ALIGNMENT_CENTER)
	_place(sub, Vector2(0.5, 0.0), Vector2(-100, 52), Vector2(200, 24))

	# Стрелки
	var left := _button(_ui_root, "‹", def.primary, func(): _cycle(-1), 40)
	_place(left, Vector2(0.0, 0.5), Vector2(26, -34), Vector2(68, 68))
	var right := _button(_ui_root, "›", def.primary, func(): _cycle(1), 40)
	_place(right, Vector2(1.0, 0.5), Vector2(-94, -34), Vector2(68, 68))

	# Карточка с описанием
	var card := _panel(_ui_root, def.primary)
	_place(card, Vector2(0.5, 1.0), Vector2(-370, -268), Vector2(740, 200))

	var name_l := _label(_ui_root, def.name, 40, TEXT, HORIZONTAL_ALIGNMENT_CENTER)
	_place(name_l, Vector2(0.5, 1.0), Vector2(-370, -258), Vector2(740, 46))

	var uni := _label(_ui_root, "%s · %s" % [def.universe, def.tagline], 18, def.primary, HORIZONTAL_ALIGNMENT_CENTER)
	_place(uni, Vector2(0.5, 1.0), Vector2(-370, -214), Vector2(740, 24))

	var stats := _label(_ui_root, "ЗДОРОВЬЕ %d    АТАКА %d    ЗАЩИТА %d    СКОРОСТЬ %d"
		% [def.max_hp, def.attack, def.defense, def.speed], 18, DIM, HORIZONTAL_ALIGNMENT_CENTER)
	_place(stats, Vector2(0.5, 1.0), Vector2(-370, -184), Vector2(740, 24))

	# Способности
	var col: int = 0
	for a in def.abilities:
		var x: float = -358.0 + float(col % 2) * 366.0
		var y: float = -150.0 + float(col / 2) * 34.0
		var cost_text: String = "бесплатно" if a.cost == 0 else "%d эн." % a.cost
		var al := _label(_ui_root, "%s — %s (%s)" % [a.title, a.desc, cost_text], 15, DIM)
		_place(al, Vector2(0.5, 1.0), Vector2(x, y), Vector2(360, 22))
		col += 1

	var go := _button(_ui_root, "В БОЙ", def.secondary, func(): _start_battle(), 26)
	_place(go, Vector2(0.5, 1.0), Vector2(-130, -56), Vector2(260, 46))

	var foe: Roster.CharDef = Roster.opponent_for(def.id)
	var hint := _label(_ui_root, "против: %s" % foe.name, 16, FAINT, HORIZONTAL_ALIGNMENT_CENTER)
	_place(hint, Vector2(0.5, 1.0), Vector2(-130, -84), Vector2(260, 22))

	_tint_lights(def.primary, def.secondary)


func _spawn_preview() -> void:
	var def: Roster.CharDef = _roster[_index]
	_preview = Figure.create(def)
	_preview.spin = true
	_preview.position = Vector3(0, 0, 0)
	_stage.add_child(_preview)
	_preview.settle()


func _cycle(step: int) -> void:
	_index = wrapi(_index + step, 0, _roster.size())
	_show_select()


# ------------------------------------------------------------------
# Бой
# ------------------------------------------------------------------

func _start_battle() -> void:
	var mine: Roster.CharDef = _roster[_index]
	var foe: Roster.CharDef = Roster.opponent_for(mine.id)
	_battle = Battle.new(mine, foe)
	_show_battle()


func _show_battle() -> void:
	_screen = Screen.BATTLE
	_clear_ui()
	_clear_figures()
	_busy = false

	_move_camera(Vector3(0, 2.7, 6.4), Vector3(0, 1.25, 0), 0.0)

	var p := Figure.create(_battle.player.def)
	p.position = Vector3(-1.95, 0, 0)
	p.rotation_degrees.y = 90.0
	p.facing = 1.0
	_stage.add_child(p)
	p.settle()

	var e := Figure.create(_battle.enemy.def)
	e.position = Vector3(1.95, 0, 0)
	e.rotation_degrees.y = -90.0
	e.facing = -1.0
	_stage.add_child(e)
	e.settle()

	_figs = [p, e]
	_tint_lights(_battle.player.def.primary, _battle.enemy.def.primary)

	_build_battle_ui()
	_refresh_battle_ui()


func _build_battle_ui() -> void:
	# Полосы здоровья
	_hp_fill[0] = _make_hp_bar(_battle.player.def, Vector2(0.0, 0.0), Vector2(24, 22), false, 0)
	_hp_fill[1] = _make_hp_bar(_battle.enemy.def, Vector2(1.0, 0.0), Vector2(-404, 22), true, 1)

	# Энергия игрока
	for i in Battle.MAX_ENERGY:
		var dot := _rect(_ui_root, FAINT)
		_place(dot, Vector2(0.0, 0.0), Vector2(26 + i * 22, 92), Vector2(14, 14))
		_energy_dots.append(dot)

	# Журнал
	_log_label = _label(_ui_root, "", 21, TEXT, HORIZONTAL_ALIGNMENT_CENTER)
	_place(_log_label, Vector2(0.5, 0.0), Vector2(-380, 120), Vector2(760, 30))

	# Кнопки способностей
	var abilities: Array = _battle.player.def.abilities
	var count: int = abilities.size()
	var bw: float = 224.0
	var gap: float = 12.0
	var total: float = float(count) * bw + float(count - 1) * gap
	for i in count:
		var a: Roster.Ability = abilities[i]
		var x: float = -total * 0.5 + float(i) * (bw + gap)
		var cost_text: String = "" if a.cost == 0 else "  ·  %d эн." % a.cost
		var ability_id: String = a.id
		var b := _button(_ui_root, a.title + cost_text, _battle.player.def.primary,
			func(): _use(ability_id), 20)
		b.tooltip_text = a.desc
		_place(b, Vector2(0.5, 1.0), Vector2(x, -84), Vector2(bw, 56))
		_ability_buttons.append({"button": b, "ability": a})

	var tip := _label(_ui_root, "", 15, FAINT, HORIZONTAL_ALIGNMENT_CENTER)
	_place(tip, Vector2(0.5, 1.0), Vector2(-400, -24), Vector2(800, 20))
	tip.text = "Способности тратят энергию. Каждый раунд она восстанавливается."


func _make_hp_bar(def: Roster.CharDef, anchor: Vector2, offset: Vector2,
		mirrored: bool, side: int) -> ColorRect:
	var width: float = 380.0
	var name_l := _label(_ui_root, def.name, 24, TEXT,
		HORIZONTAL_ALIGNMENT_RIGHT if mirrored else HORIZONTAL_ALIGNMENT_LEFT)
	_place(name_l, anchor, offset + Vector2(2, 0), Vector2(width, 28))

	var uni := _label(_ui_root, def.universe, 14, def.primary,
		HORIZONTAL_ALIGNMENT_RIGHT if mirrored else HORIZONTAL_ALIGNMENT_LEFT)
	_place(uni, anchor, offset + Vector2(2, 28), Vector2(width, 18))

	var back := _rect(_ui_root, Color(0.09, 0.10, 0.16, 0.9))
	_place(back, anchor, offset + Vector2(0, 50), Vector2(width, 16))

	var fill := _rect(_ui_root, HP_GOOD)
	_place(fill, anchor, offset + Vector2(0, 50), Vector2(width, 16))
	fill.set_meta("full_width", width)
	fill.set_meta("anchor", anchor)
	fill.set_meta("offset", offset + Vector2(0, 50))
	fill.set_meta("mirrored", mirrored)

	var hp_l := _label(_ui_root, "", 16, DIM,
		HORIZONTAL_ALIGNMENT_RIGHT if mirrored else HORIZONTAL_ALIGNMENT_LEFT)
	_place(hp_l, anchor, offset + Vector2(2, 68), Vector2(width, 20))
	_hp_text[side] = hp_l

	return fill


func _refresh_battle_ui() -> void:
	for side in 2:
		var f: Battle.Fighter = _battle.fighter(side)
		var fill: ColorRect = _hp_fill[side]
		if fill == null:
			continue
		var full: float = fill.get_meta("full_width")
		var frac: float = clampf(f.hp_fraction, 0.0, 1.0)
		var w: float = full * frac
		var anchor: Vector2 = fill.get_meta("anchor")
		var off: Vector2 = fill.get_meta("offset")
		# У противника полоса убывает справа налево
		if fill.get_meta("mirrored"):
			_place(fill, anchor, off + Vector2(full - w, 0), Vector2(w, 16))
		else:
			_place(fill, anchor, off, Vector2(w, 16))
		fill.color = HP_GOOD if frac > 0.55 else (HP_MID if frac > 0.25 else HP_LOW)
		_hp_text[side].text = "%d / %d" % [f.hp, f.def.max_hp]

	for i in _energy_dots.size():
		var dot: ColorRect = _energy_dots[i]
		dot.color = Color("#62c4ff") if i < _battle.player.energy else Color(0.22, 0.25, 0.34)

	for entry in _ability_buttons:
		var a: Roster.Ability = entry["ability"]
		var b: Button = entry["button"]
		b.disabled = _busy or _battle.over or not _battle.player.can_pay(a.cost)


func _use(ability_id: String) -> void:
	if _busy or _battle.over:
		return
	var events: Array = _battle.use(ability_id)
	_busy = true
	_refresh_battle_ui()
	await _play_events(events)


func _play_events(events: Array) -> void:
	for e in events:
		await _play_event(e)
	_busy = false
	_refresh_battle_ui()
	if _battle.over:
		await get_tree().create_timer(0.7).timeout
		_show_result()


func _fig(side: int) -> Figure:
	return _figs[side]


func _set_log(text: String) -> void:
	if _log_label and text != "":
		_log_label.text = text


func _play_event(e: Battle.Event) -> void:
	var actor: Figure = _fig(e.side)
	var target: Figure = _fig(1 - e.side)

	match e.kind:
		"attack":
			_set_log(e.text)
			var tw := actor.play_attack()
			await get_tree().create_timer(0.14).timeout
			target.play_hit()
			if e.crit:
				_flash_screen(Color("#ffd54a"), 0.35, 0.3)
			_refresh_bars_only()
			await tw.finished

		"miss":
			_set_log(e.text)
			var tw2 := actor.play_attack()
			await tw2.finished

		"heal":
			_set_log(e.text)
			actor.play_heal()
			_refresh_bars_only()
			await get_tree().create_timer(0.5).timeout

		"buff":
			_set_log(e.text)
			actor.play_buff(actor.def.primary)
			await get_tree().create_timer(0.45).timeout

		"multi":
			_set_log(e.text)
			await get_tree().create_timer(0.25).timeout

		"reflect":
			_set_log(e.text)
			target.play_hit()
			_refresh_bars_only()
			await get_tree().create_timer(0.4).timeout

		"stop":
			_set_log(e.text)
			await _time_stop()

		"ko":
			_set_log(e.text)
			await get_tree().create_timer(0.5).timeout

		_:
			pass


# Полосы обновляем по ходу анимации, кнопки — только в конце.
func _refresh_bars_only() -> void:
	for side in 2:
		var f: Battle.Fighter = _battle.fighter(side)
		var fill: ColorRect = _hp_fill[side]
		if fill == null:
			continue
		var full: float = fill.get_meta("full_width")
		var frac: float = clampf(f.hp_fraction, 0.0, 1.0)
		var w: float = full * frac
		var anchor: Vector2 = fill.get_meta("anchor")
		var off: Vector2 = fill.get_meta("offset")
		if fill.get_meta("mirrored"):
			_place(fill, anchor, off + Vector2(full - w, 0), Vector2(w, 16))
		else:
			_place(fill, anchor, off, Vector2(w, 16))
		fill.color = HP_GOOD if frac > 0.55 else (HP_MID if frac > 0.25 else HP_LOW)
		_hp_text[side].text = "%d / %d" % [f.hp, f.def.max_hp]


# Остановка времени: экран заливает золотом, мир глохнет на секунду.
func _time_stop() -> void:
	_flash_screen(Color("#ffd54a"), 0.55, 0.5)
	var big := _label(_ui_root, "ZA WARUDO", 64, Color("#ffd54a"), HORIZONTAL_ALIGNMENT_CENTER)
	_place(big, Vector2(0.5, 0.5), Vector2(-360, -70), Vector2(720, 80))
	big.modulate.a = 0.0
	var tw := create_tween()
	tw.tween_property(big, "modulate:a", 1.0, 0.18)
	tw.tween_interval(0.5)
	tw.tween_property(big, "modulate:a", 0.0, 0.3)
	tw.tween_callback(big.queue_free)
	await tw.finished


# ------------------------------------------------------------------
# Итог
# ------------------------------------------------------------------

func _show_result() -> void:
	_screen = Screen.RESULT
	var won: bool = _battle.player_won
	var accent: Color = HP_GOOD if won else HP_LOW

	var dim := _rect(_ui_root, Color(0.02, 0.03, 0.06, 0.8))
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)

	var card := _panel(_ui_root, accent)
	_place(card, Vector2(0.5, 0.5), Vector2(-260, -150), Vector2(520, 300))

	var title := _label(_ui_root, "ПОБЕДА" if won else "ПОРАЖЕНИЕ", 52, accent, HORIZONTAL_ALIGNMENT_CENTER)
	_place(title, Vector2(0.5, 0.5), Vector2(-260, -120), Vector2(520, 60))

	var who: String = _battle.player.def.name if won else _battle.enemy.def.name
	var line := _label(_ui_root, "%s выстоял в дуэли" % who, 20, DIM, HORIZONTAL_ALIGNMENT_CENTER)
	_place(line, Vector2(0.5, 0.5), Vector2(-260, -50), Vector2(520, 28))

	var rounds := _label(_ui_root, "раундов: %d" % _battle.round_no, 17, FAINT, HORIZONTAL_ALIGNMENT_CENTER)
	_place(rounds, Vector2(0.5, 0.5), Vector2(-260, -20), Vector2(520, 24))

	var again := _button(_ui_root, "РЕВАНШ", accent, func(): _start_battle(), 22)
	_place(again, Vector2(0.5, 0.5), Vector2(-230, 40), Vector2(210, 50))

	var back := _button(_ui_root, "СМЕНИТЬ БОЙЦА", Color("#62c4ff"), func(): _show_select(), 22)
	_place(back, Vector2(0.5, 0.5), Vector2(20, 40), Vector2(210, 50))
