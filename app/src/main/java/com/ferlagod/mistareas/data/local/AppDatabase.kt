// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Tareas — https://github.com/ferlagod/mis_tareas

package com.ferlagod.mistareas.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskListEntity::class, TaskEntity::class, CategoryOverride::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2: recordatorios y secciones del súper aprendidas.
        AutoMigration(from = 1, to = 2),
        // v3: listas compartidas (propietario, personas con acceso, solo lectura).
        AutoMigration(from = 2, to = 3),
        // v4: tareas que se repiten (RRULE).
        AutoMigration(from = 3, to = 4),
        // v5: subtareas, etiquetas y token de sync-collection.
        AutoMigration(from = 4, to = 5)
    ]
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun listDao(): ListDao
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mistareas.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
