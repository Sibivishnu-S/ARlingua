package com.example

import com.example.data.ObjectTranslationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalDictionaryFallbackTest {

    @Test
    fun testOfflineDictionaryTranslation_knownWords() {
        val spanishCup = ObjectTranslationRepository.getTranslation("Cup", "Spanish")
        assertNotNull(spanishCup)
        assertEquals("la taza", spanishCup?.targetWord)
        assertEquals("/ˈtasa/", spanishCup?.ipa)

        val frenchBook = ObjectTranslationRepository.getTranslation("Book", "French")
        assertNotNull(frenchBook)
        assertEquals("le livre", frenchBook?.targetWord)
        assertEquals("/lə livʁ/", frenchBook?.ipa)

        val germanChair = ObjectTranslationRepository.getTranslation("Chair", "German")
        assertNotNull(germanChair)
        assertEquals("der Stuhl", germanChair?.targetWord)
        assertEquals("/deːɐ̯ ʃtuːl/", germanChair?.ipa)
    }

    @Test
    fun testOfflineDictionaryTranslation_unknownWordGracefulFallback() {
        val unknown = ObjectTranslationRepository.getTranslation("NonExistentObjectXYZ", "Spanish")
        assertNotNull(unknown)
        assertEquals("NonExistentObjectXYZ", unknown?.targetWord)
    }
}
