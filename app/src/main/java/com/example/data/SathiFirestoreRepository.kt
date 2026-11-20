package com.example.data

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.ActivityLog
import com.example.model.AppConfig
import com.example.model.CodeType
import com.example.model.MemoryCategory
import com.example.model.PlanTier
import com.example.model.PremiumCode
import com.example.model.UserMemory
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SathiFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    suspend fun ensureUserProfile(
        uid: String,
        displayName: String,
        email: String
    ): Result<Unit> {
        val docPath = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Signed-in UID mismatch" }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName.trim().ifBlank { "বন্ধু" }.take(60)
                val payload = mapOf(
                    "userId" to uid,
                    "userName" to safeName,
                    "email" to email.trim().take(120),
                    "plan" to "free",
                    "banned" to false,
                    "preferredLanguage" to "bn",
                    "preferredVoice" to "female",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserPreferences(
        uid: String,
        userName: String,
        languageCode: String,
        voiceCode: String
    ): Result<Unit> {
        val docPath = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            val safeName = userName.trim().ifBlank { "বন্ধু" }.take(60)
            val safeLang = if (languageCode == "en") "en" else "bn"
            val safeVoice = if (voiceCode == "male") "male" else "female"
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "userName" to safeName,
                        "preferredLanguage" to safeLang,
                        "preferredVoice" to safeVoice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeMemories(uid: String): Flow<List<UserMemory>> = flow {
        val activeUid = requireUserId()
        check(activeUid == uid)
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserMemory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMemory(
        uid: String,
        category: MemoryCategory,
        title: String,
        content: String
    ): Result<Unit> {
        val path = "users/$uid/memories"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            val safeTitle = title.trim().take(100)
            val safeContent = content.trim().take(1000)
            require(safeTitle.isNotEmpty() && safeContent.isNotEmpty()) {
                "Memory title and content must not be blank"
            }
            val docRef = db.collection("users").document(uid).collection("memories").document()
            val payload = mapOf(
                "userId" to uid,
                "category" to category.code,
                "title" to safeTitle,
                "content" to safeContent,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemory(
        uid: String,
        memoryId: String,
        category: MemoryCategory,
        title: String,
        content: String
    ): Result<Unit> {
        val path = "users/$uid/memories/$memoryId"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            require(memoryId.isNotBlank())
            val safeTitle = title.trim().take(100)
            val safeContent = content.trim().take(1000)
            require(safeTitle.isNotEmpty() && safeContent.isNotEmpty()) {
                "Memory title and content must not be blank"
            }
            db.collection("users").document(uid).collection("memories").document(memoryId)
                .update(
                    mapOf(
                        "category" to category.code,
                        "title" to safeTitle,
                        "content" to safeContent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(uid: String, memoryId: String): Result<Unit> {
        val path = "users/$uid/memories/$memoryId"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            require(memoryId.isNotBlank())
            db.collection("users").document(uid).collection("memories").document(memoryId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(uid: String): Result<Unit> {
        val path = "users/$uid/memories"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            val snap = db.collection("users").document(uid).collection("memories").get().await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeGlobalConfig(): Flow<AppConfig> = flow {
        requireUserId()
        val path = "app_config/global"
        emitAll(
            db.collection("app_config").document("global")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            AppConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AppConfig()
                    } else {
                        AppConfig()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError != null) {
                        handleFirestoreError(firestoreError, OperationType.GET, path)
                    }
                    emit(AppConfig())
                }
        )
    }

    suspend fun redeemPremiumCode(uid: String, rawCode: String): Result<PlanTier> {
        val sanitizedCode = rawCode.trim().uppercase()
        val codePath = "premium_codes/$sanitizedCode"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid)
            if (sanitizedCode.isBlank() || !Regex("^[A-Za-z0-9_\\-]+$").matches(sanitizedCode)) {
                return Result.failure(IllegalArgumentException("INVALID_FORMAT"))
            }

            val codeRef = db.collection("premium_codes").document(sanitizedCode)
            val codeSnap = codeRef.get().await()
            if (!codeSnap.exists()) {
                return Result.failure(IllegalArgumentException("CODE_NOT_FOUND"))
            }
            val codeDoc = codeSnap.toObject(PremiumCode::class.java)
                ?: return Result.failure(IllegalArgumentException("CODE_NOT_FOUND"))

            if (codeDoc.status != "active") {
                return Result.failure(IllegalStateException("CODE_INACTIVE_${codeDoc.status.uppercase()}"))
            }

            val codeType = CodeType.fromCode(codeDoc.codeType)
            val userRef = db.collection("users").document(uid)
            val batch = db.batch()

            val targetPlan: PlanTier
            val expiresAt: Long

            if (codeType == CodeType.OWNER) {
                targetPlan = PlanTier.OWNER
                expiresAt = 0L
                batch.update(
                    userRef,
                    mapOf(
                        "plan" to targetPlan.code,
                        "redeemedCodeId" to sanitizedCode,
                        "planExpiresAt" to expiresAt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                targetPlan = PlanTier.PREMIUM
                expiresAt = System.currentTimeMillis() + codeType.durationMs
                batch.update(
                    codeRef,
                    mapOf(
                        "status" to "used",
                        "usedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    userRef,
                    mapOf(
                        "plan" to targetPlan.code,
                        "redeemedCodeId" to sanitizedCode,
                        "planExpiresAt" to expiresAt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(targetPlan)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, codePath)
            Result.failure(e)
        }
    }

    // --- Admin Panel Operations (Verified by Firestore rules) ---

    suspend fun isUserAdminDocPresent(uid: String): Boolean {
        return try {
            val email = auth.currentUser?.email
            val verified = auth.currentUser?.isEmailVerified == true
            if (email.equals(OWNER_EMAIL, ignoreCase = true) && verified) {
                return true
            }
            val doc = db.collection("admins").document(uid).get().await()
            doc.exists()
        } catch (_: Exception) {
            false
        }
    }

    fun observeAllCodes(): Flow<List<PremiumCode>> = flow {
        requireUserId()
        val path = "premium_codes"
        emitAll(
            db.collection("premium_codes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        PremiumCode::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError != null) {
                        handleFirestoreError(firestoreError, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError != null) {
                        handleFirestoreError(firestoreError, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeActivityLogs(): Flow<List<ActivityLog>> = flow {
        requireUserId()
        val path = "activity_logs"
        emitAll(
            db.collection("activity_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ActivityLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError != null) {
                        handleFirestoreError(firestoreError, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun generatePremiumCode(
        adminUid: String,
        adminEmail: String,
        codeType: CodeType,
        note: String
    ): Result<String> {
        val codeId = generateRandomCode(codeType)
        val path = "premium_codes/$codeId"
        return try {
            requireUserId()
            val payload = mapOf(
                "codeType" to codeType.code,
                "status" to "active",
                "createdBy" to adminUid,
                "note" to note.trim().take(120),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("premium_codes").document(codeId).set(payload).await()
            writeActivityLog(
                adminUid = adminUid,
                adminEmail = adminEmail,
                action = "GENERATE_CODE",
                target = codeId,
                details = "Generated ${codeType.labelEn} code"
            )
            Result.success(codeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokePremiumCode(
        adminUid: String,
        adminEmail: String,
        codeId: String
    ): Result<Unit> {
        val path = "premium_codes/$codeId"
        return try {
            requireUserId()
            db.collection("premium_codes").document(codeId)
                .update(
                    mapOf(
                        "status" to "revoked",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            writeActivityLog(
                adminUid = adminUid,
                adminEmail = adminEmail,
                action = "REVOKE_CODE",
                target = codeId,
                details = "Revoked activation code"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUser(
        adminUid: String,
        adminEmail: String,
        targetUid: String,
        newPlan: PlanTier,
        banned: Boolean
    ): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            val expiresAt = when (newPlan) {
                PlanTier.FREE -> 0L
                PlanTier.PREMIUM -> System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                PlanTier.OWNER -> 0L
            }
            db.collection("users").document(targetUid)
                .update(
                    mapOf(
                        "plan" to newPlan.code,
                        "planExpiresAt" to expiresAt,
                        "banned" to banned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            writeActivityLog(
                adminUid = adminUid,
                adminEmail = adminEmail,
                action = "UPDATE_USER",
                target = targetUid,
                details = "Plan=${newPlan.code}, banned=$banned"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateGlobalConfig(
        adminUid: String,
        adminEmail: String,
        config: AppConfig
    ): Result<Unit> {
        val path = "app_config/global"
        return try {
            requireUserId()
            val payload = mapOf(
                "cameraEnabledForFree" to config.cameraEnabledForFree,
                "longTermMemoryForFree" to config.longTermMemoryForFree,
                "translationForFree" to config.translationForFree,
                "dayPlanningForFree" to config.dayPlanningForFree,
                "announcementText" to config.announcementText.trim().take(500),
                "announcementActive" to config.announcementActive,
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("app_config").document("global").set(payload).await()
            writeActivityLog(
                adminUid = adminUid,
                adminEmail = adminEmail,
                action = "UPDATE_CONFIG",
                target = "global",
                details = "Updated feature flags & announcement"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun writeActivityLog(
        adminUid: String,
        adminEmail: String,
        action: String,
        target: String,
        details: String
    ) {
        try {
            val docRef = db.collection("activity_logs").document()
            docRef.set(
                mapOf(
                    "actorUid" to adminUid,
                    "actorEmail" to adminEmail.take(120),
                    "action" to action.take(80),
                    "target" to target.take(120),
                    "details" to details.take(400),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "activity_logs")
        }
    }

    private fun generateRandomCode(type: CodeType): String {
        val prefix = when (type) {
            CodeType.ONE_MONTH -> "SATHI-1M"
            CodeType.SIX_MONTHS -> "SATHI-6M"
            CodeType.ONE_YEAR -> "SATHI-1Y"
            CodeType.OWNER -> "SATHI-OWNER"
        }
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        fun chunk(len: Int) = (1..len).map { chars[random.nextInt(chars.length)] }.joinToString("")
        return "$prefix-${chunk(4)}-${chunk(4)}-${chunk(4)}"
    }

    companion object {
        const val OWNER_EMAIL = "mdbayzidh882@gmail.com"
    }
}
