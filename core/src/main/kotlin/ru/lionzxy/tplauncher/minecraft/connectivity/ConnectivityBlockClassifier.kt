package ru.lionzxy.tplauncher.minecraft.connectivity

import java.net.SocketException

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
                val m = e.message?.lowercase().orEmpty()
                if ("permission denied" in m || "wsaeacces" in m || "10013" in m) return true
            }
            e = e.cause
        }
        return false
    }
}
