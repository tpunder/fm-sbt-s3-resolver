package fm.sbt

import sbt.RawRepository

object CrossSbtUtils {
  def rawRepository(resolver: S3URLResolver, name: String) = new RawRepository(resolver)
}
