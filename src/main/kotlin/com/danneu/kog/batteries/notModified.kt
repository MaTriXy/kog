package com.danneu.kog.batteries

import com.danneu.kog.Method
import com.danneu.kog.Middleware
import com.danneu.kog.Request
import com.danneu.kog.Response
import com.danneu.kog.ResponseBody
import com.danneu.kog.Status
import com.danneu.kog.util.HttpDate
import org.joda.time.DateTime


fun notModified(etag: Boolean): Middleware = { handler -> handler@ { request ->
    val response = handler(request)

    // only consider HEAD and GET requests
    if (request.method != Method.get && request.method != Method.head) {
        return@handler response
    }

    // only consider 200 responses
    if (response.status != Status.ok) {
        return@handler response
    }

    // add etag header
    if (etag) {
        response.setHeader("ETag", response.body.etag())
    }

    // add last-modified header if body has that info
    response.body.apply {
        if (this is ResponseBody.File) {
            response.setHeader("Last-Modified", HttpDate.toString(DateTime(body.lastModified())))
        }
    }

    // only consider stale requests
    if (!isCached(request, response)) {
        return@handler response
    }

    // tell client that their cache is still valid
    Response(Status.notModified)
}}


private fun isCached(request: Request, response: Response): Boolean {
    return etagsMatch(request, response) || notModifiedSince(request, response)
}


private fun notModifiedSince(request: Request, response: Response): Boolean {
    // ensure headers exist
    val modifiedAtString = response.getHeader("last-modified") ?: return false
    val targetString = request.getHeader("if-modified-since") ?: return false

    // ensure headers parse into dates
    val modifiedAt = HttpDate.fromString(modifiedAtString) ?: return false
    val target = HttpDate.fromString(targetString) ?: return false

    // has entity not been touched since client's target?
    return modifiedAt < target
}


private fun etagsMatch(request: Request, response: Response): Boolean {
    val etag = response.getHeader("etag") ?: return false
    val target = request.getHeader("if-none-match") ?: return false
    return etag == target
}
