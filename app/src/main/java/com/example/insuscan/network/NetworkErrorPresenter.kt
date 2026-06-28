package com.example.insuscan.network

import com.example.insuscan.network.exception.ApiException

object NetworkErrorPresenter {
    fun isServerUnreachable(error: Throwable?): Boolean =
        error is ApiException.NoConnection ||
                error is ApiException.Timeout ||
                error is ApiException.ServerError

    fun message(error: Throwable?): String =
        if (isServerUnreachable(error)) "Can't reach the server."
        else "Couldn't load data."
}