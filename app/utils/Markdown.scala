package utils

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

import java.util.{List => JList}

object Markdown {
  private val extensions: JList[Extension] =
    java.util.Arrays.asList(
      TablesExtension.create(),
      AutolinkExtension.create()
    )

  private val parser: Parser = Parser.builder().extensions(extensions).build()

  private val renderer: HtmlRenderer = HtmlRenderer
    .builder()
    .extensions(extensions)
    .softbreak("<br/>\n")
    .build()

  def render(markdown: String): String = {
    if (markdown == null || markdown.isEmpty) ""
    else renderer.render(parser.parse(markdown))
  }
}
