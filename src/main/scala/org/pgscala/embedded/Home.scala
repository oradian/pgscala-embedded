package org.pgscala.embedded

import java.io.File

object Home {
  lazy val root = new File(sys.props.getOrElse(
    "pgscala-embedded"
  , sys.props("user.home") + "/.pgscala-embedded"
  )).getAbsoluteFile

  lazy val original = new File(root, "original")
  lazy val cached = new File(root, "cached")
}
