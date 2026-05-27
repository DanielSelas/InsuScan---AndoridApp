package com.example.insuscan.appdata

import android.content.Context
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.network.repository.MealRepository
import com.example.insuscan.network.repository.MealRepositoryImpl
import com.example.insuscan.network.repository.UserRepository
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

object AppDataStore {

    private const val RECENT_MEALS_COUNT = 20

    private lateinit var appContext: Context
    private val userRepository: UserRepository = UserRepositoryImpl()
    private val mealRepository: MealRepository = MealRepositoryImpl()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var profileRevision = 0L
    private var profileJob: Job? = null
    private var mealsJob: Job? = null

    private val _profileState = MutableStateFlow<DataState<Long>>(DataState.Loading)
    val profileState: StateFlow<DataState<Long>> = _profileState.asStateFlow()

    private val _mealsState = MutableStateFlow<DataState<List<Meal>>>(DataState.Loading)
    val mealsState: StateFlow<DataState<List<Meal>>> = _mealsState.asStateFlow()

    private val _saveErrors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val saveErrors: SharedFlow<String> = _saveErrors.asSharedFlow()

    private val _mealAddedSignal = MutableSharedFlow<Unit>()
    val mealAddedSignal: SharedFlow<Unit> = _mealAddedSignal.asSharedFlow()
    private val saveMutex = Mutex()
    private val pendingSaves = AtomicInteger(0)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun refreshAll() {
        refreshProfile()
        refreshMeals()
    }

    fun refreshProfile() {
        if (pendingSaves.get() > 0) return
        val email = UserProfileManager.getUserEmail(appContext) ?: run {
            _profileState.value = DataState.Error(IllegalStateException("No user email"))
            return
        }
        profileJob?.cancel()
        profileJob = scope.launch {
            _profileState.value = DataState.Loading
            userRepository.getUser(email)
                .onSuccess { userDto ->
                    UserProfileManager.syncFromServer(appContext, userDto)
                    profileRevision += 1
                    _profileState.value = DataState.Ready(profileRevision)
                }
                .onFailure { error ->
                    _profileState.value = DataState.Error(error)
                }
        }
    }

    fun refreshMeals() {
        val email = UserProfileManager.getUserEmail(appContext) ?: run {
            _mealsState.value = DataState.Error(IllegalStateException("No user email"))
            return
        }
        mealsJob?.cancel()
        mealsJob = scope.launch {
            _mealsState.value = DataState.Loading
            mealRepository.getRecentMeals(email, RECENT_MEALS_COUNT)
                .onSuccess { dtoList ->
                    _mealsState.value = DataState.Ready(dtoList.map { MealDtoMapper.map(it) })
                }
                .onFailure { error ->
                    _mealsState.value = DataState.Error(error)
                }
        }
    }

    fun saveProfile(userDto: UserDto) {
        notifyLocalProfileChange()
        pendingSaves.incrementAndGet()
        scope.launch {
            saveMutex.withLock {
                try {
                    val email = UserProfileManager.getUserEmail(appContext) ?: run {
                        _saveErrors.emit("No user email")
                        return@withLock
                    }
                    UserRepositoryImpl().updateUser(email, userDto)
                        .onFailure { _saveErrors.emit("Failed to save: ${it.message ?: "Network error"}") }
                } finally {
                    pendingSaves.decrementAndGet()
                }
            }
        }
    }

    fun notifyLocalProfileChange() {
        profileRevision += 1
        _profileState.value = DataState.Ready(profileRevision)
    }

    fun onMealsChanged() {
        refreshMeals()
        scope.launch { _mealAddedSignal.emit(Unit) }
    }

    fun clear() {
        profileJob?.cancel()
        mealsJob?.cancel()
        _profileState.value = DataState.Loading
        _mealsState.value = DataState.Loading
    }
}