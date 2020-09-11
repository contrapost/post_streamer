package me.contrapost.tweetstreamdemosc.util

import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

fun getStream(bearerToken: String): InputStreamReader? {
    val httpClient: HttpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build()
        )
        .build()
    val uriBuilder = URIBuilder("https://api.twitter.com/2/tweets/search/stream?tweet.fields=created_at&expansions=author_id")
    val httpGet = HttpGet(uriBuilder.build())
    httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken))
    val response = httpClient.execute(httpGet)
    println(response)
    return when (val entity = response.entity) {
        null -> null
        else -> InputStreamReader(entity.content)
    }
}

/*
* Helper method to setup rules before streaming data
* */
fun setupRules(bearerToken: String, userName: String) {
    val existingRules: List<String> = getRules(bearerToken)
    if (existingRules.isNotEmpty()) deleteRules(bearerToken, existingRules)
    createRules(bearerToken, userName)
}

/*
* Helper method to create rules for filtering
* */
private fun createRules(bearerToken: String, userName: String) {
    val httpClient: HttpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build()
        )
        .build()
    val uriBuilder = URIBuilder("https://api.twitter.com/2/tweets/search/stream/rules")
    val httpPost = HttpPost(uriBuilder.build())
    httpPost.setHeader("Authorization", String.format("Bearer %s", bearerToken))
    httpPost.setHeader("content-type", "application/json")
    val body = StringEntity(
        """
            {
                "add": [
                    {
                        "value": "from:$userName"
                    }
                ]
            }
        """
    )
    httpPost.entity = body
    val response = httpClient.execute(httpPost)
    val entity = response.entity
    if (null != entity) {
        println("RULES")
        println(EntityUtils.toString(entity, "UTF-8"))
    }
}

private fun getRules(bearerToken: String): List<String> {
    val rules: MutableList<String> = ArrayList()
    val httpClient: HttpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build()
        )
        .build()
    val uriBuilder = URIBuilder("https://api.twitter.com/2/tweets/search/stream/rules")
    val httpGet = HttpGet(uriBuilder.build())
    httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken))
    httpGet.setHeader("content-type", "application/json")
    val response: HttpResponse = httpClient.execute(httpGet)
    val entity: HttpEntity = response.entity
    val json = JSONObject(EntityUtils.toString(entity, "UTF-8"))
    if (json.length() > 1) {
        val array: JSONArray = json.get("data") as JSONArray
        for (i in 0 until array.length()) {
            val jsonObject: JSONObject = array.get(i) as JSONObject
            rules.add(jsonObject.getString("id"))
        }
    }
    return rules
}

private fun deleteRules(bearerToken: String, existingRules: List<String>) {
    val httpClient: HttpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build()
        )
        .build()
    val uriBuilder = URIBuilder("https://api.twitter.com/2/tweets/search/stream/rules")
    val httpPost = HttpPost(uriBuilder.build())
    httpPost.setHeader("Authorization", String.format("Bearer %s", bearerToken))
    httpPost.setHeader("content-type", "application/json")
    val body = StringEntity(getFormattedString("{ \"delete\": { \"ids\": [%s]}}", existingRules))
    httpPost.entity = body
    val response = httpClient.execute(httpPost)
    val entity = response.entity
    if (null != entity) {
        println("DELETE")
        println(EntityUtils.toString(entity, "UTF-8"))
    }
}

private fun getFormattedString(string: String, ids: List<String>): String? {
    val sb = StringBuilder()
    return if (ids.size == 1) {
        String.format(string, "\"" + ids[0] + "\"")
    } else {
        for (id in ids) {
            sb.append("\"$id\",")
        }
        val result = sb.toString()
        String.format(string, result.substring(0, result.length - 1))
    }
}
