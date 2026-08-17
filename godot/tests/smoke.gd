extends SceneTree

# Прогон боя без графики: ловит ошибки в логике и заодно показывает,
# насколько ровно сходятся бойцы. Запуск:
#   godot --headless --script res://tests/smoke.gd

const RUNS := 400
const MAX_TURNS := 300


func _init() -> void:
	var failures: int = 0
	failures += _check_pair("dio", "loki")
	failures += _check_pair("loki", "dio")
	failures += _check_edge_cases()
	failures += _check_figures()

	if failures == 0:
		print("SMOKE OK")
	else:
		print("SMOKE FAILED: %d" % failures)
	quit(1 if failures > 0 else 0)


func _def(id: String) -> Roster.CharDef:
	for c in Roster.all():
		if c.id == id:
			return c
	return null


func _check_pair(player_id: String, enemy_id: String) -> int:
	var wins: int = 0
	var problems: int = 0
	var total_rounds: int = 0

	for run in RUNS:
		var b := Battle.new(_def(player_id), _def(enemy_id))
		var turns: int = 0

		while not b.over and turns < MAX_TURNS:
			var pick: Roster.Ability = _greedy_choice(b)
			b.use(pick.id)
			turns += 1

			if b.player.hp < 0 or b.enemy.hp < 0:
				print("  ! отрицательное здоровье на ходу %d" % turns)
				problems += 1
				break
			if b.player.energy < 0 or b.enemy.energy < 0:
				print("  ! отрицательная энергия на ходу %d" % turns)
				problems += 1
				break

		if not b.over:
			print("  ! бой не завершился за %d ходов" % MAX_TURNS)
			problems += 1
			continue

		total_rounds += b.round_no
		if b.player_won:
			wins += 1

	var rate: float = 100.0 * float(wins) / float(RUNS)
	var avg: float = float(total_rounds) / float(RUNS)
	print("%s против %s: побед %.1f%%, раундов в среднем %.1f" % [player_id, enemy_id, rate, avg])
	return problems


# Зеркальный матч: за игрока играет тот же ИИ, что и за противника,
# поэтому разница в проценте побед — это разница бойцов, а не стратегий.
func _greedy_choice(b: Battle) -> Roster.Ability:
	return b.choose_for(Battle.SIDE_PLAYER)


func _check_edge_cases() -> int:
	var problems: int = 0

	# Неизвестный приём не должен ломать бой
	var b := Battle.new(_def("dio"), _def("loki"))
	var before: int = b.enemy.hp
	b.use("no_such_ability")
	if b.enemy.hp != before:
		print("  ! неизвестный приём что-то сделал")
		problems += 1

	# Ходы после конца боя игнорируются
	var b2 := Battle.new(_def("dio"), _def("loki"))
	var guard: int = 0
	while not b2.over and guard < MAX_TURNS:
		b2.use("strike")
		guard += 1
	var hp_after: int = b2.player.hp
	var events: Array = b2.use("strike")
	if events.size() != 0 or b2.player.hp != hp_after:
		print("  ! бой продолжается после завершения")
		problems += 1

	# Энергии всегда хватает на базовый удар
	var b3 := Battle.new(_def("loki"), _def("dio"))
	for i in 20:
		if b3.over:
			break
		b3.use("strike")
	if b3.player.energy < 0:
		print("  ! энергия ушла в минус")
		problems += 1

	return problems


# Фигуры собираются кодом, поэтому опечатка в геометрии тихо съедает
# детали. Проверяем, что модель вообще из чего-то состоит.
func _check_figures() -> int:
	var problems: int = 0
	for c in Roster.all():
		var f := Figure.create(c)
		var meshes: int = _count_meshes(f)
		if meshes < 14:
			print("  ! у %s подозрительно мало деталей: %d" % [c.id, meshes])
			problems += 1
		else:
			print("%s: деталей в модели %d" % [c.id, meshes])
		f.free()
	return problems


func _count_meshes(n: Node) -> int:
	var total: int = 1 if n is MeshInstance3D else 0
	for ch in n.get_children():
		total += _count_meshes(ch)
	return total
