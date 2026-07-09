package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.mapping.DataSourceRef
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.PropertyType
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.mapping.RolePropertyBinding
import com.ironclinicgym.sift.core.notion.NotionParser
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotionRecordMapperTest {

    private val mapping = DatabaseMapping(
        id = "mapping-ds1",
        ref = DataSourceRef("ws1", "db1", "ds1"),
        label = "Sift Tasks",
        bindings = listOf(
            RolePropertyBinding(Role.TITLE, "title", "Title", PropertyType.TITLE),
            RolePropertyBinding(Role.PRIORITY, "pr", "Priority", PropertyType.SELECT),
            RolePropertyBinding(Role.BUCKET, "sr", "Bucket", PropertyType.SELECT),
            RolePropertyBinding(Role.STATUS, "dn", "Done", PropertyType.CHECKBOX),
        ),
        priorities = emptyList(),
        buckets = emptyList(),
    )

    @Test fun `maps a page through the mapping by property id, not name`() {
        // Note the property keys are RENAMED on the page; only the inner ids match the mapping.
        val page = NotionParser.json.parseToJsonElement(
            """
            {"id":"page1","properties":{
              "Renamed Title":{"id":"title","type":"title","title":[{"plain_text":"Buy milk"}]},
              "When":{"id":"pr","type":"select","select":{"id":"b1","name":"today"}},
              "Area":{"id":"sr","type":"select","select":{"id":"s0","name":"Personal"}},
              "Finished":{"id":"dn","type":"checkbox","checkbox":true}
            }}
            """.trimIndent(),
        ).jsonObject

        val task = NotionRecordMapper.toTask(page, mapping)

        assertEquals("Buy milk", task.title)
        assertEquals("today", task.priorityOptionName)
        assertEquals("Personal", task.bucketOptionName)
        assertTrue(task.isDone)
        assertEquals("page1", task.pageId)
    }
}
