package pl.codziennik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.codziennik.app.data.Habit

class HabitModelTest {
    @Test fun `active habit retains a stable identifier`() {
        val habit = Habit("legacy-5", "Suplementacja", "💊", true, 5)
        assertEquals("legacy-5", habit.id)
        assertTrue(habit.active)
    }
}
