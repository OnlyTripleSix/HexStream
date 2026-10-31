package com.github.andreyasadchy.xtra.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.andreyasadchy.xtra.model.NotificationUser

@Dao
interface NotificationUsersDao {

    @Query("SELECT * FROM notifications")
    fun getAll(): List<NotificationUser>

    @Query("SELECT * FROM notifications WHERE channelId = :id")
    fun getById(id: String): NotificationUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: NotificationUser)

    @Delete
    fun delete(item: NotificationUser)
}
