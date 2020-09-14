package me.contrapost.poststreamdemosc.util

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients

fun String.getResponse(token: String): HttpResponse {
    val httpClient: HttpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build()
        )
        .build()
    val uriBuilder = URIBuilder(this)
    val httpGet = HttpGet(uriBuilder.build())
    httpGet.setHeader("Authorization", String.format("Bearer %s", token))
    return httpClient.execute(httpGet)
}
