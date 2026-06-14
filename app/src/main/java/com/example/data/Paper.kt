package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "papers")
data class Paper(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String, // e.g., "IEEE Conference", "IEEE Journal (Transactions)", "Elsevier Journal", "Springer Lecture Notes", "Generic Essay"
    val fieldOfStudy: String = "",
    val keywords: String = "",
    val abstractText: String = "",
    val introduction: String = "",
    val methodology: String = "",
    val resultsAndEvaluation: String = "",
    val conclusion: String = "",
    val referencesText: String = "",
    val titleIdeasJson: String = "",
    val surveyQuestionsJson: String = "",
    val surveyAnswersJson: String = "",
    val lastModified: Long = System.currentTimeMillis()
)

@Dao
interface PaperDao {
    @Query("SELECT * FROM papers ORDER BY lastModified DESC")
    fun getAllPapers(): Flow<List<Paper>>

    @Query("SELECT * FROM papers WHERE id = :id")
    suspend fun getPaperById(id: Int): Paper?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaper(paper: Paper): Long

    @Update
    suspend fun updatePaper(paper: Paper)

    @Delete
    suspend fun deletePaper(paper: Paper)

    @Query("DELETE FROM papers WHERE id = :id")
    suspend fun deletePaperById(id: Int)
}

@Database(entities = [Paper::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paper_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
