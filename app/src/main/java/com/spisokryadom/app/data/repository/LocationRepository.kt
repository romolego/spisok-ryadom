package com.spisokryadom.app.data.repository
import android.content.SharedPreferences
import com.spisokryadom.app.data.dao.CityDao
import com.spisokryadom.app.data.dao.ShopDao
import com.spisokryadom.app.data.entity.CityEntity
import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocationRepository(private val citiesDao: CityDao, private val shopsDao: ShopDao, private val prefs: SharedPreferences) : AutoCloseable {
    override fun close(){prefs.unregisterOnSharedPreferenceChangeListener(listener);scope.cancel()}
    suspend fun closeAndJoin(){close();scope.coroutineContext[Job]?.join()}
    private val edits=Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selected = MutableStateFlow(prefs.getLong("active_city", 1))
    val cities = citiesDao.observeAll().stateIn(scope, SharingStarted.Eagerly, listOf(CityEntity(1,"Мой город")))
    val activeCity = combine(cities, selected) { list, id -> list.find { it.id == id } ?: list.firstOrNull() ?: CityEntity(1,"Мой город") }
        .stateIn(scope, SharingStarted.Eagerly, CityEntity(1,"Мой город"))
    val cityShops = combine(shopsDao.getAllOrdered(), activeCity) { shops, city -> shops.filter { it.cityId == city.id } }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "active_city") selected.value = prefs.getLong("active_city",1) }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun select(id: Long) { selected.value=id; prefs.edit().putLong("active_city",id).apply() }
    suspend fun currentId(): Long {
        val list=citiesDao.getAllSync(); return list.find { it.id==selected.value }?.id ?: list.firstOrNull()?.id ?: 1
    }
    suspend fun add(name: String, selectAfterCreate: Boolean = true): Long = edits.withLock {
        val value=name.trim(); require(value.isNotEmpty()) { "Введите название города" }
        val existing=citiesDao.getAllSync().find { FuzzySearch.normalize(it.name)==FuzzySearch.normalize(value) }
        (existing?.id ?: citiesDao.insert(CityEntity(name=value))).also { if(selectAfterCreate)select(it) }
    }
    suspend fun rename(city: CityEntity, name: String) = edits.withLock {
        require(name.isNotBlank()) { "Введите название города" }
        require(citiesDao.getAllSync().none { it.id!=city.id && FuzzySearch.normalize(it.name)==FuzzySearch.normalize(name) }) { "Такой город уже есть" }
        citiesDao.update(city.copy(name=name.trim()))
    }
    suspend fun delete(city: CityEntity) = edits.withLock {
        require(citiesDao.getAllSync().size>1) { "Нужен хотя бы один город" }
        require(shopsDao.getAllSync().none { it.cityId==city.id }) { "Сначала перенесите магазины этого города" }
        citiesDao.deleteEmpty(city.id)
        require(citiesDao.getById(city.id)==null){"Сначала перенесите рынки и торговые центры этого города"}
        if(selected.value==city.id)select(citiesDao.getAllSync().first().id)
    }
}
