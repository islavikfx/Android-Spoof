package com.islavikfx.spoof
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.google.gson.Gson


data class User(val author: String, val username: String)

class UnitTest {

    @Test
    fun parseJson() = runBlocking {
        val json = """{"author":"iSlavik","username":"@islavikfx"}"""
        val gson = Gson()
        val user: User = gson.fromJson(json, User::class.java)
        assertEquals("iSlavik", user.author)
        assertEquals("@islavikfx", user.username)

    }
}