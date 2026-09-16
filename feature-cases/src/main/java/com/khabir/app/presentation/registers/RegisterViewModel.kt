package com.khabir.app.presentation.registers

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.RegisterFilter
import com.khabir.app.domain.usecase.register.ExportRegisterToWordUseCase
import com.khabir.app.domain.usecase.register.GetCasesForRegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RegisterUiState(val filter: RegisterFilter = RegisterFilter(),val cases: List<Case> = emptyList(),val isExporting: Boolean = false,val exportedFileUri: Uri? = null)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModel @Inject constructor(private val getCasesForRegister: GetCasesForRegisterUseCase,private val exportRegisterToWord: ExportRegisterToWordUseCase): ViewModel() {
    private val filterFlow = MutableStateFlow(RegisterFilter()); private val _uiState = MutableStateFlow(RegisterUiState()); val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    init { viewModelScope.launch { filterFlow.flatMapLatest { getCasesForRegister(it) }.collect { cases -> _uiState.update { it.copy(cases=cases) } } } }
    fun onCaseTypeChanged(v:String)=updateFilter{it.copy(caseType=v.ifBlank{null})}; fun onCourtChanged(v:String)=updateFilter{it.copy(court=v.ifBlank{null})}; fun onFromDateChanged(v:LocalDate?)=updateFilter{it.copy(fromDate=v)}; fun onToDateChanged(v:LocalDate?)=updateFilter{it.copy(toDate=v)}
    private fun updateFilter(update:(RegisterFilter)->RegisterFilter){ val f=update(_uiState.value.filter); _uiState.update{it.copy(filter=f)}; filterFlow.value=f }
    fun onExport(){ val s=_uiState.value; if(s.cases.isEmpty()) return; viewModelScope.launch{_uiState.update{it.copy(isExporting=true)}; val uri=exportRegisterToWord(s.filter,s.cases); _uiState.update{it.copy(isExporting=false,exportedFileUri=uri)}} }
    fun onExportEventConsumed()=_uiState.update{it.copy(exportedFileUri=null)}
}

