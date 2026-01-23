package com.example.insuscan.mapping

interface Mapper<From, To> {
    fun map(from: From): To
}