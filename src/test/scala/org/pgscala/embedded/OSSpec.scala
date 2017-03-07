package org.pgscala.embedded

class OSSpec extends EmbeddedSpec {
  def is = s2"""
    use-cases   ${checkUseCaseVersions}
    resolution  ${checkThisResolution}
"""

  def checkUseCaseVersions = {
    import OS.Architecture._
    import OS.Name._

    OS.values ==== IndexedSeq(
      OS(Linux, AMD64)
    , OS(Linux, X86)
    , OS(Windows, AMD64)
    , OS(Windows, X86)
    , OS(Mac, PPC)
    )
  }

  def checkThisResolution = {
    OS.resolved must beSome[OS]
  }
}
