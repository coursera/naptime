package org.coursera.naptime.sbt

import scala.tools.nsc.doc.base.comment._

object CommentRenderer {
  def renderBlock(block: Block): String = {
    block match {
      case Title(text, level) =>
        s"<h$level>${renderInline(text)}</h$level>"
      case Paragraph(text) =>
        s"<p>${renderInline(text)}</p>"
      case Code(data) =>
        s"<pre>$data</pre>"
      case UnorderedList(items) =>
        val renderedItems = items.map(item => s"<li>${renderBlock(item)}</li>")
        s"<ul>$renderedItems</ul>"
      case OrderedList(items, style) =>
        val renderedItems = items.map(item => s"<li>${renderBlock(item)}</li>")
        s"<ol>${items.map(item => s"<li>${renderBlock(item)}</li>")}</ol>"
      case DefinitionList(items) =>
        val renderedItems = items.map { case (term, definition) =>
          s"<dt>${renderInline(term)}</dt><dd>${renderBlock(definition)}</dd>"
        }
        s"<dl>$renderedItems</dl>"
      case HorizontalRule() =>
        "<hr>"
    }
  }

  def renderInline(inline: Inline): String = {
    inline match {
      case Chain(items) =>
        items.map(renderInline).mkString
      case Italic(text) =>
        s"<i>${renderInline(text)}</i>"
      case Bold(text) =>
        s"<b>${renderInline(text)}</b>"
      case Underline(text) =>
        s"<u>${renderInline(text)}</u>"
      case Superscript(text) =>
        s"<sup>${renderInline(text)}</sup>"
      case Subscript(text) =>
        s"<sub>${renderInline(text)}</sub>"
      case Link(target, title) =>
        // TODO(yifan): actually link to target
        s"<a>${renderInline(title)}</a>"
      case Monospace(text) =>
        s"<i>${renderInline(text)}</i>"
      case Text(text) =>
        StringEscaper.escapeHtml(text)
      case EntityLink(title, linkTo) =>
        // TODO(yifan): actually link to target
        s"<a>${renderInline(title)}</a>"
      case Summary(text) =>
        renderInline(text)
      case HtmlTag(data) =>
        data
    }
  }
}
