package es.planidroid.app.domain

import org.junit.Assert.*
import org.junit.Test

class AssistantRepliesTest {
    @Test fun planningAcceptsOnlyTasksExplicitlySharedInThisRequest() {
        val raw="""{"answer":"Puedo preparar un plan","action":"plan","taskId":"known"}"""
        assertNull(AssistantReplies.read(raw,emptySet()).action)
        assertEquals(AssistantAction.Plan("known"),AssistantReplies.read(raw,setOf("known")).action)
    }

    @Test fun invalidDateAndQuantityRemainUnspecified() {
        val tasks=AssistantReplies.read("""{"action":"tasks","tasks":[{"title":"Entregar proyecto","due":"mañana","minutes":-1},{"title":" "}]}""",emptySet())
            .action as AssistantAction.Tasks
        val task=tasks.entries.single()
        assertEquals(Category.INBOX,task.category)
        assertEquals("",task.due)
        assertEquals(0,task.minutes)

        val shopping=AssistantReplies.read("""{"action":"shopping","products":[{"name":"Leche","quantity":-2,"unit":"L"},{"name":"Arroz","quantity":2,"unit":"kilos"}]}""",emptySet())
            .action as AssistantAction.ShoppingItems
        assertFalse(shopping.entries.first().quantitySpecified)
        assertEquals("kg",shopping.entries.last().unit)
        assertTrue(shopping.entries.all{it.listId.isBlank()})
    }

    @Test fun repliesCannotDeleteOrChangeExistingRecords() {
        assertNull(AssistantReplies.read("""{"answer":"Respuesta","action":"delete","taskId":"known"}""",setOf("known")).action)
    }
}
