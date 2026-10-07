package com.khabir.app.domain.usecase.workminutes

import com.khabir.app.domain.model.*
import com.khabir.app.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AutomaticReceiptLoadTest {
    private val date=LocalDate.of(2026,10,5)
    private val case=Case(7,"1",date,"123","2026","أسوان","مدني",receiptDate=date)
    private class Minutes(var stored:WorkMinutesRecord?=null):WorkMinutesRepository {
        var writes=0
        override fun observeForCase(caseId:Long)=flowOf(stored)
        override fun observeAll()=flowOf(listOfNotNull(stored))
        override suspend fun getById(id:Long)=stored?.takeIf {it.id==id}
        override suspend fun getForCase(caseId:Long)=stored?.takeIf {it.caseId==caseId}
        override suspend fun save(record:WorkMinutesRecord):Long {writes++;stored=record.copy(id=record.id.takeIf {it>0} ?: 42);return stored!!.id}
    }
    private class Cases(val case:Case):CaseRepository {
        override fun search(query:String)=flowOf(listOf(case))
        override fun filterForRegister(caseType:String?,court:String?,fromEpochDay:Long?,toEpochDay:Long?)=flowOf(listOf(case))
        override fun observeById(caseId:Long)=flowOf(case.takeIf {it.id==caseId})
        override suspend fun getById(caseId:Long)=case.takeIf {it.id==caseId}
        override suspend fun save(case:Case)=case.id
        override suspend fun moveToTrash(caseId:Long){}
        override suspend fun restoreFromTrash(caseId:Long){}
        override suspend fun archive(caseId:Long){}
    }
    @Test fun firstLoadPersistsReceiptAndSecondLoadReusesSameId()=runBlocking {
        val repo=Minutes();val load=GetOrCreateWorkMinutesUseCase(repo,Cases(case))
        val first=load(0,7);val second=load(first.id,7)
        assertEquals(42L,first.id);assertEquals(first,second);assertEquals(1,repo.writes)
    }
    @Test fun existingManualEntriesArePreservedWhenAddingReceipt()=runBlocking {
        val original=WorkMinutesRecord(id=9,caseId=7,entries=listOf(WorkMinutesEntry(1,bodyText="تدوين الخبير")))
        val repo=Minutes(original);val loaded=GetOrCreateWorkMinutesUseCase(repo,Cases(case))(9,7)
        assertEquals(9L,loaded.id);assertEquals(original.entries.first(),loaded.entries.first());assertEquals(2,loaded.entries.size)
    }
    @Test fun independentRecordDoesNotAcquireLinkedCaseReceipt()=runBlocking {
        val saved=WorkMinutesRecord(id=9,caseNo="مستقل",entries=listOf(WorkMinutesEntry(1,bodyText="تدوين مستقل")))
        val repo=Minutes(saved);assertEquals(saved,GetOrCreateWorkMinutesUseCase(repo,Cases(case))(9,7));assertEquals(0,repo.writes)
    }
    @Test fun missingReceiptDateDoesNotPersistInventedMinute()=runBlocking {
        val repo=Minutes();val loaded=GetOrCreateWorkMinutesUseCase(repo,Cases(case.copy(receiptDate=null)))(0,7)
        assertTrue(loaded.entries.isEmpty());assertEquals(0,repo.writes)
    }
}
