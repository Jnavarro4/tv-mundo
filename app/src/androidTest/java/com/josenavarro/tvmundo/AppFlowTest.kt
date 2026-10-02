package com.josenavarro.tvmundo

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.josenavarro.tvmundo.data.Catalog
import com.josenavarro.tvmundo.data.Category
import com.josenavarro.tvmundo.data.Channel
import com.josenavarro.tvmundo.data.Country
import com.josenavarro.tvmundo.data.StreamSource
import com.josenavarro.tvmundo.data.SuggestedEvent
import com.josenavarro.tvmundo.data.SuggestedPick
import com.josenavarro.tvmundo.data.Suggestions
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pruebas de interfaz de punta a punta: la app real con un catálogo de prueba
 * (sin red) y el control remoto simulado con teclas del D-pad.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        val files = instrumentation.targetContext.filesDir
        val json = Json { encodeDefaults = true }
        // Streams que fallan al instante (puerto cerrado) para probar el panel de error.
        fun ch(id: String, name: String, country: String, cat: String) = Channel(
            id, name, country, listOf(cat), logo = null,
            streams = listOf(StreamSource(url = "http://127.0.0.1:9/$id.m3u8")),
        )
        val catalog = Catalog(
            updatedAt = System.currentTimeMillis(),
            channels = listOf(
                ch("CaracolTV.co", "Caracol TV", "CO", "general"),
                ch("CanalRCN.co", "Canal RCN", "CO", "general"),
                ch("NoticiasRCN.co", "Noticias RCN", "CO", "news"),
                ch("WinSports.co", "Win Sports", "CO", "sports"),
                ch("TyCSports.ar", "TyC Sports", "AR", "sports"),
                ch("TN.ar", "TN", "AR", "news"),
            ),
            countries = listOf(Country("CO", "Colombia", "🇨🇴"), Country("AR", "Argentina", "🇦🇷")),
            categories = listOf(Category("general", "General"), Category("news", "Noticias"), Category("sports", "Deportes")),
        )
        File(files, "catalog.json").writeText(json.encodeToString(Catalog.serializer(), catalog))
        val now = System.currentTimeMillis() / 1000
        val suggestions = Suggestions(
            generatedAt = "2026-10-02T10:00:00Z",
            source = "prueba",
            events = listOf(
                SuggestedEvent(
                    id = "final", title = "Final de prueba", description = "Partido de prueba",
                    type = "futbol", competition = "Liga de prueba", startEpoch = now - 600, channels = listOf("WinSports.co"),
                ),
            ),
            picks = listOf(SuggestedPick("NoticiasRCN.co", "Motivo de prueba")),
        )
        File(files, "sugeridos.json").writeText(json.encodeToString(Suggestions.serializer(), suggestions))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("Eventos de hoy")
        // Espera a que el Inicio tome el foco (como al abrir la app con el control).
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun waitForText(text: String, substring: Boolean = true, timeout: Long = 15_000) {
        compose.waitUntil(timeout) {
            compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Elemento enfocable/clicable que contiene el texto (árbol fusionado, como lo ve el usuario). */
    private fun node(text: String, substring: Boolean = true): SemanticsNodeInteraction =
        compose.onAllNodes(hasText(text, substring = substring) and hasClickAction()).onFirst()

    private fun click(text: String, substring: Boolean = true) {
        node(text, substring).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun press(vararg keys: Int) {
        keys.forEach {
            instrumentation.sendKeyDownUpSync(it)
            compose.waitForIdle()
        }
    }

    private fun openSection(label: String) {
        compose.onNode(hasContentDescription(label) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun inicioMuestraFilasConIA() {
        waitForText("Final de prueba")
        waitForText("Recomendados hoy")
        // El panel superior muestra el evento enfocado al abrir.
        waitForText("Sugerido por IA")
        waitForText("Dónde verlo: Win Sports")
    }

    @Test
    fun controlRemotoReproduceYSaltaCanalCaido() {
        // El foco inicial está en la primera tarjeta (el evento); se baja hasta "Recomendados hoy"
        // (puede haber una fila "Seguir viendo" en medio si el equipo ya tiene historial).
        repeat(3) {
            if (compose.onAllNodes(hasText("Motivo de prueba", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                press(KeyEvent.KEYCODE_DPAD_DOWN)
            }
        }
        waitForText("Motivo de prueba")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // El stream falla: aparece el panel de error con "Siguiente canal" enfocado.
        waitForText("No se pudo reproducir")
        press(KeyEvent.KEYCODE_BACK)
        waitForText("Recomendados hoy")
    }

    @Test
    fun siguienteCanalDesdeElPanelDeError() {
        openSection("Países")
        click("Colombia")
        waitForText("Mantén pulsado OK")
        click("Caracol TV")
        waitForText("No se pudo reproducir «")
        // OK sobre "Siguiente canal" pasa al siguiente de la lista.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("No se pudo reproducir «Caracol TV»"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        press(KeyEvent.KEYCODE_BACK)
        waitForText("Mantén pulsado OK")
    }

    @Test
    fun eventoMuestraDondeVerlo() {
        click("Final de prueba")
        waitForText("Dónde verlo o seguirlo")
        waitForText("Win Sports")
        press(KeyEvent.KEYCODE_BACK)
        waitForText("Eventos de hoy")
    }

    @Test
    fun busquedaInteligente() {
        openSection("Buscar")
        waitForText("Búsqueda inteligente")
        click("Noticias de Colombia")
        waitForText("Entendí: Noticias")
        waitForText("Noticias RCN")
    }

    @Test
    fun favoritoConPulsacionLarga() {
        openSection("Temáticas")
        click("Deportes", substring = false)
        waitForText("TyC Sports")
        node("TyC Sports").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitUntil(10_000) {
            listOf("agregado a Favoritos", "quitado de Favoritos").any {
                compose.onAllNodes(hasText(it, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        val added = compose.onAllNodes(hasText("TyC Sports agregado a Favoritos", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        press(KeyEvent.KEYCODE_BACK)
        openSection("Favoritos")
        compose.waitForIdle()
        val listed = compose.onAllNodes(hasText("TyC Sports") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        // Si se agregó debe aparecer en Favoritos; si se quitó (ya estaba), ya no.
        org.junit.Assert.assertEquals(added, listed)
    }

    @Test
    fun ajustesCambianPreferencias() {
        openSection("Ajustes")
        waitForText("Vista previa en vivo")
        val before = if (compose.onAllNodes(hasText("Activada"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) "Activada" else "Desactivada"
        val after = if (before == "Activada") "Desactivada" else "Activada"
        click("Vista previa en vivo")
        waitForText(after, substring = false)
        click("Vista previa en vivo")
        waitForText(before, substring = false)
    }
}
