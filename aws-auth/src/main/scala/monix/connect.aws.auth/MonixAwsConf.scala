/*
 * Copyright (c) 2020-2020 by The Monix Connect Project Developers.
 * See the project homepage at: https://connect.monix.io
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

package monix.connect.aws.auth

import monix.execution.internal.InternalApi
import software.amazon.awssdk.regions.Region
import pureconfig._
import pureconfig.generic.auto._
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import java.net.URI

import scala.language.implicitConversions

@InternalApi
private[connect] final case class MonixAwsConf(
  region: Region,
  credentials: AwsCredentialsProvider,
  endpoint: Option[URI],
  httpClient: Option[HttpClientConf])

@InternalApi
private[connect] object MonixAwsConf {

  object Implicits {
    implicit val credentialsProviderReader: ConfigReader[AwsCredentialsProvider] =
      ConfigReader[AwsCredentialsConf].map { credentialsConf => credentialsConf.credentialsProvider }
    implicit val providerReader: ConfigReader[Provider.Type] = ConfigReader[String].map(Provider.fromString(_))
    implicit val regionReader: ConfigReader[Region] = ConfigReader[String].map(Region.of(_))
    implicit val uriReader: ConfigReader[URI] = ConfigReader[String].map(URI.create(_))
  }
}
