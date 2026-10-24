package net.pipebomb.android.core.net

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

interface CookieStorage {
    fun load(): List<StoredCookie>
    fun save(cookies: List<StoredCookie>)
    fun clear()
}

@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

class SharedPreferencesCookieStorage(context: Context) : CookieStorage {
    private val prefs = context.getSharedPreferences("pipebomb_cookies", Context.MODE_PRIVATE)

    override fun load(): List<StoredCookie> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            PipeBombJson.decodeFromString<List<StoredCookie>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun save(cookies: List<StoredCookie>) {
        prefs.edit().putString(KEY, PipeBombJson.encodeToString(cookies)).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY = "cookies"
    }
}

class InMemoryCookieStorage(initial: List<StoredCookie> = emptyList()) : CookieStorage {
    private var cookies = initial.toList()
    override fun load(): List<StoredCookie> = cookies
    override fun save(cookies: List<StoredCookie>) {
        this.cookies = cookies.toList()
    }
    override fun clear() {
        cookies = emptyList()
    }
}

class PersistentCookieJar(
    private val storage: CookieStorage,
    private val now: () -> Long = System::currentTimeMillis,
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val merged = storage.load().toMutableList()
        for (cookie in cookies) {
            merged.removeAll {
                it.name == cookie.name &&
                    it.domain == cookie.domain &&
                    it.path == cookie.path
            }
            merged += cookie.toStored()
        }
        storage.save(merged.filterNot { it.isExpired(now()) })
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val all = storage.load()
        val active = all.filterNot { it.isExpired(now()) }
        if (active.size != all.size) {
            storage.save(active)
        }
        return active.mapNotNull { it.toCookie() }.filter { it.matches(url) }
    }

    fun clear() {
        storage.clear()
    }
}

private fun Cookie.toStored(): StoredCookie = StoredCookie(
    name = name,
    value = value,
    domain = domain,
    path = path,
    expiresAt = expiresAt,
    secure = secure,
    httpOnly = httpOnly,
    hostOnly = hostOnly,
)

private fun StoredCookie.toCookie(): Cookie? {
    return runCatching {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .path(path)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (expiresAt > 0) builder.expiresAt(expiresAt)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        builder.build()
    }.getOrNull()
}

private fun StoredCookie.isExpired(now: Long): Boolean = expiresAt in 1 until now
