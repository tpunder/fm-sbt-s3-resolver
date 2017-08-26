package fm.sbt

import sbt.librarymanagement.RawRepository

object CrossSbtUtils {
  def rawRepository(resolver: S3URLResolver) = new RawRepository(resolver, resolver.getName)
}
