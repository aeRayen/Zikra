package com.zikra.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.zikra.core.database.content.ContentDatabase
import com.zikra.core.database.secure.PrivateDatabase
import com.zikra.core.database.user.UserDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The committed schema JSON (schemas/) is the migration contract. Creating each database from its
 * exported version-1 schema and validating it fails if entities change without a version bump.
 * Every future version adds a test here that runs the real Migration and validates the result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaContractTest {

    @get:Rule
    val user = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UserDatabase::class.java)

    @get:Rule
    val content = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ContentDatabase::class.java)

    @get:Rule
    val secure = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PrivateDatabase::class.java)

    @Test
    fun userSchemaV1MatchesEntities() {
        user.createDatabase("user-test.db", 1).close()
        user.runMigrationsAndValidate("user-test.db", 1, true).close()
    }

    @Test
    fun contentSchemaV1MatchesEntities() {
        content.createDatabase("content-test.db", 1).close()
        content.runMigrationsAndValidate("content-test.db", 1, true).close()
    }

    @Test
    fun privateSchemaV1MatchesEntities() {
        secure.createDatabase("private-test.db", 1).close()
        secure.runMigrationsAndValidate("private-test.db", 1, true).close()
    }
}
