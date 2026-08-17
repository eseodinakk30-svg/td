class_name Roster
extends RefCounted

# Данные бойцов. Всё описано здесь, чтобы добавить нового персонажа
# можно было одной функцией, не трогая бой и отрисовку.

class Ability:
	var id: String
	var title: String
	var desc: String
	var cost: int

	func _init(p_id: String, p_title: String, p_desc: String, p_cost: int) -> void:
		id = p_id
		title = p_title
		desc = p_desc
		cost = p_cost


class CharDef:
	var id: String
	var name: String
	var universe: String
	var tagline: String
	var build: String          # телосложение: "heavy" или "slim"
	var primary: Color         # основной цвет костюма
	var secondary: Color       # акценты, металл
	var skin: Color
	var hair: Color
	var max_hp: int
	var attack: int
	var defense: int
	var speed: int
	var abilities: Array


static func dio() -> CharDef:
	var c := CharDef.new()
	c.id = "dio"
	c.name = "ДИО"
	c.universe = "Мир Жожо"
	c.tagline = "Я отбросил свою человечность"
	c.build = "heavy"
	c.primary = Color("#f2c14a")      # золото
	c.secondary = Color("#b8232f")    # багровый
	c.skin = Color("#e8c9a8")
	c.hair = Color("#f6e07a")
	c.max_hp = 110
	c.attack = 16
	c.defense = 9
	c.speed = 8
	c.abilities = [
		Ability.new("strike", "Удар", "Прямой удар без затрат", 0),
		Ability.new("drain", "Кровопийца", "Урон и восстановление здоровья", 2),
		Ability.new("roller", "Дорожный каток", "Тяжёлый удар с большим уроном", 3),
		Ability.new("world", "ZA WARUDO", "Время встало: враг пропускает ход", 5),
	]
	return c


static func loki() -> CharDef:
	var c := CharDef.new()
	c.id = "loki"
	c.name = "ЛОКИ"
	c.universe = "Мир Марвел"
	c.tagline = "Я вам не бог. Я хуже"
	c.build = "slim"
	c.primary = Color("#1f7a4d")      # изумруд
	c.secondary = Color("#d9b64a")    # золото шлема
	c.skin = Color("#e3c2a6")
	c.hair = Color("#2a2320")
	c.max_hp = 106
	c.attack = 16
	c.defense = 7
	c.speed = 16
	c.abilities = [
		Ability.new("strike", "Удар", "Прямой удар без затрат", 0),
		Ability.new("daggers", "Кинжалы", "Три быстрых броска подряд", 2),
		Ability.new("illusion", "Иллюзии", "Двойник принимает удар и успевает ткнуть в ответ", 2),
		Ability.new("swap", "Подмена", "Урон в ответ и возврат части следующего удара", 3),
	]
	return c


static func all() -> Array:
	var list: Array = []
	list.append(dio())
	list.append(loki())
	return list


# Противник — тот, кого игрок не выбрал.
static func opponent_for(id: String) -> CharDef:
	for c in all():
		if c.id != id:
			return c
	return all()[0]
