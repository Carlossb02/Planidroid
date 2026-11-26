package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
class RecipeBookTest{
    @Test fun matchExistingRecipeBeforeQuery(){
        val r=SavedRecipe(title="Croquetas de jamón",servings=4,ingredients=listOf(RecipeIngredient("Harina",100.0,"g")))
        val p=SavedRecipe(title="Paella de verduras",ingredients=listOf(RecipeIngredient("Arroz",200.0,"g")))
        assertEquals(listOf(r),RecipeBook.matches("Quiero hacer croquetas",listOf(r,p)))
        assertEquals(listOf(r),RecipeBook.matches("Dame una receta de CROQUETAS de jamon",listOf(r,p)))
        assertTrue(RecipeBook.matches("Una receta de tortilla",listOf(r,p)).isEmpty())
        assertEquals(r,decode<SavedRecipe>(encode(r)))
        RecipeBook.validate(r)
    }
    @Test fun variantsAreSeparateAndUnknownPhotoQuantityPreserved(){
        val first=SavedRecipe(title="Croquetas",ingredients=listOf(RecipeIngredient("Sal",0.0,"")))
        val second=first.copy(id=newId(),title="Croquetas de pollo",source="photo")
        assertEquals(2,RecipeBook.matches("croquetas",listOf(first,second)).size)
        assertNotEquals(first.id,second.id);RecipeBook.validate(second)
        assertEquals(0.0,decode<SavedRecipe>(encode(second)).ingredients.first().amount,0.0)
    }
    @Test fun invalidUnitRejected(){
        val r=SavedRecipe(title="Croquetas",ingredients=listOf(RecipeIngredient("Harina",1.0,"oz")))
        assertTrue(runCatching{RecipeBook.validate(r)}.isFailure)
    }
    @org.junit.Test fun ingredientQuantitiesScaleWithoutInventingMissingValues(){
        org.junit.Assert.assertEquals(225.0,RecipeBook.scaleAmount(150.0,2,3),0.0001)
        org.junit.Assert.assertEquals(0.3333,RecipeBook.scaleAmount(1.0,3,1),0.0001)
        org.junit.Assert.assertEquals(0.0,RecipeBook.scaleAmount(0.0,2,4),0.0)
        org.junit.Assert.assertEquals(100.0,RecipeBook.scaleAmount(100.0,0,4),0.0)
    }
}
