/*
 * Copyright 2014 Frugal Mechanic (http://frugalmechanic.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fm.sbt

import java.util.{Collections, List}
import sbt.{RawRepository, Resolver}

final class S3RawRepository(val name: String) extends AnyVal {
  def atS3(location: String): Resolver = {
    require(null != location && location != "", "Empty Location!")
    val pattern: List[String] = Collections.singletonList(resolvePattern(location, Resolver.mavenStyleBasePattern))
    new RawRepository(new S3URLResolver(name, location, pattern))
  }
  
  private def resolvePattern(base: String, pattern: String): String = {
    val normBase = base.replace('\\', '/')
    if(normBase.endsWith("/") || pattern.startsWith("/")) normBase + pattern else normBase + "/" + pattern
  }
}