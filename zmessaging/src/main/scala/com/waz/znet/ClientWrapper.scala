/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.znet

import java.security.cert.X509Certificate
import javax.net.ssl._

import com.google.android.gms.security.ProviderInstaller
import com.koushikdutta.async.http.{AsyncHttpClient, AsyncHttpClientMiddleware, AsyncSSLEngineConfigurator}
import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils._
import org.apache.http.conn.ssl.{AbstractVerifier, StrictHostnameVerifier}

import scala.concurrent.Future

trait ClientWrapper extends (AsyncHttpClient => Future[AsyncHttpClient])

/**
 * Wrapper for instrumenting of AsyncHttpClient, by default is empty, but will be replaced in tests.
  */
object ClientWrapper extends ClientWrapper {
  import Threading.Implicits.Background

  val domains @ Seq(zinfra, wire, cloudfront) = Seq("zinfra.io", "wire.com", "cloudfront.net")

  val installGmsCoreOpenSslProvider = Future (try {
    ProviderInstaller.installIfNeeded(ZMessaging.context)
  } catch {
    case t: Throwable => debug("Looking up GMS Core OpenSSL provider failed fatally.") // this should only happen in the tests
  })

  def apply(client: AsyncHttpClient): Future[AsyncHttpClient] = installGmsCoreOpenSslProvider map { _ =>
    // using specific hostname verifier to ensure compatibility with `isCertForDomain` (below)
    client.getSSLSocketMiddleware.setHostnameVerifier(new StrictHostnameVerifier)
    client.getSSLSocketMiddleware.setTrustManagers(Array(new X509TrustManager {
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
        debug(s"checking certificate for authType $authType, name: ${chain(0).getSubjectDN.getName}")
        chain.headOption.fold(throw new SSLException("expected at least one certificate!")) { cert =>
          val tm = if (isCertForDomain(zinfra, cert) || isCertForDomain(wire, cert)) {
            verbose("using backend trust manager")
            ServerTrust.backendTrustManager
          }
          else if (isCertForDomain(cloudfront, cert)) {
            verbose("using cdn trust manager")
            ServerTrust.cdnTrustManager
          }
          else {
            verbose("using system trust manager")
            ServerTrust.systemTrustManager
          }
          try {
            tm.checkServerTrusted(chain, authType)
          } catch {
            case e: Throwable =>
              error("certificate check failed", e)
              throw e
          }
        }
      }

      override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = throw new SSLException("unexpected call to checkClientTrusted")

      override def getAcceptedIssuers: Array[X509Certificate] = throw new SSLException("unexpected call to getAcceptedIssuers")

      /**
        * Checks if certificate matches given domain.
        * This is used to check if currently verified server is known to wire, and we should do certificate pinning for it.
        *
        * Warning: it's very important that this implementation matches used HostnameVerifier.
        * If HostnameVerifier accepts this cert with some wire sub-domain then this function must return true,
        * otherwise pinning will be skipped and we risk MITM attack.
        */
      private def isCertForDomain(domain: String, cert: X509Certificate): Boolean = {
        def iter(arr: Array[String]) = Option(arr).fold2(Iterator.empty, _.iterator)
        (iter(AbstractVerifier.getCNs(cert)) ++ iter(AbstractVerifier.getDNSSubjectAlts(cert))).exists(_.endsWith(s".$domain"))
      }
    }))

    client.getSSLSocketMiddleware.setSSLContext(returning(SSLContext.getInstance("TLSv1.2")) { _.init(null, null, null) })

    client.getSSLSocketMiddleware.addEngineConfigurator(new AsyncSSLEngineConfigurator {
      override def configureEngine(engine: SSLEngine, data: AsyncHttpClientMiddleware.GetSocketData, host: String, port: Int): Unit = {
        debug(s"configureEngine($host, $port)")

        if (domains.exists(host.endsWith)) {
          verbose("restricting to TLSv1.2")
          engine.setSSLParameters(returning(new SSLParameters) { params =>
            if (engine.getSupportedProtocols.contains(protocol)) params.setProtocols(Array(protocol))
            else warn(s"$protocol not supported by this device, falling back to defaults.")

            if (engine.getSupportedCipherSuites.contains(cipherSuite)) params.setCipherSuites(Array(cipherSuite))
            else warn(s"cipher suite $cipherSuite not supported by this device, falling back to defaults.")
          })
        }
      }
    })

    client
  }

  val protocol = "TLSv1.2"
  val cipherSuite = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
}
