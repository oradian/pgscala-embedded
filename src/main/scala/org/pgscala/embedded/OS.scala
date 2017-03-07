package org.pgscala.embedded

import java.util.Locale

class OS private (val name: OS.Name, val architecture: OS.Architecture) {
  override def toString = name.classifier + architecture.classifier
}

object OS {
  sealed abstract class Name private (val classifier: String, val archiveMode: String)
  object Name {
    case object Linux extends Name("linux", "tar.gz")
    case object Windows extends Name("windows", "zip")
    case object Mac extends Name("osx", "zip")
    val values: IndexedSeq[Name] = IndexedSeq(Linux, Windows, Mac)

    lazy val resolved: Option[Name] = {
      val propsOsName = sys.props("os.name").toLowerCase(Locale.ROOT)
      Name.values.find(os => propsOsName.contains(os.toString.toLowerCase(Locale.ROOT)))
    }
  }

  sealed abstract class Architecture private (val classifier: String)
  object Architecture {
    case object AMD64 extends Architecture("-x64")
    case object X86 extends Architecture("")
    case object PPC extends Architecture("")
    val values: IndexedSeq[Architecture] = IndexedSeq(AMD64, X86, PPC)

    lazy val resolved: Option[Architecture] = {
      val propsOsArch = sys.props("os.arch")
      Architecture.values.find(os => propsOsArch.toString.equalsIgnoreCase(os.toString))
    }
  }

  lazy val values: IndexedSeq[OS] = for {
    name <- Name.values
    architecture <- Architecture.values
    if name != Name.Mac && architecture != Architecture.PPC ||
       name == Name.Mac && architecture == Architecture.PPC
  } yield new OS(name, architecture)

  def apply(name: Name, architecture: Architecture): OS = values find { os =>
    os.name == name && os.architecture == architecture
  } getOrElse (
    sys.error(s"""Combination of OS name "$name" and architecture "$architecture" is not supported!""")
  )

  lazy val resolved = for {
    name <- Name.resolved
    architecture <- Architecture.resolved
  } yield apply(name, architecture)
}
