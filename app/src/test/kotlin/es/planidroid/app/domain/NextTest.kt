package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class NextTest {
 @Test fun dependenciesAndCycles(){val t=Task(title="T");val u=Task(title="U",dependencies=listOf(t.id));assertEquals(listOf(t.id),TaskConstraints.unmet(u,listOf(t,u)));assertTrue(TaskConstraints.cycle(t.copy(dependencies=listOf(u.id)),listOf(t,u)));assertTrue(TaskConstraints.unmet(u,listOf(t.copy(status=Status.DONE),u)).isEmpty())}
 @Test fun repeatStableId(){val t=Task(title="T",due="2027-01-01",status=Status.DONE,recurrence=Recurrence.WEEKLY,checks=listOf(Check(title="x",done=true)));val next=RepeatingTasks.next(t)!!;assertEquals("2027-01-08",next.due);assertEquals(next.id,RepeatingTasks.next(t)!!.id);assertEquals(Status.PENDING,next.status);assertFalse(next.checks[0].done)}
}
