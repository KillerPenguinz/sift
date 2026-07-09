package com.ironclinicgym.sift.core.mapping

import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.core.notion.model.NotionPropertySchema
import com.ironclinicgym.sift.core.notion.model.NotionSelectOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingLogicTest {

    private fun binding(role: Role, type: PropertyType) =
        RolePropertyBinding(role, "${role.name}_id", role.name, type)

    @Test fun `validator passes when all required roles are bound to compatible types`() {
        val result = MappingValidator.validate(
            listOf(
                binding(Role.PRIORITY, PropertyType.SELECT),
                binding(Role.BUCKET, PropertyType.SELECT),
                binding(Role.STATUS, PropertyType.CHECKBOX),
                binding(Role.TITLE, PropertyType.TITLE),
            ),
        )
        assertTrue(result.isSatisfied)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun `validator blocks an incompatible priority type with a plain message`() {
        val result = MappingValidator.validate(
            listOf(
                binding(Role.PRIORITY, PropertyType.RICH_TEXT),
                binding(Role.BUCKET, PropertyType.SELECT),
                binding(Role.STATUS, PropertyType.CHECKBOX),
                binding(Role.TITLE, PropertyType.TITLE),
            ),
        )
        assertFalse(result.isSatisfied)
        val msg = result.issues.single { it.role == Role.PRIORITY }.message
        assertFalse("no hyphens in user text", msg.contains("-"))
        assertTrue(msg.contains("Select or Status"))
    }

    @Test fun `validator reports a missing required role`() {
        val result = MappingValidator.validate(listOf(binding(Role.TITLE, PropertyType.TITLE)))
        assertFalse(result.isSatisfied)
        assertEquals(2, result.issues.size) // priority, status missing (bucket is optional)
    }

    @Test fun `validator passes without optional bucket`() {
        val result = MappingValidator.validate(
            listOf(
                binding(Role.PRIORITY, PropertyType.SELECT),
                binding(Role.STATUS, PropertyType.CHECKBOX),
                binding(Role.TITLE, PropertyType.TITLE),
            ),
        )
        assertTrue(result.isSatisfied)
    }

    @Test fun `auto-matcher maps known names and orders by urgency, surfacing the rest`() {
        val options = listOf(
            OptionInput("o1", "Later"),
            OptionInput("o2", "ASAP"),
            OptionInput("o3", "Blocked"), // unmatched
            OptionInput("o4", "Today"),
        )
        val match = AutoMatcher.matchPriorities(options)
        // Matched ones first, in urgency order: ASAP, Today, Later; then unmatched Blocked.
        assertEquals(listOf("ASAP", "Today", "Later", "Blocked"), match.bindings.map { it.optionName })
        assertEquals(listOf("o3"), match.unmatchedOptionIds)
        assertEquals("ASAP", match.bindings.first { it.optionName == "ASAP" }.priorityKey)
        assertNull(match.bindings.first { it.optionName == "Blocked" }.priorityKey)
    }

    @Test fun `auto-matcher never collapses extras`() {
        val options = (1..5).map { OptionInput("x$it", "Custom $it") }
        val match = AutoMatcher.matchPriorities(options)
        assertEquals(5, match.bindings.size)
        assertEquals(5, match.unmatchedOptionIds.size)
    }

    @Test fun `default seeded buckets are exactly Work and Personal`() {
        assertEquals(listOf("Work", "Personal"), SchemaTemplate.defaultBucketNames())
    }

    @Test fun `schema template builds the opinionated create properties`() {
        val props = SchemaTemplate.buildCreateProperties()
        assertNotNull(props[SchemaTemplate.PROP_PRIORITY])
        assertNotNull(props[SchemaTemplate.PROP_BUCKET])
        assertNotNull(props[SchemaTemplate.PROP_TITLE])
        assertNotNull(props[SchemaTemplate.PROP_DONE])
    }

    @Test fun `schema template builds a complete mapping from a created schema`() {
        val schema = autoSetupSchema()
        val ref = DataSourceRef("ws1", "db1", "ds1")
        val mapping = SchemaTemplate.buildMapping(ref, schema)

        assertEquals(4, mapping.bindings.size)
        assertEquals(PropertyType.TITLE, mapping.requireProperty(Role.TITLE).propertyType)
        assertEquals(6, mapping.priorities.size)
        assertEquals("asap", mapping.priorities.first().optionName) // urgency order preserved
        assertEquals(2, mapping.buckets.size)
        // Bindings store ids, not the authored names being matched against.
        assertEquals("pr", mapping.requireProperty(Role.PRIORITY).propertyId)
    }

    private fun autoSetupSchema(): NotionDataSourceSchema {
        val priorities = listOf("asap", "today", "tomorrow", "soon", "later", "one day")
        val buckets = listOf("Work", "Personal")
        return NotionDataSourceSchema(
            databaseId = "db1",
            dataSourceId = "ds1",
            title = "Sift Tasks",
            properties = listOf(
                NotionPropertySchema("title", "Title", PropertyType.TITLE),
                NotionPropertySchema("pr", "Priority", PropertyType.SELECT,
                    priorities.mapIndexed { i, n -> NotionSelectOption("b$i", n, "default") }),
                NotionPropertySchema("sr", "Bucket", PropertyType.SELECT,
                    buckets.mapIndexed { i, n -> NotionSelectOption("s$i", n, "default") }),
                NotionPropertySchema("dn", "Done", PropertyType.CHECKBOX),
            ),
        )
    }
}
