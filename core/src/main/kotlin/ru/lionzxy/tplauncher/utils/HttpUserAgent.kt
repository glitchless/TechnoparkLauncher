package ru.lionzxy.tplauncher.utils

const val HTTP_USER_AGENT = "TechnoparkLauncher"

/**
 * Cloudflare in front of *.glitchless.ru returns 403 (error 1010) for User-Agents that start with
 * "Java/", which is exactly what HttpURLConnection sends by default ("Java/<version>"). The UA is
 * composed as "${http.agent} Java/<version>", so giving `http.agent` any non-blank value moves
 * "Java/" off the front and the WAF lets the request through.
 *
 * Must run at startup, before the first HTTP connection: HttpURLConnection reads the UA once when
 * its class is loaded, so setting the property later has no effect.
 */
fun configureHttpUserAgent() {
    if (System.getProperty("http.agent").isNullOrBlank()) {
        System.setProperty("http.agent", HTTP_USER_AGENT)
    }
}
