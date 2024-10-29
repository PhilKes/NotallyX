package com.philkes.notallyx.data.imports

import com.philkes.notallyx.data.model.SpanRepresentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class HtmlUtilsTest {

    @Test
    fun `parseBodyAndSpansFromHtml Evernote`() {
        val contentHtml =
            """
            <en-note>
              <div>
                <b>This text needs to be fat</b>
              </div>
              <div>
                <br />
              </div>
              <div>
                <i>A very italic</i>
              </div>
              <div>
                <br />
              </div>
              <div>
                <s>Outdated stuff</s>
              </div>
              <div>
                <br />
              </div>
              <div>
                <span style="--en-fontfamily: monospace; font-family: &quot;Source Code Pro&quot;,monospace">
                  System.out.println("Super useful code");
                </span>
              </div>
              <div>
                <br />
              </div>
              <div>
                <a href="https://github.com/PhilKes/NotallyX">https://github.com/PhilKes/NotallyX</a>
              </div>
              <div>
                <br />
              </div>
              <div>
                <br />
              </div>
              <div>
                <br />
              </div>
              <div>
                <br />
              </div>
              <div>I want to format this</div>
            </en-note>
            """
                .trimIndent()

        val (body, spans) =
            parseBodyAndSpansFromHtml(contentHtml, "en-note", brTagsAsNewLine = false)

        assertThat(body)
            .isEqualTo(
                """
            This text needs to be fat

            A very italic

            Outdated stuff

            System.out.println("Super useful code");

            https://github.com/PhilKes/NotallyX




            I want to format this
            """
                    .trimIndent()
                    .trimMargin()
            )
        assertThat(spans)
            .hasSize(5)
            .anyMatch { body.substringOfSpan(it) == "This text needs to be fat" && it.bold }
            .anyMatch { body.substringOfSpan(it) == "A very italic" && it.italic }
            .anyMatch { body.substringOfSpan(it) == "Outdated stuff" && it.strikethrough }
            .anyMatch {
                body.substringOfSpan(it) == "System.out.println(\"Super useful code\");" &&
                    it.monospace
            }
            .anyMatch {
                body.substringOfSpan(it) == "https://github.com/PhilKes/NotallyX" &&
                    it.link &&
                    it.linkData.equals("https://github.com/PhilKes/NotallyX")
            }
    }

    @Test
    fun `parseBodyAndSpansFromHtml Google Keep`() {
        val contentHtml =
            """
            <p dir="ltr" style="line-height:1.38;margin-top:0.0pt;margin-bottom:0.0pt;">
            <span
                style="font-size:16.0pt;font-family:'Google Sans';color:#000000;background-color:transparent;font-weight:700;font-style:normal;font-variant:normal;text-decoration:none;vertical-align:baseline;white-space:pre;white-space:pre-wrap;">
            This text needs to be fat</span>
            </p>
            <br/>
            <p dir="ltr" style="line-height:1.38;margin-top:0.0pt;margin-bottom:0.0pt;">
            <span
                style="font-size:16.0pt;font-family:'Google Sans';color:#000000;background-color:transparent;font-weight:400;font-style:italic;font-variant:normal;text-decoration:none;vertical-align:baseline;white-space:pre;white-space:pre-wrap;">
            A very italic</span>
            </p>
            <br/>
            <p dir="ltr" style="line-height:1.38;margin-top:0.0pt;margin-bottom:0.0pt;">
            <span
                style="font-size:16.0pt;font-family:'Google Sans';color:#000000;background-color:transparent;font-weight:400;font-style:normal;font-variant:normal;text-decoration:none;vertical-align:baseline;white-space:pre;white-space:pre-wrap;">
            Outdated stuff</span>
            </p>
            <br/>
            <p dir="ltr" style="line-height:1.38;margin-top:0.0pt;margin-bottom:0.0pt;">
            <span
                style="font-size:16.0pt;font-family:'Google Sans';color:#000000;background-color:transparent;font-weight:400;font-style:normal;font-variant:normal;text-decoration:none;vertical-align:baseline;white-space:pre;white-space:pre-wrap;">
            System.out.println(&quot;Super useful code&quot;);</span>
            </p>
            <br/>
            <p dir="ltr" style="line-height:1.38;margin-top:0.0pt;margin-bottom:0.0pt;">
            <span
                style="font-size:16.0pt;font-family:'Google Sans';color:#000000;background-color:transparent;font-weight:400;font-style:normal;font-variant:normal;text-decoration:underline;-webkit-text-decoration-skip:none;text-decoration-skip-ink:none;vertical-align:baseline;white-space:pre;white-space:pre-wrap;">
            https://github.com/PhilKes/NotallyX</span>
            </p>
            <br/>
            <br/>
            <br/>
            <br/>
            <p dir="ltr" style="line-height:1.38;margin-top:0.0pt;margin-bottom:0.0pt;">
            <span
                style="font-size:16.0pt;font-family:'Google Sans';color:#000000;background-color:transparent;font-weight:400;font-style:normal;font-variant:normal;text-decoration:underline;-webkit-text-decoration-skip:none;text-decoration-skip-ink:none;vertical-align:baseline;white-space:pre;white-space:pre-wrap;">
            I want to format this</span>
            </p>
            """
                .trimIndent()

        val (body, spans) =
            parseBodyAndSpansFromHtml(
                contentHtml,
                paragraphsAsNewLine = true,
                brTagsAsNewLine = true,
            )

        assertThat(body)
            .isEqualTo(
                """
            This text needs to be fat

            A very italic

            Outdated stuff

            System.out.println("Super useful code");

            https://github.com/PhilKes/NotallyX




            I want to format this
            """
                    .trimIndent()
                    .trimMargin()
            )
        assertThat(spans)
            .hasSize(3)
            .anyMatch { body.substringOfSpan(it) == "This text needs to be fat" && it.bold }
            .anyMatch { body.substringOfSpan(it) == "A very italic" && it.italic }
            .anyMatch {
                body.substringOfSpan(it) == "https://github.com/PhilKes/NotallyX" &&
                    it.link &&
                    it.linkData.equals("https://github.com/PhilKes/NotallyX")
            }
    }
}

fun String.substringOfSpan(span: SpanRepresentation): String {
    return substring(span.start, span.end)
}
