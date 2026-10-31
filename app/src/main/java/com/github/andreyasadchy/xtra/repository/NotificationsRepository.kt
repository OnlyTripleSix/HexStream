package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.db.NotificationUsersDao
import com.github.andreyasadchy.xtra.db.ShownNotificationsDao
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class NotificationsRepository(
    private val shownNotificationsDao: ShownNotificationsDao,
    private val notificationUsersDao: NotificationUsersDao,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) {

    suspend fun getNewStreams(networkLibrary: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>): List<Stream> = withContext(Dispatchers.IO) {
        val ids = notificationUsersDao.getAll().map { it.channelId }
        val sources = mutableListOf<suspend () -> List<Stream>>()
        if (ids.isNotEmpty()) sources += suspend {
            try {
                gqlQueryLocal(networkLibrary, gqlHeaders, ids)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    helixLocal(networkLibrary, helixHeaders, ids)
                } else throw e
            }
        }
        if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            sources += suspend {
                gqlQueryLoad(networkLibrary, gqlHeaders)
            }
        }
        val fetched = NotificationSources.collect(sources)
        val list = fetched.streams
        val liveList = list.mapNotNull { stream ->
            stream.channelId.takeUnless { it.isNullOrBlank() }?.let { channelId ->
                stream.createdAt.takeUnless { it.isNullOrBlank() }?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }?.let { createdAt ->
                    ShownNotification(channelId, createdAt)
                }
            }
        }
        val oldList = shownNotificationsDao.getAll()
        // A failed source cannot establish that its channels went offline.
        if (fetched.complete) oldList.filter { item -> liveList.find { it.channelId == item.channelId } == null }.let {
            shownNotificationsDao.deleteList(it)
        }
        shownNotificationsDao.insertList(liveList)
        val newStreams = liveList.mapNotNull { item ->
            item.takeIf { oldList.find { it.channelId == item.channelId }.let { it == null || it.startedAt < item.startedAt } }?.channelId
        }
        list.filter { it.channelId in newStreams }
    }

    private suspend fun gqlQueryLoad(networkLibrary: String?, gqlHeaders: Map<String, String>): List<Stream> {
        val list = mutableListOf<Stream>()
        var offset: String? = null
        do {
            val response = graphQLRepository.loadQueryUserFollowedStreams(networkLibrary, gqlHeaders, 100, offset)
            val data = response.data!!.user!!.followedLiveUsers!!
            val items = data.edges!!
            items.mapNotNull { item ->
                item?.node?.let {
                    if (it.self?.follower?.notificationSettings?.isEnabled == true) {
                        Stream(
                            id = it.stream?.id,
                            channelId = it.id,
                            channelLogin = it.login,
                            channelName = it.displayName,
                            channelImageURL = it.profileImageURL,
                            gameId = it.stream?.game?.id,
                            gameSlug = it.stream?.game?.slug,
                            gameName = it.stream?.game?.displayName,
                            title = it.stream?.broadcaster?.broadcastSettings?.title,
                            language = it.stream?.broadcaster?.broadcastSettings?.language,
                            thumbnailURL = it.stream?.previewImageURL,
                            createdAt = it.stream?.createdAt?.toString(),
                            viewerCount = it.stream?.viewersCount,
                            tags = it.stream?.freeformTags?.mapNotNull { tag -> tag.name },
                        )
                    } else null
                }
            }.let { list.addAll(it) }
            offset = items.lastOrNull()?.cursor?.toString()
        } while (!items.lastOrNull()?.cursor?.toString().isNullOrBlank() && data.pageInfo?.hasNextPage == true)
        return list
    }

    private suspend fun gqlQueryLocal(networkLibrary: String?, gqlHeaders: Map<String, String>, ids: List<String>): List<Stream> {
        val items = ids.chunked(100).map { list ->
            graphQLRepository.loadQueryUsersStream(networkLibrary, gqlHeaders, list)
        }.flatMap { it.data!!.users!! }
        val list = items.mapNotNull { item ->
            item?.let {
                if (it.stream?.viewersCount != null) {
                    Stream(
                        id = it.stream.id,
                        channelId = it.id,
                        channelLogin = it.login,
                        channelName = it.displayName,
                        channelImageURL = it.profileImageURL,
                        gameId = it.stream.game?.id,
                        gameSlug = it.stream.game?.slug,
                        gameName = it.stream.game?.displayName,
                        title = it.stream.broadcaster?.broadcastSettings?.title,
                        language = it.stream.broadcaster?.broadcastSettings?.language,
                        thumbnailURL = it.stream.previewImageURL,
                        createdAt = it.stream.createdAt?.toString(),
                        viewerCount = it.stream.viewersCount,
                        tags = it.stream.freeformTags?.mapNotNull { tag -> tag.name },
                    )
                } else null
            }
        }
        return list
    }

    private suspend fun helixLocal(networkLibrary: String?, helixHeaders: Map<String, String>, ids: List<String>): List<Stream> {
        val items = ids.chunked(100).map {
            helixRepository.getStreams(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val users = items.mapNotNull { it.channelId }.chunked(100).map {
            helixRepository.getUsers(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val list = items.mapNotNull {
            if (it.viewerCount != null) {
                Stream(
                    id = it.id,
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    channelImageURL = it.channelId?.let { id ->
                        users.find { user -> user.id == id }?.profileImageURL
                    },
                    gameId = it.gameId,
                    gameName = it.gameName,
                    title = it.title,
                    thumbnailURL = it.thumbnailURL,
                    createdAt = it.startedAt,
                    viewerCount = it.viewerCount,
                    tags = it.tags,
                    language = it.language,
                )
            } else null
        }
        return list
    }

    suspend fun saveList(list: List<ShownNotification>) = withContext(Dispatchers.IO) {
        shownNotificationsDao.insertList(list)
    }

    suspend fun getUserById(id: String) = withContext(Dispatchers.IO) {
        notificationUsersDao.getById(id)
    }

    suspend fun saveUser(item: NotificationUser) = withContext(Dispatchers.IO) {
        notificationUsersDao.insert(item)
    }

    suspend fun deleteUser(item: NotificationUser) = withContext(Dispatchers.IO) {
        notificationUsersDao.delete(item)
    }
}
