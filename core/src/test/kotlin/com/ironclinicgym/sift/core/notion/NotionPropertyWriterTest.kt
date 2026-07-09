package com.ironclinicgym.sift.core.notion

import com.ironclinicgym.sift.core.domain.mappingFixture
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.notion.NotionPropertyWriter.PropertyEdit
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionPropertyWriterTest {

    private val mapping = mappingFixture()

    @Test
    fun `writes target the user's mapped property name, never a hardcoded one`() {
        // The status role is bound to a property the fixture named "Done"; a select to "Priority".
        val props = NotionPropertyWriter.buildProperties(
            mapping,
            listOf(
                PropertyEdit.TitleText("Hello"),
                PropertyEdit.SelectByName(Role.PRIORITY, "asap"),
                PropertyEdit.Checkbox(Role.STATUS, true),
            ),
        )
        assertTrue(props.containsKey("Name")) // title property's mapped name
        assertTrue(props.containsKey("Priority"))
        assertTrue(props.containsKey("Done"))
        assertEquals("asap", props["Priority"]!!.jsonObject["select"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(true, props["Done"]!!.jsonObject["checkbox"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `an edit for an unbound role is skipped`() {
        // WORK_FLAG is not bound in the fixture, so nothing is written for it.
        val props = NotionPropertyWriter.buildProperties(mapping, listOf(PropertyEdit.Checkbox(Role.WORK_FLAG, true)))
        assertTrue(props.isEmpty())
    }

    @Test
    fun `clearing a date writes an explicit null`() {
        val props = NotionPropertyWriter.buildProperties(mapping, listOf(PropertyEdit.DateValue(Role.DUE_DATE, null)))
        assertEquals(JsonNull, props["Due"]!!.jsonObject["date"])
    }

    @Test
    fun `a date value writes its start`() {
        val props = NotionPropertyWriter.buildProperties(mapping, listOf(PropertyEdit.DateValue(Role.DUE_DATE, "2026-07-02")))
        assertEquals("2026-07-02", props["Due"]!!.jsonObject["date"]!!.jsonObject["start"]!!.jsonPrimitive.content)
    }
}
