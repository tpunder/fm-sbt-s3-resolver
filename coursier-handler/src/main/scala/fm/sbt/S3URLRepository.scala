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

import java.net.URL
import java.util.List
import org.apache.ivy.plugins.repository.url.URLRepository
import scala.collection.JavaConverters._

final class S3URLRepository extends URLRepository {
  private[this] val s3: S3URLHandler = new S3URLHandler()
  
  override def list(parent: String): List[String] = {
    if (parent.startsWith("s3")) {
      s3.list(new URL(parent)).map{ _.toExternalForm }.asJava
    } else {
      super.list(parent)
    }
  }
}