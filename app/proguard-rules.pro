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
#
# Deliberately a single blanket rule over ALL of data.** rather than one -keep per feature
# package: the per-package version of this rule (one line added per new data/<feature> module)
# is exactly what caused the release-only "ClassCastException: null" on Visit Summary, Quick
# Response, and any other screen under data.beneficiaries/data.lookups/data.quickresponse/
# data.visitsummary — those packages' DTOs were never added to the per-package list as the
# features shipped, so R8 stripped them in release with no compile-time signal. A single
# forward-covering rule can't silently miss a future feature package the same way.
-keep class org.armman.supervisor.data.** { *; }

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

# Root cause of the release-only "Something went wrong" on every authenticated screen right after
# login (reproduced on git HEAD with no other changes applied — this predates and is unrelated to
# TokenAuthenticator/refresh work). SessionStore/OfflineCredentialCache read/write through
# EncryptedSharedPreferencesStore (androidx.security.crypto), which resolves its AEAD/key-manager
# implementations via Tink's internal registries (KeyParser/KeySerializer/ParametersParser/
# ParametersSerializer/PrimitiveConstructor, SharedPrefKeysetWriter, AndroidKeystoreKmsClient) at
# runtime rather than through direct code references R8's reachability analysis can see. Without
# a keep rule, R8 shrinking removes these classes entirely (confirmed via mapping.txt:
# R8$$REMOVED$$CLASS$$ entries for com.google.crypto.tink.internal.KeyParser and siblings), so
# EncryptedSharedPreferences.create(...) throws the first time any screen touches SessionStore —
# with no message reaching the UI, since the failure originates deep in Tink, not from this app's
# own descriptive `error(...)` calls. Confirmed via bisection: isMinifyEnabled=false fixes it,
# -dontobfuscate does not, -dontoptimize does not — isolating shrinking specifically.
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
