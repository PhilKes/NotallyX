package com.philkes.notallyx.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelExtensionsTest {

    @Test
    fun `Json toBaseNote()`() {
        val json =
            """
          {
              "type": "LIST",
              "color": "#E2F6D3",
              "title": "Test",
              "pinned": false,
              "date-created": 1742822848689,
              "labels": [
                "Ggg"
              ],
              "items": [
                {
                  "body": "A",
                  "checked": false,
                  "isChild": false,
                  "order": 0
                },
                {
                  "body": "B",
                  "checked": true,
                  "isChild": true,
                  "order": 1
                },
                {
                  "body": "C",
                  "checked": false,
                  "isChild": true,
                  "order": 2
                },
                {
                  "body": "D",
                  "checked": false,
                  "isChild": false,
                  "order": 3
                },
                {
                  "body": "E",
                  "checked": true,
                  "isChild": false,
                  "order": 4
                }
              ],
              "reminders": [
                {
                  "id": 0,
                  "dateTime": 1742822940000,
                  "repetition": "{\"value\":1,\"unit\":\"DAYS\"}"
                }
              ]
            }
        """

        val baseNote = json.toBaseNote()

        assertEquals("Test", baseNote.title)
        assertEquals(false, baseNote.pinned)
        assertEquals("#E2F6D3", baseNote.color)
        assertEquals(Folder.NOTES, baseNote.folder)
        assertEquals(
            mutableListOf(
                ListItem("A", false, false, 0, mutableListOf()),
                ListItem("B", true, true, 1, mutableListOf()),
                ListItem("C", false, true, 2, mutableListOf()),
                ListItem("D", false, false, 3, mutableListOf()),
                ListItem("E", true, false, 4, mutableListOf()),
            ),
            baseNote.items,
        )
        assertEquals(1, baseNote.reminders.size)
        assertEquals(Repetition(1, RepetitionTimeUnit.DAYS), baseNote.reminders[0].repetition)
    }
}
