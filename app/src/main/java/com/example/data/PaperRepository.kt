package com.example.data

import kotlinx.coroutines.flow.Flow

class PaperRepository(private val paperDao: PaperDao) {
    val allPapers: Flow<List<Paper>> = paperDao.getAllPapers()

    suspend fun getPaperById(id: Int): Paper? {
        return paperDao.getPaperById(id)
    }

    suspend fun insertPaper(paper: Paper): Long {
        return paperDao.insertPaper(paper)
    }

    suspend fun updatePaper(paper: Paper) {
        paperDao.updatePaper(paper)
    }

    suspend fun deletePaper(paper: Paper) {
        paperDao.deletePaper(paper)
    }

    suspend fun deletePaperById(id: Int) {
        paperDao.deletePaperById(id)
    }
}
