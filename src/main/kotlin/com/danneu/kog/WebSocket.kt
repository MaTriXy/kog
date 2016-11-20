package com.danneu.kog

import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.websocket.server.WebSocketHandler
import org.eclipse.jetty.server.Request as JettyServerRequest


class WebSocket(val session: Session) {
    var onClose: (statusCode: Int, reason: String?) -> Unit = { a, b -> /* noop */ }
    var onError: (cause: Throwable) -> Unit = { /* noop */ }
    var onText: (message: String) -> Unit = { /* noop */ }
    var onBinary: (payload: ByteArray, offset: Int, len: Int) -> Unit = { a, b, c -> /* noop */ }

    companion object
}


fun WebSocket.Companion.adapter(request: Request, accept: WebSocketAcceptor): WebSocketAdapter {
    return object : WebSocketAdapter() {
        var socket: WebSocket? = null

        override fun onWebSocketConnect(session: Session) {
            super.onWebSocketConnect(session)
            this.socket = WebSocket(session)
            accept(request, this.socket!!)
        }

        // TODO: Find a better way to guarantee socket.session non-nullable downstream instead of with `!!`

        override fun onWebSocketError(cause: Throwable) {
            super.onWebSocketError(cause)
            this.socket!!.onError(cause)
        }

        override fun onWebSocketClose(statusCode: Int, reason: String?) {
            super.onWebSocketClose(statusCode, reason)
            this.socket!!.onClose(statusCode, reason)
        }

        override fun onWebSocketText(message: String) {
            super.onWebSocketText(message)
            this.socket!!.onText(message)
        }

        override fun onWebSocketBinary(payload: ByteArray, offset: Int, len: Int) {
            super.onWebSocketBinary(payload, offset, len)
            this.socket!!.onBinary(payload, offset, len)
        }
    }
}


fun WebSocket.Companion.handler(key: String, accept: WebSocketAcceptor): WebSocketHandler {
    var request: Request? = null
    return object : WebSocketHandler() {
        override fun configure(factory: WebSocketServletFactory) {
            // TODO: Allow idletimeout config. factory.policy.idleTimeout = idleTimeout
            factory.creator = WebSocketCreator { req, res -> WebSocket.adapter(request!!, accept) }
        }
        override fun handle(target: String, baseReq: JettyServerRequest, req: HttpServletRequest, res: HttpServletResponse) {
            val factory = this.webSocketFactory

            if (key !== req.getAttribute("ws-handler")) {
                // go to next handler
                return
            }

            // HACK: Saving into upstream variable so configure() can read it
            request = req.getAttribute("kog-request") as Request

            if (factory.acceptWebSocket(req, res)) {
                baseReq.isHandled = true
            } else {
                if (res.isCommitted) { baseReq.isHandled = true }
            }
        }
    }
}
