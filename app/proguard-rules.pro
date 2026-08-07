-keep class net.sqlcipher.** { *; }
-keepattributes Signature, *Annotation*

# Gson (de)serializes model classes via reflection using their Kotlin property names/generic
# signatures. Without these — Gson's own recommended R8 rules — minification renames/strips
# fields nothing calls directly, silently turning network responses into empty objects and
# crashing ("Abstract classes can't be instantiated") on TypeToken-based (de)serialization, e.g.
# every field in data/auth's DTOs and persisted-session models (AuthApi.kt, SessionStore.kt,
# OfflineCredentialCache.kt).
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Wire/storage-contract model classes Gson constructs purely via reflection (never `new`d
# directly in app code, only referenced through Retrofit's erased Response<T> generic or
# gson.fromJson(json, X::class.java)) are invisible to R8's reachability analysis. Without a
# whole-class -keep, R8 drops the class from the dex entirely — GsonConverterFactory then hands
# Response.body() a type that isn't there, and reading it throws ClassCastException
# (RemoteAuthRepository.loginOnline / SessionStore / OfflineCredentialCache).
-keep class org.armman.supervisor.data.auth.** { *; }

# Same reflection-based Gson risk as data.auth above, for the projects/Sakhi roster DTOs
# (ProjectDto, SakhiDto, ProjectsEnvelopeDto, SakhisEnvelopeDto) — without this, R8 strips/renames
# their fields in release builds and Gson silently mis-populates them, surfacing as the Dashboard's
# generic error state right after login (DashboardRepositoryImpl -> ProjectsRepository.getProjects).
-keep class org.armman.supervisor.data.projects.** { *; }

# Same reflection-based Gson risk as data.projects above, for inventory-items/transactions DTOs
# and request bodies (InventoryItemDto, InventoryTransactionDto, CreateInventoryTransactionRequest,
# UpdateInventoryTransactionRequest, envelope types) and supervisor-events DTOs/requests
# (SupervisorEventDto, CreateSupervisorEventRequest, envelope types) — without this, R8
# strips/renames their fields in release builds, silently breaking Assign Item and
# Meetings/Training (e.g. "Failed to load sakhi detail", HTTP 403-looking failures that are
# actually malformed request bodies / mis-populated response fields, not real server rejections).
-keep class org.armman.supervisor.data.inventory.** { *; }
-keep class org.armman.supervisor.data.events.** { *; }

# Same reflection-based Gson risk as data.inventory/data.events above, for the call-logs DTOs
# (CallLogDto, CreateCallLogRequestDto, envelope types) — without this, R8 strips/renames their
# fields in release builds, silently breaking Call Sheet ("Failed to load call sheet").
-keep class org.armman.supervisor.data.calllog.** { *; }

# Room entities are constructed via reflection by Room's generated *_Impl DAOs, invisible to R8's
# reachability analysis the same way Gson-constructed DTOs are — without this, field
# renaming/stripping in release corrupts the local Room read-cache/offline-write-queue tables
# added for Assign Item and Meetings/Training (TransactionEntity, PendingInventoryTransactionEntity,
# InventoryItemCacheEntity, SupervisorEventCacheEntity, PendingSupervisorEventEntity, etc.).
-keep class org.armman.supervisor.data.local.** { *; }

# Retrofit's own recommended R8 rules. Retrofit builds each call's generic return type
# (Response<LoginResponseDto>) from the service interface method's signature/annotations at
# runtime; stripping those causes GsonConverterFactory to hand back the wrong type and
# Response.body() throws ClassCastException the moment it's read (RemoteAuthRepository.loginOnline).
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
  @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Google Tink (used transitively) references errorprone annotations that are
# compile-time only and absent at runtime; safe to suppress per R8's own guidance.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
