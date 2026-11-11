package com.univote.evoting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.univote.evoting.data.local.DatabaseSeeder
import com.univote.evoting.data.local.UniVoteDatabase
import com.univote.evoting.data.local.entity.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {

    private lateinit var db: UniVoteDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UniVoteDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seed_populatesCoreTables() = runBlocking {
        DatabaseSeeder(db).seedIfEmpty()

        assertEquals(5, db.departmentDao().count())
        assertEquals(12, db.voterDao().observeCount().first())
        assertEquals(6, db.userDao().observeCount().first())
        assertEquals(Role.ELECTION_ADMIN, db.userDao().findByUsername("admin")?.role)
    }

    @Test
    fun seed_storesHashedPasswordsOnly() = runBlocking {
        DatabaseSeeder(db).seedIfEmpty()

        val admin = db.userDao().findByUsername("admin")
        assertNotNull(admin)
        assertEquals(false, admin!!.passwordHash.contains("Admin@123"))
    }

    @Test
    fun seed_runTwice_doesNotDuplicate() = runBlocking {
        val seeder = DatabaseSeeder(db)
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()

        assertEquals(5, db.departmentDao().count())
        assertEquals(12, db.voterDao().observeCount().first())
    }

    @Test
    fun voterRoll_unknownRegNumber_returnsNull() = runBlocking {
        DatabaseSeeder(db).seedIfEmpty()
        assertNull(db.voterDao().findByRegNumber("XYZ/9999/2099"))
    }
}
