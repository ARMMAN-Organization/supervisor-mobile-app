package org.armman.supervisor.data.media

interface MediaRepository {
  /** Uploads the file at [filePath] as [assetType] and returns the resulting `mediaId`. Runs the
   * full 3-call chain (presigned URL -> S3 PUT -> finalize) fresh every call — a presigned URL
   * expires in ~900s, so a retried upload must not reuse one from an earlier attempt. */
  suspend fun uploadPhoto(filePath: String, assetType: String = MediaAssetType.TRAINING_PHOTO): String
}
