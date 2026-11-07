package com.example.profilebrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** একটি প্রোফাইল: আলাদা কুকি/লগইন সহ একটি ব্রাউজার প্রোফাইল। */
data class Profile(
    val id: String,
    val name: String,
    val type: String, // "facebook" | "messenger" | "gmail" | "custom"
    val url: String
) {
    fun typeLabel(): String = when (type) {
        "facebook" -> "Facebook"
        "messenger" -> "Messenger"
        "gmail" -> "Gmail"
        else -> "ওয়েবসাইট"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("url", url)
    }

    companion object {
        fun defaultUrl(type: String): String = when (type) {
            "facebook" -> "https://www.facebook.com"
            "messenger" -> "https://www.messenger.com"
            "gmail" -> "https://mail.google.com"
            else -> "https://www.google.com"
        }

        fun fromJson(o: JSONObject): Profile = Profile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "প্রোফাইল"),
            type = o.optString("type", "facebook"),
            url = o.optString("url", defaultUrl(o.optString("type", "facebook")))
        )

        fun create(name: String, type: String, customUrl: String): Profile {
            val url = if (type == "custom") {
                val u = customUrl.trim()
                if (u.startsWith("http://") || u.startsWith("https://")) u
                else "https://$u"
            } else defaultUrl(type)
            return Profile(UUID.randomUUID().toString(), name.trim(), type, url)
        }
    }
}

/** প্রোফাইলগুলো SharedPreferences-এ JSON হিসেবে সংরক্ষণ করে। */
object ProfileStore {
    private const val PREFS = "profiles"
    private const val KEY = "profile_list"

    fun loadAll(ctx: Context): List<Profile> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(ctx: Context, profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    fun get(ctx: Context, id: String): Profile? =
        loadAll(ctx).firstOrNull { it.id == id }

    fun add(ctx: Context, profile: Profile): List<Profile> {
        val list = loadAll(ctx).toMutableList()
        list.add(profile)
        saveAll(ctx, list)
        return list
    }

    fun delete(ctx: Context, id: String): List<Profile> {
        val list = loadAll(ctx).filter { it.id != id }
        saveAll(ctx, list)
        return list
    }

    /** ফোনের Gmail অ্যাকাউন্টগুলো থেকে Gmail প্রোফাইল বানায় — যেগুলো নেই সেগুলোই।
        নাম হিসেবে পুরো ইমেইল ঠিকানাটা থাকে, যাতে কোনটা কোন Gmail বোঝা যায়। */
    fun addGmailProfiles(ctx: Context, emails: List<String>): List<Profile> {
        val list = loadAll(ctx).toMutableList()
        val existing = list.map { it.name }.toSet()
        emails.distinct().forEach { email ->
            if (email !in existing) {
                list.add(
                    Profile(
                        UUID.randomUUID().toString(),
                        email,
                        "gmail",
                        Profile.defaultUrl("gmail")
                    )
                )
            }
        }
        saveAll(ctx, list)
        return list
    }
}
