package com.spisokryadom.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.spisokryadom.app.util.FuzzySearch

/** A small editable catalogue, not a product database. Installation never creates shop placements. */
object StandardCategories {
 data class Category(val name:String,val icon:String,val aliases:List<String> = emptyList())
 val groups=listOf(
  Category("Продукты","groceries",listOf()),
  Category("Овощи и фрукты","vegetables",listOf("Овощи/фрукты")),
  Category("Молочные продукты и яйца","milk",listOf("Молочка")),
  Category("Мясо и птица","meat",listOf()),
  Category("Рыба и морепродукты","fish",listOf()),
  Category("Хлеб и выпечка","bread",listOf("Выпечка")),
  Category("Бакалея","grains",listOf()),
  Category("Консервы","canned",listOf()),
  Category("Замороженные продукты","frozen",listOf("Заморозка")),
  Category("Готовая еда","ready",listOf("Гастроном (готовая еда)")),
  Category("Сладости и снеки","sweets",listOf()),
  Category("Вода и напитки","water",listOf("Напитки")),
  Category("Чай и кофе","tea",listOf()),
  Category("Орехи и сухофрукты","nuts",listOf("Орехи")),
  Category("Соусы и специи","herbs",listOf()),
  Category("Детские товары","baby",listOf()),
  Category("Бытовая химия","cleaning",listOf()),
  Category("Гигиена и косметика","hygiene",listOf("Гигиена")),
  Category("Товары для дома","home",listOf()),
  Category("Посуда и кухня","tableware",listOf()),
  Category("Аптека","medicine",listOf()),
  Category("Здоровье","vitamins",listOf()),
  Category("Зоотовары","pets",listOf("Для животных")),
  Category("Одежда и обувь","clothes",listOf("Одежда")),
  Category("Электроника","tech",listOf()),
  Category("Автомобильное","car",listOf("Автотовары")),
  Category("Ремонт и инструменты","tools",listOf()),
  Category("Сад и дача","garden",listOf()),
  Category("Канцелярия и творчество","stationery",listOf()),
  Category("Спорт и отдых","sport",listOf()))
 val departments=listOf(
  Category("Овощи/фрукты","vegetables",listOf()),
  Category("Молочка","milk",listOf()),
  Category("Яйца","eggs",listOf()),
  Category("Сыры","cheese",listOf()),
  Category("Мясо/колбаса","meat",listOf()),
  Category("Рыба и морепродукты","fish",listOf()),
  Category("Выпечка","bread",listOf()),
  Category("Крупы","grains",listOf()),
  Category("Макароны","pasta",listOf()),
  Category("Бакалея (мука, сахар, масло)","groceries",listOf("Бакалея")),
  Category("Консервы","canned",listOf()),
  Category("Заморозка","frozen",listOf()),
  Category("Гастроном (готовая еда)","ready",listOf("Готовая еда")),
  Category("Сладости","sweets",listOf()),
  Category("Снеки","snacks",listOf()),
  Category("Орехи","nuts",listOf("Орехи и сухофрукты")),
  Category("Чай и кофе","tea",listOf()),
  Category("Вода","water",listOf()),
  Category("Соки и газировка","bottle",listOf()),
  Category("Соусы и специи","herbs",listOf()),
  Category("Бытовая химия","cleaning",listOf()),
  Category("Бумажные товары","paper",listOf()),
  Category("Гигиена и косметика","hygiene",listOf()),
  Category("Лекарства и медизделия","medicine",listOf()),
  Category("Корма и товары для животных","pets",listOf()),
  Category("Детские товары","baby",listOf()),
  Category("Товары для дома","home",listOf()),
  Category("Посуда и кухня","tableware",listOf()),
  Category("Автотовары","car",listOf()),
  Category("Канцелярия","stationery",listOf()))
 fun install(db:SupportSQLiteDatabase):Int {
  var added=0
  fun installTable(table:String,rows:List<Category>,department:Boolean){
   data class Existing(val id:Long,val name:String,val icon:String)
   val existing=mutableListOf<Existing>()
   db.query("SELECT id,name,iconKey FROM $table").use{c->while(c.moveToNext())existing+=Existing(c.getLong(0),c.getString(1),c.getString(2))}
   var order=db.query("SELECT COALESCE(MAX(displayOrder),-1)+1 FROM $table").use{it.moveToFirst();it.getInt(0)}
   rows.forEach{row->
    val keys=(row.aliases+row.name).map{FuzzySearch.normalize(it)}.toSet()
    val found=existing.filter{FuzzySearch.normalize(it.name) in keys}
    if(found.isNotEmpty())found.filter{it.icon.isBlank()}.forEach{db.execSQL("UPDATE $table SET iconKey=? WHERE id=?",arrayOf(row.icon,it.id))}
    else {
     if(department)db.execSQL("INSERT INTO $table(name,normalizedName,displayOrder,accent,iconKey) VALUES(?,?,?,'',?)",arrayOf(row.name,FuzzySearch.normalize(row.name),order++,row.icon))
     else db.execSQL("INSERT INTO $table(name,displayOrder,accent,iconKey) VALUES(?,?,'',?)",arrayOf(row.name,order++,row.icon))
     added++
    }
   }
  }
  installTable("product_groups",groups,false);installTable("department_types",departments,true)
  return added
 }
}
