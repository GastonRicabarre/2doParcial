package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/**
 * Marca de que ya se descargó, al menos una vez, una edición completa del programa.
 * Hace falta porque una edición válida puede no tener charlas (v4): con la tabla de
 * charlas vacía no se distingue "programa vacío" de "nunca se descargó nada".
 */
@Entity(tableName = "estado")
data class Estado(@PrimaryKey val id: Int, val descargada: Boolean)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT COUNT(*) FROM estado WHERE descargada = 1")
    abstract fun edicionesDescargadas(): Flow<Int>

    @Query("SELECT * FROM charlas")
    abstract suspend fun todas(): List<Charla>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardar(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun registrar(estado: Estado)

    @Query("DELETE FROM charlas WHERE id IN (:ids)")
    abstract suspend fun retirar(ids: List<String>)

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    /**
     * Aplica una instantánea completa del programa en una sola transacción.
     * Las marcas se leen DENTRO de la transacción, así que cualquier marca agregada o
     * quitada mientras la petición estaba en vuelo se respeta. Las charlas que ya no
     * están se borran (y con ellas su marca: si vuelven, vuelven sin marcar).
     */
    @Transaction
    open suspend fun reemplazar(fichas: List<Charla>) {
        val actuales = todas()
        val marcadas = actuales.filter { it.favorita }.map { it.id }.toSet()
        val nuevas = fichas.associateBy { it.id }.values.map { it.copy(favorita = it.id in marcadas) }
        val vigentes = nuevas.map { it.id }.toSet()
        actuales.map { it.id }.filter { it !in vigentes }.chunked(500).forEach { retirar(it) }
        guardar(nuevas)
        registrar(Estado(id = 1, descargada = true))
    }
}

/** v1 -> v2: agrega la tabla `estado` sin tocar `charlas` (se conservan las marcas de los usuarios). */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `estado` (`id` INTEGER NOT NULL, `descargada` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        // Quien ya tenía charlas guardadas ya había descargado una edición.
        db.execSQL("INSERT OR IGNORE INTO `estado` (`id`, `descargada`) SELECT 1, 1 WHERE EXISTS (SELECT 1 FROM `charlas`)")
    }
}

@Database(entities = [Charla::class, Estado::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}
