package com.khabir.app.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import com.khabir.app.data.ai.AiProvider
import com.khabir.app.data.ai.GeminiDocumentVisionService
import com.khabir.app.data.ai.PersonalAiKeyStore
import com.khabir.app.domain.model.ExpertProfile
import com.khabir.app.domain.repository.ExpertProfileRepository
import com.khabir.app.domain.usecase.profile.GetExpertProfileUseCase
import com.khabir.app.domain.usecase.profile.SaveExpertProfileUseCase
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExpertProfileKeyDraftTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var vm: ExpertProfileViewModel

    @Before fun prepare() {
        val context = RuntimeEnvironment.getApplication() as Context
        preferences = context.getSharedPreferences("secure_ai_preferences", Context.MODE_PRIVATE)
        // Opaque storage fixture: this test checks deletion/replacement boundaries,
        // not Android Keystore cryptography or a live provider credential.
        preferences.edit().clear().putString("personal_gemini_key", "encrypted-fixture")
            .putString("personal_ai_provider", AiProvider.GEMINI.name).commit()
        val repository = object : ExpertProfileRepository {
            override fun observe() = flowOf<ExpertProfile?>(null)
            override suspend fun get(): ExpertProfile? = null
            override suspend fun save(profile: ExpertProfile) = Unit
        }
        val store = PersonalAiKeyStore(context)
        vm = ExpertProfileViewModel(GetExpertProfileUseCase(repository), SaveExpertProfileUseCase(repository), store, GeminiDocumentVisionService(store))
    }

    private fun assertSavedPairUnchanged() {
        assertEquals("encrypted-fixture", preferences.getString("personal_gemini_key", null))
        assertEquals(AiProvider.GEMINI.name, preferences.getString("personal_ai_provider", null))
    }

    @Test fun selectingAlreadySelectedProviderDoesNotDeleteSavedKey() {
        vm.onAiProviderChanged(AiProvider.GEMINI)
        assertSavedPairUnchanged()
    }

    @Test fun editingThenClearingReplacementDraftPreservesSavedKey() {
        vm.onPersonalAiKeyChanged("unfinished-replacement")
        vm.onPersonalAiKeyChanged("")
        assertSavedPairUnchanged()
    }

    @Test fun switchingProviderPreservesSavedPairAndClearsUnvalidatedDraft() {
        vm.onPersonalAiKeyChanged("gemini-draft")
        vm.onAiProviderChanged(AiProvider.OPENAI)
        assertSavedPairUnchanged()
        assertEquals("", vm.uiState.value.personalAiKey)
        assertEquals(AiProvider.OPENAI, vm.uiState.value.aiProvider)
        assertFalse(vm.uiState.value.hasPersonalAiKey)
    }

    @Test fun explicitDeleteStillRemovesSavedPair() {
        vm.clearPersonalAiKey()
        assertFalse(preferences.contains("personal_gemini_key"))
        assertFalse(preferences.contains("personal_ai_provider"))
    }
}
