package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Lo que hay guardado: las charlas y si alguna vez se descargó una edición válida (aunque esté vacía). */
data class Cartelera(val charlas: List<Charla>, val descargada: Boolean)

class ProgramaModel(app: Application, private val guardado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.obtener(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    /** null hasta que Room entrega la primera lectura (evita mostrar "vacío" por error al arrancar). */
    val cartelera: StateFlow<Cartelera?> =
        combine(repo.programa, repo.edicionesDescargadas) { charlas, ediciones ->
            Cartelera(charlas, charlas.isNotEmpty() || ediciones > 0)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Filtros en SavedStateHandle: sobreviven a rotación y a la muerte del proceso,
    // pero una tarea nueva arranca sin filtros.
    val dia: StateFlow<Int> = guardado.getStateFlow(CLAVE_DIA, 0)
    val tema: StateFlow<String> = guardado.getStateFlow(CLAVE_TEMA, TEMA_TODOS)

    /** true si la última petición terminó en falla. */
    val fallo = MutableStateFlow(false)
    val cargando = MutableStateFlow(false)

    fun elegirDia(valor: Int) { guardado[CLAVE_DIA] = valor }
    fun elegirTema(valor: String) { guardado[CLAVE_TEMA] = valor }

    /** Carga automática: una por proceso, sin importar cuántas veces se recree la pantalla. */
    fun cargaInicial() {
        if (servicios.reclamarCargaAutomatica()) descargar()
    }

    /** Actualización explícita (Actualizar / Reintentar). Se ignora si ya hay una petición en curso. */
    fun actualizar() = descargar()

    private fun descargar() {
        if (cargando.value) return          // siempre en el hilo principal: no hay carrera
        cargando.value = true
        viewModelScope.launch {
            try {
                repo.sincronizar()
                fallo.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // IOException, HttpException, JSON inválido, etc.: la base no se tocó.
                fallo.value = true
            } finally {
                cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    companion object {
        const val TEMA_TODOS = "Todos"
        private const val CLAVE_DIA = "filtro_dia"
        private const val CLAVE_TEMA = "filtro_tema"
    }
}
