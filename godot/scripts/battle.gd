class_name Battle
extends RefCounted

# Пошаговый бой. Логика ничего не знает про 3D: она возвращает список
# событий, а сцена уже проигрывает их анимациями.

const MAX_ENERGY := 6
const START_ENERGY := 2

const SIDE_PLAYER := 0
const SIDE_ENEMY := 1


class Fighter:
	var def: Roster.CharDef
	var hp: int
	var energy: int
	var illusion: bool = false      # следующая входящая атака промахнётся
	var reflect: int = 0            # процент урона, летящий обратно
	var time_stopped: int = 0       # сколько ходов пропустить

	func _init(p_def: Roster.CharDef) -> void:
		def = p_def
		hp = p_def.max_hp
		energy = START_ENERGY

	var alive: bool:
		get: return hp > 0

	var hp_fraction: float:
		get: return float(hp) / float(def.max_hp)

	func can_pay(cost: int) -> bool:
		return energy >= cost


class Event:
	var kind: String        # attack, multi, heal, buff, miss, reflect, stop, ko, round
	var side: int           # кто действует
	var amount: int = 0
	var crit: bool = false
	var text: String = ""

	func _init(p_kind: String, p_side: int, p_text: String = "", p_amount: int = 0) -> void:
		kind = p_kind
		side = p_side
		text = p_text
		amount = p_amount


var player: Fighter
var enemy: Fighter
var over: bool = false
var player_won: bool = false
var round_no: int = 1

var _rng := RandomNumberGenerator.new()
var _events: Array = []


func _init(player_def: Roster.CharDef, enemy_def: Roster.CharDef) -> void:
	player = Fighter.new(player_def)
	enemy = Fighter.new(enemy_def)
	_rng.randomize()


func fighter(side: int) -> Fighter:
	return player if side == SIDE_PLAYER else enemy


func _other(side: int) -> int:
	return SIDE_ENEMY if side == SIDE_PLAYER else SIDE_PLAYER


func _emit(kind: String, side: int, text: String = "", amount: int = 0, crit: bool = false) -> void:
	var e := Event.new(kind, side, text, amount)
	e.crit = crit
	_events.append(e)


# --- Урон ---

func _roll(attacker: Fighter, defender: Fighter, power: float) -> Array:
	var base: float = float(attacker.def.attack) * power
	var spread: float = base * 0.18
	var dmg: float = base - spread + _rng.randf() * spread * 2.0
	# Защита срезает пропорционально силе удара, иначе серия слабых
	# попаданий полностью гасится бронёй.
	dmg -= float(defender.def.defense) * 0.55 * power
	var crit_chance: float = 0.05 + float(attacker.def.speed) * 0.005
	var crit: bool = _rng.randf() < crit_chance
	if crit:
		dmg *= 1.7
	return [maxi(1, int(round(dmg))), crit]


# Наносит урон и разбирается с иллюзиями и подменой.
func _hit(attacker_side: int, power: float, label: String = "") -> void:
	var attacker := fighter(attacker_side)
	var defender_side := _other(attacker_side)
	var defender := fighter(defender_side)

	if defender.illusion:
		defender.illusion = false
		_emit("miss", attacker_side, "%s бьёт по двойнику" % attacker.def.name)
		return

	# Скорость даёт шанс уйти от удара — иначе она почти ни на что не влияет.
	var dodge: float = 0.02 + float(defender.def.speed) * 0.008
	if _rng.randf() < dodge:
		_emit("miss", attacker_side, "%s уклоняется" % defender.def.name)
		return

	var res := _roll(attacker, defender, power)
	var dmg: int = res[0]
	var crit: bool = res[1]
	defender.hp = maxi(0, defender.hp - dmg)

	var text := label if label != "" else "%s наносит %d" % [attacker.def.name, dmg]
	if crit:
		text += " — критично!"
	_emit("attack", attacker_side, text, dmg, crit)

	if defender.reflect > 0 and attacker.alive:
		var back: int = maxi(1, dmg * defender.reflect / 100)
		defender.reflect = 0
		attacker.hp = maxi(0, attacker.hp - back)
		_emit("reflect", defender_side, "Подмена отражает %d" % back, back)


# --- Применение способности ---

func _use_ability(side: int, ability_id: String) -> void:
	var me := fighter(side)
	var foe := fighter(_other(side))

	match ability_id:
		"strike":
			_hit(side, 1.0)

		"drain":
			var before: int = foe.hp
			_hit(side, 0.9, "%s впивается" % me.def.name)
			var dealt: int = before - foe.hp
			if dealt > 0:
				var healed: int = maxi(1, dealt * 55 / 100)
				me.hp = mini(me.def.max_hp, me.hp + healed)
				_emit("heal", side, "%s восстанавливает %d" % [me.def.name, healed], healed)

		"roller":
			_emit("buff", side, "%s поднимает каток" % me.def.name)
			_hit(side, 1.9, "Каток обрушивается")

		"world":
			foe.time_stopped = 1
			_emit("stop", side, "ZA WARUDO! Время остановилось")
			_hit(side, 1.0, "Удар в застывшем времени")

		"daggers":
			_emit("multi", side, "%s бросает кинжалы" % me.def.name)
			for i in 3:
				if foe.alive:
					_hit(side, 0.55)

		"illusion":
			me.illusion = true
			_emit("buff", side, "%s растраивается в двойниках" % me.def.name)
			_hit(side, 0.2, "Двойник жалит")

		"swap":
			me.reflect = 45
			_emit("buff", side, "%s готовит подмену" % me.def.name)
			_hit(side, 0.25, "Обманный выпад")

		_:
			_hit(side, 1.0)


# --- Ход игрока и ответ противника ---

func use(ability_id: String) -> Array:
	_events = []
	if over:
		return _events

	var ability := _find_ability(player.def, ability_id)
	if ability == null:
		return _events
	if not player.can_pay(ability.cost):
		_emit("buff", SIDE_PLAYER, "Не хватает энергии")
		return _events

	player.energy -= ability.cost
	_use_ability(SIDE_PLAYER, ability_id)

	if not enemy.alive:
		_finish(true)
		return _events

	_enemy_turn()

	if not player.alive:
		_finish(false)
		return _events

	_end_round()
	return _events


func _find_ability(def: Roster.CharDef, id: String) -> Roster.Ability:
	for a in def.abilities:
		if a.id == id:
			return a
	return null


func _enemy_turn() -> void:
	if enemy.time_stopped > 0:
		enemy.time_stopped -= 1
		_emit("stop", SIDE_ENEMY, "%s не может двигаться" % enemy.def.name)
		return

	var choice := choose_for(SIDE_ENEMY)
	enemy.energy -= choice.cost
	_use_ability(SIDE_ENEMY, choice.id)


# Простой, но не бестолковый ИИ: лечится когда плохо, копит на сильное,
# в остальное время бьёт тем, что позволяет энергия.
# Публичный, чтобы тест мог устроить зеркальный матч на том же мозге.
func choose_for(side: int) -> Roster.Ability:
	var me := fighter(side)
	var affordable: Array = []
	for a in me.def.abilities:
		if not me.can_pay(a.cost):
			continue
		# Не тратить ход на то, что уже висит
		if a.id == "illusion" and me.illusion:
			continue
		if a.id == "swap" and me.reflect > 0:
			continue
		affordable.append(a)

	var low: bool = me.hp_fraction < 0.45

	for a in affordable:
		if a.id == "drain" and low:
			return a
		if a.id == "illusion" and low and not me.illusion:
			return a

	for a in affordable:
		if a.id == "world":
			return a

	var strong: Array = []
	for a in affordable:
		if a.cost > 0:
			strong.append(a)

	if strong.size() > 0 and _rng.randf() < 0.7:
		return strong[_rng.randi_range(0, strong.size() - 1)]

	return _find_ability(me.def, "strike")


func _end_round() -> void:
	player.energy = mini(MAX_ENERGY, player.energy + 1)
	enemy.energy = mini(MAX_ENERGY, enemy.energy + 1)
	round_no += 1
	_emit("round", SIDE_PLAYER, "")


func _finish(won: bool) -> void:
	over = true
	player_won = won
	var loser := enemy if won else player
	_emit("ko", SIDE_ENEMY if won else SIDE_PLAYER, "%s повержен" % loser.def.name)
