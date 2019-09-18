package org.pgscala.embedded

import java.io.File

object Home {
  lazy val root: File = new File(sys.props.getOrElse("pgscala-embedded", sys.props("user.home") + "/.pgscala-embedded")).getAbsoluteFile
  lazy val original: File = new File(root, "original")
  lazy val cached: File = new File(root, "cached")
}
