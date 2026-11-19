package ar.jornadas.data

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    val programa = dao.observar()
    val edicionesDescargadas = dao.edicionesDescargadas()

    /**
     * Descarga la instantánea completa y la aplica de forma atómica. Si algo falla antes
     * de tener la respuesta completa y válida, no se toca la base: se siguen usando los
     * datos guardados. Una lista vacía es una respuesta válida (programa sin charlas).
     */
    suspend fun sincronizar() {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.reemplazar(fichas)
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
