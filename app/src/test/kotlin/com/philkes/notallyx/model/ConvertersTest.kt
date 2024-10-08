package com.philkes.notallyx.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    @Test
    fun `jsonToFiles input with localName and originalName fields`() {
        val json =
            """
            [
                {
                    "localName": "content://some/random/path/123435",
                    "originalName": "file1.pdf",
                    "mimeType": "application/pdf"
                },
                 {
                    "localName": "content://some/other/random/path/123435",
                    "originalName": "random.jpeg",
                    "mimeType": "image/jpeg"
                }
            ]
        """
                .trimIndent()

        val files = Converters.jsonToFiles(json)

        assertEquals(2, files.size)
        assertEquals(
            FileAttachment("content://some/random/path/123435", "file1.pdf", "application/pdf"),
            files[0],
        )
        assertEquals(
            FileAttachment("content://some/other/random/path/123435", "random.jpeg", "image/jpeg"),
            files[1],
        )
    }

    @Test
    fun `jsonToFiles input with only name field`() {
        val json =
            """
            [
                {
                    "name": "content://some/random/path/123435",
                    "mimeType": "application/pdf"
                },
                 {
                    "name": "content://some/other/random/path/123435",
                    "mimeType": "image/jpeg"
                }
            ]
        """
                .trimIndent()

        val files = Converters.jsonToFiles(json)

        assertEquals(2, files.size)
        assertEquals(
            FileAttachment("content://some/random/path/123435", "123435", "application/pdf"),
            files[0],
        )
        assertEquals(
            FileAttachment("content://some/other/random/path/123435", "123435", "image/jpeg"),
            files[1],
        )
    }
}
