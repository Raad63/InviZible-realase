/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.web

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.CHROME_BROWSER_USER_AGENT
import pan.alexander.tordnscrypt.utils.Constants.DEFAULT_PROXY_PORT
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.Constants.MAX_PORT_NUMBER
import pan.alexander.tordnscrypt.utils.Constants.NUMBER_REGEX
import pan.alexander.tordnscrypt.utils.connectionchecker.ProxyAuthManager.setDefaultAuth
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_ADDRESS
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PASS
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PORT
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_USER
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession

class HttpsConnectionManager @Inject constructor(
    private val context: Context,
    private val pathVars: PathVars,
    @Named(DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: SharedPreferences,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher
) {

    var readTimeoutSec = 180
    var connectTimeoutSec = 180

    @Throws(IOException::class)
    fun get(url: String, block: (inputStream: InputStream) -> Unit) {

        val httpsURLConnection = getHttpsUrlConnection(url)

        try {
            httpsURLConnection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                connectTimeout = 1000 * connectTimeoutSec
                readTimeout = 1000 * readTimeoutSec
            }.connect()

            val response = httpsURLConnection.responseCode
            if (response == HTTP_OK) {
                block(httpsURLConnection.inputStream)
            } else {
                throw IOException("HttpsConnectionManager $url response code $response")
            }
        } finally {
            httpsURLConnection.disconnect()
        }
    }

    @Throws(IOException::class)
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun get(url: String, data: Map<String, String>): List<String> =
        withContext(dispatcherIo) {

            val query = mapToQuery(data)

            val httpsURLConnection = getHttpsUrlConnection("$url?$query")

            try {
                httpsURLConnection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                    connectTimeout = 1000 * connectTimeoutSec
                    readTimeout = 1000 * readTimeoutSec
                }.connect()

                val response = httpsURLConnection.responseCode
                if (response == HTTP_OK) {
                    mutableListOf<String>().also { lines ->
                        httpsURLConnection.inputStream.bufferedReader().useLines {
                            it.forEach { line ->
                                if (!isActive) {
                                    return@forEach
                                }
                                lines.add(line)
                            }
                        }
                    }
                } else {
                    throw IOException("HttpsConnectionManager $url response code $response")
                }
            } finally {
                httpsURLConnection.disconnect()
            }
        }

    @Throws(IOException::class)
    fun post(url: String, data: Map<String, String>, block: (inputStream: InputStream) -> Unit) {

        val httpsURLConnection = getHttpsUrlConnection(url)

        try {
            val query = mapToQuery(data)

            httpsURLConnection.apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                setRequestProperty(
                    "Content-Length",
                    query.toByteArray().size.toString()
                )
                doOutput = true
                connectTimeout = 1000 * connectTimeoutSec
                readTimeout = 1000 * readTimeoutSec
            }.connect()

            httpsURLConnection.outputStream.bufferedWriter().use {
                it.write(query)
                it.flush()
            }

            val response = httpsURLConnection.responseCode
            if (response == HTTP_OK) {
                block(httpsURLConnection.inputStream)
            } else {
                throw IOException("HttpsConnectionManager $url response code $response")
            }
        } finally {
            httpsURLConnection.disconnect()
        }

    }

    @Throws(IOException::class)
    fun post(url: String, data: Map<String, String>): List<String> {

        val httpsURLConnection = getHttpsUrlConnection(url)

        val lines = try {
            val query = mapToQuery(data)

            httpsURLConnection.apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                setRequestProperty(
                    "Content-Length",
                    query.toByteArray().size.toString()
                )
                doOutput = true
                connectTimeout = 1000 * connectTimeoutSec
                readTimeout = 1000 * readTimeoutSec
            }.connect()

            httpsURLConnection.outputStream.bufferedWriter().use {
                it.write(query)
                it.flush()
            }

            val response = httpsURLConnection.responseCode
            if (response == HTTP_OK) {
                mutableListOf<String>().also { lines ->
                    httpsURLConnection.inputStream.bufferedReader().useLines {
                        it.forEach { line ->
                            if (!Thread.currentThread().isInterrupted) {
                                lines.add(line)
                            } else {
                                throw CancellationException(
                                    "HttpsConnectionManager post $url is cancelled"
                                )
                            }
                        }
                    }
                }
            } else {
                throw IOException("HttpsConnectionManager $url response code $response")
            }

        } finally {
            httpsURLConnection.disconnect()
        }

        return lines
    }

    fun getHttpsUrlConnection(url: String): HttpsURLConnection {
        val modulesStatus = ModulesStatus.getInstance()
        val proxyAddress =
            defaultPreferences.getString(PROXY_ADDRESS, LOOPBACK_ADDRESS) ?: LOOPBACK_ADDRESS
        val proxyPort = defaultPreferences.getString(PROXY_PORT, DEFAULT_PROXY_PORT).let {
            if (it?.matches(Regex(NUMBER_REGEX)) == true && it.toLong() <= MAX_PORT_NUMBER) {
                it.toInt()
            } else {
                DEFAULT_PROXY_PORT.toInt()
            }
        }
        val useProxy = defaultPreferences.getBoolean(USE_PROXY, false)
                && proxyAddress.isNotBlank()
                && proxyPort != 0

        val proxy = if (modulesStatus.torState == ModuleState.RUNNING && modulesStatus.isTorReady) {
            //Use http proxy as older android versions use Socks4 proxy which leaks DNS
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                logi("Using tor http proxy for url connection")
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(
                        LOOPBACK_ADDRESS, pathVars.torHTTPTunnelPort.toInt()
                    )
                )
            } else {
                logi("Using tor socks proxy for url connection")
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(
                        LOOPBACK_ADDRESS, pathVars.torSOCKSPort.toInt()
                    )
                )
            }
        } else if (useProxy) {
            logi("Using socks proxy for url connection")
            val proxyUser = defaultPreferences.getString(PROXY_USER, "") ?: ""
            val proxyPass = defaultPreferences.getString(PROXY_PASS, "") ?: ""
            setDefaultAuth(proxyUser, proxyPass)
            Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress(
                    proxyAddress, proxyPort
                )
            )
        } else {
            logi("Using direct url connection")
            null
        }

        val urlConnection = URL(url)

        val httpsURLConnection = if (proxy == null) {
            urlConnection.openConnection() as HttpsURLConnection
        } else {
            urlConnection.openConnection(proxy) as HttpsURLConnection
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            httpsURLConnection.hostnameVerifier =
                HostnameVerifier { hostname: String, session: SSLSession ->
                    hostname == session.peerHost
                }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M && url.startsWith("https")) {
            tryGetCompatibleTlsSocketFactory()?.let {
                httpsURLConnection.sslSocketFactory = it
            }
        }

        return httpsURLConnection
    }

    private fun tryGetCompatibleTlsSocketFactory() = try {
        TLSSocketFactory(context)
    } catch (e: Exception) {
        loge("HttpsConnectionManager tryGetCompatibleTlsSocketFactory", e)
        null
    }

    private fun mapToQuery(data: Map<String, String>) = data.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }
}
