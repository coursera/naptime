package org.coursera.naptime.sbt

import sbt.File

import scala.reflect.internal.util.Position
import scala.tools.nsc.Global
import scala.tools.nsc.doc.DocFactory
import scala.tools.nsc.doc.Settings
import scala.tools.nsc.doc.base.CommentFactoryBase
import scala.tools.nsc.doc.base.LinkTo
import scala.tools.nsc.doc.base.LinkToExternal
import scala.tools.nsc.doc.base.LinkToMember
import scala.tools.nsc.doc.base.MemberLookupBase
import scala.tools.nsc.doc.base.comment.Comment
import scala.tools.nsc.doc.model.MemberEntity
import scala.tools.nsc.reporters.AbstractReporter

object ScaladocExtractor {

  class ScaladocCommentFactory(compiler: Global, settings: Settings)
    extends CommentFactoryBase with MemberLookupBase {

    override val global: compiler.type = compiler

    override def internalLink(sym: global.Symbol, site: global.Symbol): Option[LinkTo] = None

    override def findExternalLink(sym: global.Symbol, name: String): Option[LinkToExternal] = None

    override def warnNoLink: Boolean = false

    override def toString(link: LinkTo): String = link.toString

    override def chooseLink(links: List[LinkTo]): LinkTo = {
      val members = links.collect {
        case linkToMember@LinkToMember(member: MemberEntity, _) => (member, linkToMember)
      }
      if (members.isEmpty) {
        links.head
      } else {
        members.min(Ordering[MemberEntity].on[(MemberEntity, LinkTo)](_._1))._2
      }
    }

    private[this] def parseComment(symbol: compiler.Symbol, docComment: compiler.DocComment): Comment = {
      parseAtSymbol(docComment.raw, "", docComment.pos, Some(symbol))
    }

    def getComments: Map[String, Comment] = {
      compiler.docComments.map { case (symbol, docComment) =>
        symbol.fullName -> parseComment(symbol, docComment)
      }.toMap
    }
  }

  def analyze(sourceFiles: Seq[File]): Map[String, Comment] = {
    val settings = new Settings(error => (), message => ())
    val scalaLibraryPath = ScalaLibraryLocator.getPath.getOrElse {
      throw new Exception("Could not get path to SBT's Scala library for Scaladoc generation")
    }
    /**
     * Provide Scala library to the new compiler instance.
     * This has to be SBT's Scala 2.10, not the compile target's Scala 2.11.
     * See: http://stackoverflow.com/a/26413909
     */
    settings.bootclasspath.append(scalaLibraryPath)
    settings.classpath.append(scalaLibraryPath)

    val reporter = new BlackHoleReporter(settings)
    val docFactory = new DocFactory(reporter, settings)
    val compiler = docFactory.compiler
    docFactory.makeUniverse(Left(sourceFiles.toList.map(_.getAbsolutePath)))

    val commentFactory = new ScaladocCommentFactory(compiler, settings)
    commentFactory.getComments
  }
}

class BlackHoleReporter(override val settings: Settings) extends AbstractReporter {
  override def display(pos: Position, msg: String, severity: Severity): Unit = ()
  override def displayPrompt(): Unit = ()
}
