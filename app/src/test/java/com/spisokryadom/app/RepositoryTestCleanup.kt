package com.spisokryadom.app

// Keep Room open until repository observers have removed their invalidation callbacks.
fun com.spisokryadom.app.data.repository.LocationRepository.closeForTest() =
    kotlinx.coroutines.runBlocking { closeAndJoin() }
