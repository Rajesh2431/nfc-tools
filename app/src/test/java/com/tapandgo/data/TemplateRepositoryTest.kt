package com.tapandgo.data

import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.data.repository.TemplateRepository
import com.tapandgo.data.repository.TemplateRepository.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateRepositoryTest {
    private val ok = listOf(RecordSpec.Text("hi"))

    @Test
    fun `validation rules`() {
        assertNull(TemplateRepository.validate("Name", "", ok))
        assertEquals(Problem.NAME_EMPTY, TemplateRepository.validate(" ", "", ok))
        assertEquals(Problem.NAME_TOO_LONG, TemplateRepository.validate("x".repeat(81), "", ok))
        assertEquals(Problem.DESCRIPTION_TOO_LONG, TemplateRepository.validate("n", "x".repeat(501), ok))
        assertEquals(Problem.NO_RECORDS, TemplateRepository.validate("n", "", emptyList()))
        assertEquals(Problem.INVALID_RECORD, TemplateRepository.validate("n", "", listOf(RecordSpec.Uri("file:///x"))))
        assertEquals(Problem.INVALID_RECORD, TemplateRepository.validate("n", "", listOf(RecordSpec.Empty)))
    }
}
