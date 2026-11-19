package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla

@Composable
fun Catalogo(model: ProgramaModel, lista: LazyListState, abrir: (String) -> Unit) {
    val dia by model.dia.collectAsStateWithLifecycle()
    val tema by model.tema.collectAsStateWithLifecycle()
    val cartelera by model.cartelera.collectAsStateWithLifecycle()
    val fallo by model.fallo.collectAsStateWithLifecycle()
    val cargando by model.cargando.collectAsStateWithLifecycle()
    Selector(listOf("Ambos días", "Día 1", "Día 2"), dia) { model.elegirDia(it) }
    val temas = listOf(ProgramaModel.TEMA_TODOS, "Android", "Datos", "Calidad")
    Selector(temas, temas.indexOf(tema).coerceAtLeast(0)) { model.elegirTema(temas[it]) }
    TextButton(onClick = model::actualizar, enabled = !cargando) { Text("Actualizar") }
    if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
    val actual = cartelera
    when {
        actual == null -> Unit
        !actual.descargada -> {
            // Nunca hubo una descarga exitosa: no hay datos guardados que mostrar.
            if (cargando) {
                Text("Descargando el programa…")
            } else {
                Text("No se pudo consultar el programa")
                OutlinedButton(onClick = model::actualizar) { Text("Reintentar") }
            }
        }
        else -> {
            if (fallo) Text("Sin conexión: se están usando los datos guardados del programa.")
            val visibles = actual.charlas.filter {
                (dia == 0 || it.dia == dia) && (tema == ProgramaModel.TEMA_TODOS || it.tema == tema)
            }
            when {
                actual.charlas.isEmpty() -> Text("Sin charlas programadas")
                visibles.isEmpty() -> Text("No hay charlas con los filtros elegidos")
                else -> Grilla(visibles, abrir, lista)
            }
        }
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, abrir: (String) -> Unit, estado: LazyListState = rememberLazyListState()) {
    LazyColumn(state = estado, verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
