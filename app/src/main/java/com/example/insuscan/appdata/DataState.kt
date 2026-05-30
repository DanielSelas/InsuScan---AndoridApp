package com.example.insuscan.appdata

sealed class DataState<out T> {
    object Loading : DataState<Nothing>()
    data class Ready<T>(val data: T) : DataState<T>()
    data class Error(val cause: Throwable) : DataState<Nothing>()
}