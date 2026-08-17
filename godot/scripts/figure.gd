class_name Figure
extends Node3D

# Персонаж собирается из примитивов прямо в коде: ни одной готовой модели,
# только коробки и капсулы. Силуэт, цвет и детали задаёт CharDef.

var def: Roster.CharDef
var body: Node3D          # всё, что покачивается при дыхании
var cape: Node3D
var spin: bool = false    # медленное вращение на экране выбора
var facing: float = 1.0   # 1 — смотрит вправо, -1 — влево

var _t: float = 0.0
var _base_pos: Vector3 = Vector3.ZERO
var _shake: float = 0.0


static func create(p_def: Roster.CharDef) -> Figure:
	var f := Figure.new()
	f.def = p_def
	f._build()
	return f


# --- Примитивы ---

func _mat(color: Color, metallic: float = 0.0, glow: float = 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.metallic = metallic
	m.roughness = 0.35 if metallic > 0.4 else 0.62
	if glow > 0.0:
		m.emission_enabled = true
		m.emission = color
		m.emission_energy_multiplier = glow
	return m


func _box(parent: Node3D, size: Vector3, pos: Vector3, color: Color,
		rot_deg: Vector3 = Vector3.ZERO, metallic: float = 0.0, glow: float = 0.0) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var mesh := BoxMesh.new()
	mesh.size = size
	mi.mesh = mesh
	mi.material_override = _mat(color, metallic, glow)
	mi.position = pos
	mi.rotation_degrees = rot_deg
	parent.add_child(mi)
	return mi


func _capsule(parent: Node3D, radius: float, height: float, pos: Vector3, color: Color,
		rot_deg: Vector3 = Vector3.ZERO, metallic: float = 0.0) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var mesh := CapsuleMesh.new()
	mesh.radius = radius
	mesh.height = maxf(height, radius * 2.0 + 0.001)
	mi.mesh = mesh
	mi.material_override = _mat(color, metallic)
	mi.position = pos
	mi.rotation_degrees = rot_deg
	parent.add_child(mi)
	return mi


func _sphere(parent: Node3D, radius: float, pos: Vector3, color: Color,
		metallic: float = 0.0, glow: float = 0.0) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var mesh := SphereMesh.new()
	mesh.radius = radius
	mesh.height = radius * 2.0
	mi.mesh = mesh
	mi.material_override = _mat(color, metallic, glow)
	mi.position = pos
	parent.add_child(mi)
	return mi


# --- Сборка ---

func _build() -> void:
	body = Node3D.new()
	add_child(body)

	var heavy: bool = def.build == "heavy"
	var shoulder: float = 0.32 if heavy else 0.25
	var limb_r: float = 0.105 if heavy else 0.082
	var chest_w: float = shoulder * 2.0
	var depth: float = 0.32 if heavy else 0.25
	var dark := Color(def.primary.r * 0.28, def.primary.g * 0.28, def.primary.b * 0.28)

	# Ноги
	_capsule(body, limb_r, 0.86, Vector3(-shoulder * 0.55, 0.44, 0.0), dark)
	_capsule(body, limb_r, 0.86, Vector3(shoulder * 0.55, 0.44, 0.0), dark)
	# Сапоги
	_box(body, Vector3(limb_r * 2.4, 0.16, depth * 1.1), Vector3(-shoulder * 0.55, 0.08, 0.03), def.secondary, Vector3.ZERO, 0.5)
	_box(body, Vector3(limb_r * 2.4, 0.16, depth * 1.1), Vector3(shoulder * 0.55, 0.08, 0.03), def.secondary, Vector3.ZERO, 0.5)

	# Таз и грудь
	_box(body, Vector3(chest_w * 0.8, 0.26, depth * 0.9), Vector3(0, 0.98, 0), dark)
	_box(body, Vector3(chest_w, 0.46, depth), Vector3(0, 1.32, 0), def.primary)

	# Плечи
	_sphere(body, limb_r * 1.5, Vector3(-chest_w * 0.5, 1.5, 0), def.primary)
	_sphere(body, limb_r * 1.5, Vector3(chest_w * 0.5, 1.5, 0), def.primary)

	# Руки
	_capsule(body, limb_r * 0.9, 0.74, Vector3(-chest_w * 0.55, 1.14, 0.02), dark)
	_capsule(body, limb_r * 0.9, 0.74, Vector3(chest_w * 0.55, 1.14, 0.02), dark)

	# Шея и голова
	_capsule(body, limb_r * 0.7, 0.16, Vector3(0, 1.6, 0), def.skin)
	var head := _box(body, Vector3(0.30, 0.34, 0.30), Vector3(0, 1.83, 0), def.skin)

	# Плащ
	cape = Node3D.new()
	cape.position = Vector3(0, 1.52, -depth * 0.5)
	body.add_child(cape)
	var cape_color: Color = def.secondary if def.id == "dio" else def.primary
	_box(cape, Vector3(chest_w * 1.15, 1.15, 0.05), Vector3(0, -0.55, -0.04), cape_color)

	if def.id == "dio":
		_build_dio(head, chest_w, depth)
	else:
		_build_loki(head, chest_w, depth)


func _build_dio(head: MeshInstance3D, chest_w: float, depth: float) -> void:
	# Зачёсанная назад копна
	_box(head, Vector3(0.33, 0.16, 0.33), Vector3(0, 0.22, 0), def.hair)
	_box(head, Vector3(0.30, 0.20, 0.16), Vector3(0, 0.30, -0.14), def.hair, Vector3(-24, 0, 0))
	_box(head, Vector3(0.22, 0.16, 0.14), Vector3(0, 0.38, -0.26), def.hair, Vector3(-42, 0, 0))
	# Обруч на лбу
	_box(head, Vector3(0.33, 0.07, 0.33), Vector3(0, 0.10, 0), def.secondary, Vector3.ZERO, 0.6)
	_sphere(head, 0.045, Vector3(0, 0.10, 0.16), def.primary, 0.8, 1.4)
	# Глаза
	_sphere(head, 0.028, Vector3(-0.07, 0.02, 0.155), Color("#ff3b2f"), 0.0, 2.2)
	_sphere(head, 0.028, Vector3(0.07, 0.02, 0.155), Color("#ff3b2f"), 0.0, 2.2)
	# Наплечники и знак на груди
	_box(body, Vector3(0.20, 0.12, 0.30), Vector3(-chest_w * 0.56, 1.58, 0), def.secondary, Vector3(0, 0, 16), 0.5)
	_box(body, Vector3(0.20, 0.12, 0.30), Vector3(chest_w * 0.56, 1.58, 0), def.secondary, Vector3(0, 0, -16), 0.5)
	_sphere(body, 0.075, Vector3(0, 1.38, depth * 0.5), def.primary, 0.9, 1.1)


func _build_loki(head: MeshInstance3D, chest_w: float, depth: float) -> void:
	# Шлем
	_box(head, Vector3(0.32, 0.14, 0.32), Vector3(0, 0.20, 0), def.secondary, Vector3.ZERO, 0.8)
	_box(head, Vector3(0.10, 0.16, 0.06), Vector3(0, 0.08, 0.15), def.secondary, Vector3.ZERO, 0.8)
	# Рога: цепочка сегментов, загибающихся вверх и назад
	for side in [-1.0, 1.0]:
		var pos := Vector3(0.11 * side, 0.26, 0.02)
		var ang := 12.0
		for i in 7:
			var seg: float = 0.16 - i * 0.012
			pos += Vector3(0.022 * side, seg * 0.72, -0.020)
			_box(head, Vector3(0.062 - i * 0.005, seg, 0.062 - i * 0.005), pos,
				def.secondary, Vector3(-ang, 0, 9.0 * side), 0.85)
			ang += 6.0
	# Глаза
	_sphere(head, 0.026, Vector3(-0.07, 0.02, 0.155), Color("#7ef2c8"), 0.0, 1.8)
	_sphere(head, 0.026, Vector3(0.07, 0.02, 0.155), Color("#7ef2c8"), 0.0, 1.8)
	# Пластины на груди
	_box(body, Vector3(0.09, 0.40, 0.04), Vector3(-0.10, 1.32, depth * 0.5), def.secondary, Vector3.ZERO, 0.8)
	_box(body, Vector3(0.09, 0.40, 0.04), Vector3(0.10, 1.32, depth * 0.5), def.secondary, Vector3.ZERO, 0.8)
	_box(body, Vector3(0.20, 0.10, 0.05), Vector3(0, 1.52, depth * 0.5), def.secondary, Vector3.ZERO, 0.8)


# --- Жизнь на экране ---

func settle() -> void:
	_base_pos = position


func _process(delta: float) -> void:
	_t += delta
	if body:
		body.position.y = sin(_t * 2.0) * 0.022
		body.rotation_degrees.z = sin(_t * 1.1) * 0.9
	if cape:
		cape.rotation_degrees.x = 6.0 + sin(_t * 1.3) * 4.5
	if spin:
		rotation_degrees.y += delta * 22.0
	if _shake > 0.0:
		_shake = maxf(0.0, _shake - delta * 4.0)
		if _shake == 0.0:
			position = _base_pos
		else:
			position = _base_pos + Vector3(
				randf_range(-1.0, 1.0) * _shake * 0.09,
				0.0,
				randf_range(-1.0, 1.0) * _shake * 0.09
			)


# Выпад в сторону противника.
func play_attack() -> Tween:
	var tw := create_tween()
	var forward := Vector3(facing * 0.85, 0.0, 0.0)
	tw.tween_property(self, "position", _base_pos + forward, 0.13) \
		.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_property(self, "position", _base_pos, 0.24) \
		.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN_OUT)
	return tw


func play_hit() -> void:
	_shake = 1.0
	_burst(Color("#ffd66e"), 10, 1.6)


func play_heal() -> void:
	_burst(Color("#6ef2a0"), 12, 1.1)


func play_buff(color: Color) -> void:
	_burst(color, 14, 1.3)


# Вспышка из разлетающихся искр — дешевле и предсказуемее, чем система частиц.
func _burst(color: Color, count: int, speed: float) -> void:
	for i in count:
		var s := _sphere(self, randf_range(0.035, 0.075), Vector3(0, 1.25, 0), color, 0.0, 2.4)
		var dir := Vector3(
			randf_range(-1.0, 1.0),
			randf_range(-0.2, 1.0),
			randf_range(-1.0, 1.0)
		).normalized()
		var life := randf_range(0.35, 0.6)
		var tw := create_tween()
		tw.set_parallel(true)
		tw.tween_property(s, "position", s.position + dir * speed, life) \
			.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(s, "scale", Vector3.ZERO, life)
		tw.chain().tween_callback(s.queue_free)
