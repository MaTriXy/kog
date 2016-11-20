package com.danneu.kog.adapters

import javax.servlet.http.HttpServlet
import com.danneu.kog.Handler
import com.danneu.kog.Method
import com.danneu.kog.Request
import com.danneu.kog.Response
import java.util.Locale
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


// Create a Servlet from a kog handler
class Servlet(val handler: Handler) : HttpServlet() {
    override fun service (servletRequest: HttpServletRequest, servletResponse: HttpServletResponse) {
        val request = intoKogRequest(servletRequest)
        val response = handler(request).finalize()
        updateServletResponse(servletResponse, response)
    }

    companion object {
        fun updateServletResponse(servletResponse: HttpServletResponse, response: Response) {
            servletResponse.status = response.status.code

            // Set headers
            for ((k, v) in response.headers.iterator()) {
                servletResponse.addHeader(k, v)
            }

            // Some headers have special setters
            val type = response.getHeader("Content-Type")
            if (type != null) {
                servletResponse.contentType = type
            }

            response.body.pipe(servletResponse.outputStream)
        }

        fun intoKogRequest(r: HttpServletRequest): Request {
            return Request(
              serverPort = r.serverPort,
              serverName = r.serverName,
              remoteAddr = r.remoteAddr,
              href = r.requestURL.toString() + if (r.queryString != null) { "?" + r.queryString } else { "" },
              queryString = r.queryString,
              scheme = r.scheme,
              method = Method.fromString(r.method.toLowerCase(Locale.ENGLISH)),
              protocol = r.protocol,
              headers = expandHeaders(r),
              type = r.contentType?.split(";", limit = 1)?.get(0)?.toLowerCase(),
              length = if (r.contentLength >= 0) { r.contentLength } else { null },
              charset = r.characterEncoding?.toLowerCase(),
              //sslClientCert = r.getAttribute("javax.servlet.request.X509Certificate").first()
              body = r.inputStream,
              path = r.pathInfo ?: "/"
            )
        }
    }
}


fun expandHeaders(r: HttpServletRequest): MutableList<Pair<String, String>> {
    return r.headerNames.asSequence().flatMap { name ->
        r.getHeaders(name).asSequence().map { value ->
            name to value
        }
    }.toMutableList()
}