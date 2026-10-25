package net.pipebomb.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.InMemoryCookieStorage
import net.pipebomb.android.core.net.InMemoryServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("auth_bootstrap", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        server = MockWebServer()
        server.start()
        val factory = ApiClientFactory(
            serverUrlProvider = InMemoryServerUrlProvider(server.url("/").toString()),
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
            json = PipeBombJson,
            debugLogging = false,
        )
        repository = AuthRepository(
            context = ApplicationProvider.getApplicationContext(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            apiClientFactory = factory,
            cookieJar = PersistentCookieJar(InMemoryCookieStorage()),
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun cachedUserKeepsOfflineSessionAuthenticated() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"uuid":"user","username":"offline","privileges":[]}"""),
        )

        repository.login("offline", "password")
        server.shutdown()

        val result = repository.refresh()

        assertTrue(repository.session.value is SessionState.Authenticated)
        assertTrue((result as net.pipebomb.android.core.net.ApiResult.Failure).error is
            net.pipebomb.android.core.net.ApiError.Network)
    }
}
