package com.murmur.app

sealed class ScreenRoutes(val route: String) {
    object Start : ScreenRoutes("start")
    data class Stream(val streamId: String) : ScreenRoutes("stream/{streamId}") {
        fun createRoute(id: String) = "stream/$id"
    }
}