package fm.sbt

import sbt.librarymanagement.RawRepository

object CrossSbtUtils {
  def rawRepository(resolver: S3URLResolver, name: String) = new RawRepository(resolver, name)
}
