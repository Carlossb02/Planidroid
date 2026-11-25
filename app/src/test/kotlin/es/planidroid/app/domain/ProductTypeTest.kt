package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
class ProductTypeTest{
    @Test fun categories(){
        mapOf("Garbanzos cocidos" to ProductType.VEGETABLES,"Lentejas" to ProductType.VEGETABLES,"Piña" to ProductType.FRUIT,"Leche semidesnatada" to ProductType.DAIRY,"Yogures naturales" to ProductType.YOGURT,"Queso manchego" to ProductType.CHEESE,"Pechuga de pollo" to ProductType.MEAT,"Merluza" to ProductType.FISH,"Pasta de dientes" to ProductType.HYGIENE,"Detergente de lavadora" to ProductType.CLEANING,"Tomate" to ProductType.VEGETABLES,"Sal" to ProductType.PANTRY).forEach{(name,type)->assertEquals(name,type,productType(name))}
    }
}
