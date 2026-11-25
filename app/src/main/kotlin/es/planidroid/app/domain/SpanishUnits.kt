package es.planidroid.app.domain
import java.text.Normalizer
import java.util.Locale

/** Closed catalogue: metric units and common Spanish retail/cooking formats. */
object SpanishUnits {
    val options=linkedMapOf(
        "" to "Sin unidad",
        "ud" to "Unidades", "g" to "Gramos", "kg" to "Kilogramos", "mg" to "Miligramos",
        "ml" to "Mililitros", "cl" to "Centilitros", "dl" to "Decilitros", "L" to "Litros",
        "mm" to "Milímetros", "cm" to "Centímetros", "m" to "Metros", "m²" to "Metros cuadrados",
        "docena" to "Docenas", "par" to "Pares", "paquete" to "Paquetes", "caja" to "Cajas",
        "bolsa" to "Bolsas", "bote" to "Botes", "lata" to "Latas", "botella" to "Botellas",
        "brik" to "Briks", "tarro" to "Tarros", "bandeja" to "Bandejas", "envase" to "Envases",
        "rollo" to "Rollos", "sobre" to "Sobres", "pieza" to "Piezas", "manojo" to "Manojos",
        "diente" to "Dientes", "rebanada" to "Rebanadas", "loncha" to "Lonchas", "ración" to "Raciones",
        "cucharadita" to "Cucharaditas", "cucharada" to "Cucharadas", "pizca" to "Pizcas", "vaso" to "Vasos", "taza" to "Tazas"
    )
    private fun fold(s:String)=Normalizer.normalize(s.trim().lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").trimEnd('.')
    private val aliases=mapOf("u" to "ud","uds" to "ud","un" to "ud","unidad" to "ud","unidades" to "ud","gramo" to "g","gramos" to "g","gr" to "g","grs" to "g","kilo" to "kg","kilos" to "kg","kilogramo" to "kg","kilogramos" to "kg","litro" to "L","litros" to "L","l" to "L","mililitro" to "ml","mililitros" to "ml","brick" to "brik","bricks" to "brik","cda" to "cucharada","cdta" to "cucharadita","sin unidad" to "")
    fun canonical(value:String):String? {
        val s=fold(value)
        return aliases[s]?:options.keys.firstOrNull{fold(it)==s}?:options.entries.firstOrNull{fold(it.value)==s}?.key
    }
    fun label(value:String):String=options[value]?:value+" (anterior)"
}
