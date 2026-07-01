package ru.lionzxy.tplauncher.minecraft.connectivity

import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Recognises a Windows WSAEACCES (10013) "Permission denied" socket block — a firewall / antivirus
 * (e.g. Dr.Web) / VPN LSP refusing the JVM's outbound sockets. This is NOT a reachability failure
 * (that surfaces as [java.net.UnknownHostException] / connection timeout / connection refused), so
 * callers can treat a positive match as "the machine is blocking us, not the server is unreachable".
 */
object ConnectivityBlockClassifier {
    fun isPermissionDeniedSocket(t: Throwable?): Boolean {
        var e = t
        val seen = HashSet<Throwable>()
        while (e != null && seen.add(e)) {
            if (e is SocketException) {
                // JDK native code raises WSAEACCES as the fixed English strings
                // "Permission denied: connect" / "Permission denied: getsockopt"; "wsaeacces" covers
                // wrappers that embed the WSA name. A bare "10013" digit match is intentionally NOT
                // used: any host:port or byte count containing that sequence would false-positive.
                val m = e.message?.lowercase().orEmpty()
                if ("permission denied" in m || "wsaeacces" in m) return true
            }
            e = e.cause
        }
        return false
    }

    /**
     * True when [t] (or any cause) is an environmental network failure — offline, blocked, DNS-less,
     * timing out, or TLS-refused — as opposed to a genuine application/server error (an HTTP error
     * body, a parse failure, a broken file). Used to decide when a network step may degrade to
     * local/offline data instead of aborting (and being reported to Sentry as a bug).
     * [isPermissionDeniedSocket] matches imply this (WSAEACCES is a [SocketException]).
     */
    fun isEnvironmentalNetworkFailure(t: Throwable?): Boolean {
        var e = t
        val seen = HashSet<Throwable>()
        while (e != null && seen.add(e)) {
            when (e) {
                // SocketException covers ConnectException, NoRouteToHostException, BindException.
                is UnknownHostException, is SocketException, is SocketTimeoutException, is SSLException ->
                    return true
            }
            e = e.cause
        }
        return false
    }
}
