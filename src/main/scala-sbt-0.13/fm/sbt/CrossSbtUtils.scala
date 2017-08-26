package fm.sbt

import sbt.RawRepository

object CrossSbtUtils {
  def rawRepository(resolver: S3URLResolver) = new RawRepository(resolver)
}
