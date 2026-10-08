package dev.milinko.movielens

import dev.milinko.movielens.util.AppLocale
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Zamenjuje Dispatchers.Main test dispečerom (viewModelScope ga koristi) i lokalizovane stringove
 * (AppLocale traži Android Context) predvidljivim tekstom "res:<id>".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
        mockkObject(AppLocale)
        every { AppLocale.string(any(), *anyVararg()) } answers { "res:${firstArg<Int>()}" }
        every { AppLocale.currentTag() } returns "sr"
    }

    override fun finished(description: Description) {
        unmockkObject(AppLocale)
        Dispatchers.resetMain()
    }
}
