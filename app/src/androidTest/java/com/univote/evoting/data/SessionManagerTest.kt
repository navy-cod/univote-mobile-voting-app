package com.univote.evoting.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.core.session.SessionManager
import com.univote.evoting.data.local.entity.Role
import com.univote.evoting.data.repository.SessionUser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val user = SessionUser(1, "admin", "Election Administrator", Role.ELECTION_ADMIN, null)
    private var now = 1_000_000L
    private lateinit var session: SessionManager

    @Before
    fun setUp() {
        context.getSharedPreferences(SessionManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        session = SessionManager(context) { now }
    }

    @Test
    fun start_setsCurrentUser() {
        session.start(user)
        assertNotNull(session.currentUser.value)
    }

    @Test
    fun validate_afterTimeout_endsSession() {
        session.start(user)
        now += SessionManager.SESSION_TIMEOUT_MS + 1
        session.validate()
        assertNull(session.currentUser.value)
    }

    @Test
    fun touch_withinTimeout_extendsSession() {
        session.start(user)
        now += SessionManager.SESSION_TIMEOUT_MS - 1_000
        session.touch()
        now += SessionManager.SESSION_TIMEOUT_MS - 1_000
        session.validate()
        assertNotNull(session.currentUser.value)
    }

    @Test
    fun end_clearsSession_evenForNewInstance() {
        session.start(user)
        session.end()
        assertNull(SessionManager(context) { now }.currentUser.value)
    }

    @Test
    fun newInstance_restoresActiveSession() {
        session.start(user)
        assertNotNull(SessionManager(context) { now }.currentUser.value)
    }
}