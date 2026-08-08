package dev.bartuzen.qbitcontroller.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

actual fun createProwlarrHttpClient(trustSelfSignedCertificates: Boolean, block: HttpClientConfig<*>.() -> Unit) =
    HttpClient(OkHttp) {
        block()

        engine {
            config {
                retryOnConnectionFailure(true)

                if (trustSelfSignedCertificates) {
                    val trustAllManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}

                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }

                    val sslContext = SSLContext.getInstance("SSL")
                    sslContext.init(null, arrayOf(trustAllManager), SecureRandom())
                    sslSocketFactory(sslContext.socketFactory, trustAllManager)
                    hostnameVerifier { _, _ -> true }
                }
            }
        }
    }
