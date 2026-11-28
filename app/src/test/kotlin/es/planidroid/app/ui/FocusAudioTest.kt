package es.planidroid.app.ui

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class FocusAudioTest {
    @Test fun generatedAmbiencesHaveAnAudibleSignalAndQuietLoopBoundary(){
        assertTrue(focusSamples(FocusAmbience.SILENT).isEmpty())
        val tracks=FocusAmbience.entries.filterNot{it==FocusAmbience.SILENT}.map{focusSamples(it)}
        for(samples in tracks){
            assertEquals(0,samples.first().toInt())
            assertEquals(0,samples.last().toInt())
            assertTrue(samples.maxOf{abs(it.toInt())} in 500..20000)
        }
        assertFalse(tracks[1].contentEquals(tracks[2]))
    }
}
